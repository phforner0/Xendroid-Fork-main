#include <cassert>
#include <csignal>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>

#include <sys/prctl.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <ucontext.h>
#include <unistd.h>

#include "xe_crash_record.h"

// C01: the line a native crash leaves in the run's fatal report, and that it is written
// from a real fault handler, once, without replacing the core's own words.

static std::string Format(int signal_number, int code, uintptr_t address, const char* thread, uintptr_t pc,
                          uintptr_t start = 0x7000000000, uintptr_t end = 0x7100000000, uintptr_t bias = 0x7000000000,
                          size_t capacity = 320) {
  char out[400];
  std::memset(out, '#', sizeof(out));
  const size_t length = xe::FormatNativeCrash(out, capacity, signal_number, code, address, thread, pc, "libe.so",
                                              start, end, bias);
  assert(length <= capacity);
  assert(out[length] == '#');  // nothing written past what it returns
  return std::string(out, length);
}

static void SaysTheSignalTheAddressTheThreadAndWhereInTheCore() {
  assert(Format(SIGSEGV, SEGV_MAPERR, 0x10, "GPU Commands", 0x70001a2b3c) ==
         "native crash: SIGSEGV (SEGV_MAPERR) at 0x0000000000000010, thread 'GPU Commands', pc libe.so+0x1a2b3c");
  // A pc in another library (a driver) is said as it is.
  assert(Format(SIGBUS, BUS_ADRALN, 0x7f00000003, "XThread 0042", 0x12345) ==
         "native crash: SIGBUS (BUS_ADRALN) at 0x0000007f00000003, thread 'XThread 0042', pc 0x12345");
  assert(Format(SIGILL, ILL_ILLOPC, 0x7000000400, "main", 0x7000000400) ==
         "native crash: SIGILL (ILL_ILLOPC) at 0x0000007000000400, thread 'main', pc libe.so+0x400");
}

static void UnknownSignalsAndCodesAreNumbersAndNamesArePrintable() {
  assert(Format(99, 7, 0, "a'b\x01" "c", 0x1) == "native crash: signal 99 (code 7) at 0x0000000000000000, thread 'a?b?c', pc 0x1");
  assert(Format(SIGSEGV, -6, 0, "", 0x1) == "native crash: SIGSEGV (code -6) at 0x0000000000000000, thread '?', pc 0x1");
  assert(Format(SIGSEGV, SEGV_ACCERR, 0, nullptr, 0x1).find("thread '?'") != std::string::npos);
  // At most 16 characters of a name (the kernel keeps 15).
  assert(Format(SIGSEGV, SEGV_ACCERR, 0, "0123456789abcdefXYZ", 0x1).find("thread '0123456789abcdef'") != std::string::npos);
}

static void ALineNeverOverrunsItsBuffer() {
  const std::string cut = Format(SIGSEGV, SEGV_MAPERR, 0x10, "GPU Commands", 0x70001a2b3c, 0x7000000000, 0x7100000000,
                                 0x7000000000, 20);
  assert(cut == "native crash: SIGSEG");
  assert(Format(SIGSEGV, SEGV_MAPERR, 0x10, "x", 0x1, 0, 0, 0, 0).empty());
}

static std::string Read(const std::string& path) {
  std::string text;
  if (FILE* file = std::fopen(path.c_str(), "rb")) {
    char buffer[512];
    size_t n;
    while ((n = std::fread(buffer, 1, sizeof(buffer), file)) > 0) text.append(buffer, n);
    std::fclose(file);
  }
  return text;
}

static void OnFault(int signal_number, siginfo_t* info, void* context) {
  uintptr_t pc = 0;
#if defined(__x86_64__)
  pc = static_cast<uintptr_t>(static_cast<ucontext_t*>(context)->uc_mcontext.gregs[REG_RIP]);
#else
  (void)context;
#endif
  xe::RecordNativeCrash(signal_number, info->si_code, reinterpret_cast<uintptr_t>(info->si_addr), pc);
  _exit(42);
}

// Runs [crash] in a child that records its fault in [path]; returns the child's exit code.
template <typename Crash>
static int InChild(const std::string& path, Crash crash) {
  const pid_t child = fork();
  assert(child >= 0);
  if (child == 0) {
    prctl(PR_SET_NAME, "crash-test", 0, 0, 0);
    xe::SetCrashRecordPath(path.c_str());
    struct sigaction action {};
    action.sa_sigaction = OnFault;
    action.sa_flags = SA_SIGINFO;
    sigaction(SIGSEGV, &action, nullptr);
    crash();
    _exit(0);
  }
  int status = 0;
  waitpid(child, &status, 0);
  return WIFEXITED(status) ? WEXITSTATUS(status) : -1;
}

static void ARealFaultIsRecordedFromItsHandler(const std::string& dir) {
  const std::string path = dir + "/run-1.fatal";
  std::remove(path.c_str());
  assert(InChild(path, [] {
    volatile uintptr_t address = 8;  // not a constant: the compiler cannot turn the store into a trap
    *reinterpret_cast<volatile int*>(address) = 1;
  }) == 42);
  const std::string line = Read(path);
  assert(line.rfind("native crash: SIGSEGV (SEGV_MAPERR) at 0x0000000000000008, thread 'crash-test', pc ", 0) == 0);
  assert(line.back() == '\n' && line.find('\n') == line.size() - 1);
#if defined(__x86_64__)
  // The fault is in this program, the code that recorded it: said relative to it.
  assert(line.find(", pc core+0x") != std::string::npos);
#endif
  struct stat info {};
  assert(stat(path.c_str(), &info) == 0 && (info.st_mode & 0777) == 0600);
}

static void TheCoresOwnWordsWin(const std::string& dir) {
  const std::string path = dir + "/run-2.fatal";
  if (FILE* file = std::fopen(path.c_str(), "wb")) {
    std::fputs("Graphics device lost\n", file);
    std::fclose(file);
  }
  assert(InChild(path, [] { raise(SIGSEGV); }) == 42);
  assert(Read(path) == "Graphics device lost\n");
}

static void OnlyTheFirstFaultIsRecorded(const std::string& dir) {
  const std::string first = dir + "/run-3.fatal";
  const std::string second = dir + "/run-4.fatal";
  std::remove(first.c_str());
  std::remove(second.c_str());
  assert(InChild(first, [&] {
    xe::RecordNativeCrash(SIGSEGV, SEGV_MAPERR, 0x10, 0);
    xe::SetCrashRecordPath(second.c_str());
    xe::RecordNativeCrash(SIGBUS, BUS_ADRERR, 0x20, 0);
  }) == 0);
  assert(Read(first).rfind("native crash: SIGSEGV (SEGV_MAPERR) at 0x0000000000000010", 0) == 0);
  assert(Read(second).empty());
}

static void NoPathRecordsNothing(const std::string& dir) {
  const std::string path = dir + "/run-5.fatal";
  std::remove(path.c_str());
  const pid_t child = fork();
  if (child == 0) {
    xe::SetCrashRecordPath(path.c_str());
    xe::SetCrashRecordPath("");
    xe::RecordNativeCrash(SIGSEGV, SEGV_MAPERR, 0x10, 0);
    _exit(0);
  }
  int status = 0;
  waitpid(child, &status, 0);
  assert(Read(path).empty());
}

int main(int argc, char** argv) {
  assert(argc == 2);
  const std::string dir = argv[1];
  mkdir(dir.c_str(), 0700);
  SaysTheSignalTheAddressTheThreadAndWhereInTheCore();
  UnknownSignalsAndCodesAreNumbersAndNamesArePrintable();
  ALineNeverOverrunsItsBuffer();
  ARealFaultIsRecordedFromItsHandler(dir);
  TheCoresOwnWordsWin(dir);
  OnlyTheFirstFaultIsRecorded(dir);
  NoPathRecordsNothing(dir);
  std::puts("crash_record_test: ok");
  return 0;
}
