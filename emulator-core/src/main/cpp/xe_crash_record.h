#ifndef xendroid_XE_CRASH_RECORD_H
#define xendroid_XE_CRASH_RECORD_H

#include <cstddef>
#include <cstdint>

namespace xe {
// C01: one line about a native crash the core did not survive (signal, fault address,
// thread, pc), left in the run's fatal report (xe_fatal_report.h) by the fault handler
// right before the platform's handler ends the process, unless the core already left its
// own words there. The host's run record then names the crash instead of only "native
// crash". Async-signal-safe: no allocation, locks or stdio, and only the first fault of
// the process is recorded (a fault while recording is not recorded again).

// Where the line goes (the run's .fatal file); null or empty turns it off. Not from a
// signal handler: the first call also finds the core's own code range.
void SetCrashRecordPath(const char* path);

// The fault handler's call (ExceptionHandler::SetUnhandledFaultHook).
void RecordNativeCrash(int signal_number, int code, uintptr_t fault_address, uintptr_t pc);

// The line itself, pure (tests): at most [capacity] bytes, no terminator; returns its
// length. [pc] inside [module_start, module_end) is said as [module]+offset from
// [module_bias], the way symbolizers take it.
size_t FormatNativeCrash(char* out, size_t capacity, int signal_number, int code,
                         uintptr_t fault_address, const char* thread, uintptr_t pc,
                         const char* module, uintptr_t module_start, uintptr_t module_end,
                         uintptr_t module_bias);
}  // namespace xe

#endif  // xendroid_XE_CRASH_RECORD_H
