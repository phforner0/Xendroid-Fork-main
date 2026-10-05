#ifndef xendroid_XE_FATAL_REPORT_H
#define xendroid_XE_FATAL_REPORT_H

#include <string>

namespace xe {
// Where a fatal error's message is left before the process aborts (FatalError
// shows no UI on Android), so the host's run record can say why the game closed.
// Set by the host before boot; empty turns it off.
void SetFatalReportPath(std::string path);
}  // namespace xe

#endif  // xendroid_XE_FATAL_REPORT_H
