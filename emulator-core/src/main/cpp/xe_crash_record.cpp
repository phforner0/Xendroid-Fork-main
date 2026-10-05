#include "xe_crash_record.h"

#include <atomic>
#include <cerrno>
#include <csignal>
#include <cstring>

#include <dlfcn.h>
#include <fcntl.h>
#include <link.h>
#include <sys/prctl.h>
#include <unistd.h>

namespace xe {
namespace {

constexpr size_t kPathCapacity = 1024;
// A new path goes to the buffer not published, then is published: the handler only reads.
char paths_[2][kPathCapacity];
std::atomic<int> published_{-1};

// The core's code range, found once outside any signal handler.
char module_name_[64];
std::atomic<uintptr_t> module_start_{0};
std::atomic<uintptr_t> module_end_{0};
std::atomic<uintptr_t> module_bias_{0};

std::atomic_flag recording_ = ATOMIC_FLAG_INIT;

// Appends to a fixed buffer; anything past its end is dropped.
struct Line {
  char* out;
  size_t capacity;
  size_t length = 0;

  void Put(char c) {
    if (length < capacity) out[length++] = c;
  }
  void Put(const char* text) {
    while (*text) Put(*text++);
  }
  void Decimal(long value) {
    unsigned long magnitude = value < 0 ? 0UL - static_cast<unsigned long>(value) : static_cast<unsigned long>(value);
    if (value < 0) Put('-');
    char digits[24];
    int count = 0;
    do {
      digits[count++] = static_cast<char>('0' + magnitude % 10);
      magnitude /= 10;
    } while (magnitude != 0);
    while (count > 0) Put(digits[--count]);
  }
  // At least [width] digits, after "0x".
  void Hex(uintptr_t value, int width) {
    Put("0x");
    char digits[2 * sizeof(uintptr_t)];
    int count = 0;
    do {
      digits[count++] = "0123456789abcdef"[value & 0xF];
      value >>= 4;
    } while (value != 0);
    while (count < width && count < static_cast<int>(sizeof(digits))) digits[count++] = '0';
    while (count > 0) Put(digits[--count]);
  }
};

const char* SignalName(int signal_number) {
  switch (signal_number) {
    case SIGSEGV: return "SIGSEGV";
    case SIGBUS: return "SIGBUS";
    case SIGILL: return "SIGILL";
    case SIGFPE: return "SIGFPE";
    case SIGTRAP: return "SIGTRAP";
    case SIGABRT: return "SIGABRT";
    default: return nullptr;
  }
}

const char* CodeName(int signal_number, int code) {
  switch (signal_number) {
    case SIGSEGV:
      switch (code) {
        case SEGV_MAPERR: return "SEGV_MAPERR";
        case SEGV_ACCERR: return "SEGV_ACCERR";
      }
      break;
    case SIGBUS:
      switch (code) {
        case BUS_ADRALN: return "BUS_ADRALN";
        case BUS_ADRERR: return "BUS_ADRERR";
        case BUS_OBJERR: return "BUS_OBJERR";
      }
      break;
    case SIGILL:
      switch (code) {
        case ILL_ILLOPC: return "ILL_ILLOPC";
        case ILL_ILLOPN: return "ILL_ILLOPN";
        case ILL_ILLADR: return "ILL_ILLADR";
        case ILL_ILLTRP: return "ILL_ILLTRP";
        case ILL_PRVOPC: return "ILL_PRVOPC";
        case ILL_PRVREG: return "ILL_PRVREG";
        case ILL_COPROC: return "ILL_COPROC";
        case ILL_BADSTK: return "ILL_BADSTK";
      }
      break;
  }
  return nullptr;
}

struct ModuleSearch {
  uintptr_t address;
  bool found = false;
  uintptr_t start = 0, end = 0, bias = 0;
  const char* name = nullptr;
};

// The loaded object whose segments hold [address]: its range, load bias and file name.
int FindModule(dl_phdr_info* info, size_t, void* data) {
  auto* search = static_cast<ModuleSearch*>(data);
  uintptr_t start = UINTPTR_MAX, end = 0;
  bool holds = false;
  for (int i = 0; i < info->dlpi_phnum; ++i) {
    const auto& header = info->dlpi_phdr[i];
    if (header.p_type != PT_LOAD) continue;
    const uintptr_t from = info->dlpi_addr + header.p_vaddr;
    const uintptr_t to = from + header.p_memsz;
    if (from < start) start = from;
    if (to > end) end = to;
    if (search->address >= from && search->address < to) holds = true;
  }
  if (!holds) return 0;
  search->found = true;
  search->start = start;
  search->end = end;
  search->bias = info->dlpi_addr;
  search->name = info->dlpi_name;
  return 1;
}

void LocateModule() {
  ModuleSearch search{reinterpret_cast<uintptr_t>(&LocateModule)};
  dl_iterate_phdr(FindModule, &search);
  if (!search.found) return;
  const char* name = search.name && *search.name ? search.name : "core";
  if (const char* slash = std::strrchr(name, '/')) name = slash + 1;
  std::strncpy(module_name_, name, sizeof(module_name_) - 1);
  module_bias_.store(search.bias, std::memory_order_relaxed);
  module_end_.store(search.end, std::memory_order_relaxed);
  module_start_.store(search.start, std::memory_order_release);
}

}  // namespace

size_t FormatNativeCrash(char* out, size_t capacity, int signal_number, int code,
                         uintptr_t fault_address, const char* thread, uintptr_t pc,
                         const char* module, uintptr_t module_start, uintptr_t module_end,
                         uintptr_t module_bias) {
  Line line{out, capacity};
  line.Put("native crash: ");
  if (const char* name = SignalName(signal_number)) {
    line.Put(name);
  } else {
    line.Put("signal ");
    line.Decimal(signal_number);
  }
  line.Put(" (");
  if (const char* name = CodeName(signal_number, code)) {
    line.Put(name);
  } else {
    line.Put("code ");
    line.Decimal(code);
  }
  line.Put(") at ");
  line.Hex(fault_address, 2 * sizeof(uintptr_t));
  line.Put(", thread '");
  if (thread && *thread) {
    // A thread name is the program's own text: printable ASCII only.
    for (int i = 0; i < 16 && thread[i]; ++i) {
      const char c = thread[i];
      line.Put(c >= ' ' && c <= '~' && c != '\'' ? c : '?');
    }
  } else {
    line.Put('?');
  }
  line.Put("', pc ");
  if (module && *module && pc >= module_start && pc < module_end) {
    line.Put(module);
    line.Put('+');
    line.Hex(pc - module_bias, 1);
  } else {
    line.Hex(pc, 1);
  }
  return line.length;
}

void SetCrashRecordPath(const char* path) {
  static std::atomic_flag located = ATOMIC_FLAG_INIT;
  if (!located.test_and_set()) LocateModule();
  const size_t length = path ? std::strlen(path) : 0;
  if (length == 0 || length >= kPathCapacity) {
    published_.store(-1, std::memory_order_release);
    return;
  }
  const int next = published_.load(std::memory_order_relaxed) == 0 ? 1 : 0;
  std::memcpy(paths_[next], path, length + 1);
  published_.store(next, std::memory_order_release);
}

void RecordNativeCrash(int signal_number, int code, uintptr_t fault_address, uintptr_t pc) {
  // The first fault of the process only: another thread's, or one inside this function.
  if (recording_.test_and_set()) return;
  const int index = published_.load(std::memory_order_acquire);
  if (index < 0) return;
  const int saved_errno = errno;
  char thread[17] = {};
  prctl(PR_GET_NAME, thread, 0, 0, 0);
  char line[320];
  size_t length = FormatNativeCrash(line, sizeof(line) - 1, signal_number, code, fault_address, thread, pc,
                                    module_name_, module_start_.load(std::memory_order_acquire),
                                    module_end_.load(std::memory_order_relaxed),
                                    module_bias_.load(std::memory_order_relaxed));
  line[length++] = '\n';
  // O_EXCL: the core's own words (a FatalError just before) win over this line.
  const int file = open(paths_[index], O_WRONLY | O_CREAT | O_EXCL | O_CLOEXEC, 0600);
  if (file >= 0) {
    size_t written = 0;
    while (written < length) {
      const ssize_t n = write(file, line + written, length - written);
      if (n > 0) {
        written += static_cast<size_t>(n);
      } else if (n < 0 && errno == EINTR) {
        continue;
      } else {
        break;
      }
    }
    fsync(file);
    close(file);
  }
  errno = saved_errno;
}

}  // namespace xe
