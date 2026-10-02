/**
 ******************************************************************************
 * Xenia : Xbox 360 Emulator Research Project                                 *
 ******************************************************************************
 * Copyright 2020 Ben Vanik. All rights reserved.                             *
 * Released under the BSD license - see LICENSE in the root for more details. *
 ******************************************************************************
 */

#include <algorithm>
#include <cstdio>
#include <mutex>
#include <string>

#include <unistd.h>

#include "xe_fatal_report.h"
#include "xenia/base/system.h"

namespace xe {
    namespace {
    std::mutex fatal_report_mutex;
    std::string fatal_report_path;
    }  // namespace

    void SetFatalReportPath(std::string path) {
        std::lock_guard<std::mutex> lock(fatal_report_mutex);
        fatal_report_path = std::move(path);
    }

    // There is no message box on this platform. An error box only comes from
    // FatalError (abort follows) or a fatal cvar error: leave its text for the
    // host's run record instead (temp file + rename, bounded).
    void ShowSimpleMessageBox(SimpleMessageBoxType type, std::string_view message) {
        if (type != SimpleMessageBoxType::Error) {
            return;
        }
        std::string path;
        {
            std::lock_guard<std::mutex> lock(fatal_report_mutex);
            path = fatal_report_path;
        }
        if (path.empty()) {
            return;
        }
        const std::string temporary = path + "." + std::to_string(gettid()) + ".tmp";
        FILE* file = std::fopen(temporary.c_str(), "wb");
        if (!file) {
            return;
        }
        const size_t size = std::min<size_t>(message.size(), 4096);
        const bool written = std::fwrite(message.data(), 1, size, file) == size &&
                             std::fflush(file) == 0 && fsync(fileno(file)) == 0;
        std::fclose(file);
        if (!written || std::rename(temporary.c_str(), path.c_str()) != 0) {
            std::remove(temporary.c_str());
        }
    }

    void LaunchFileExplorer(const std::filesystem::path& path) {}

    void LaunchWebBrowser(const std::string_view url) {}

    bool SetProcessPriorityClass(const uint32_t priority_class) { return true; }

    bool IsUseNexusForGameBarEnabled() { return false; }
}
