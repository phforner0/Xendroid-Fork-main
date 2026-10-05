/**
 ******************************************************************************
 * Xenia : Xbox 360 Emulator Research Project                                 *
 ******************************************************************************
 * Copyright 2022 Ben Vanik. All rights reserved.                             *
 * Released under the BSD license - see LICENSE in the root for more details. *
 ******************************************************************************
 */

#ifndef XENIA_PATCHER_H_
#define XENIA_PATCHER_H_

#include <mutex>
#include <utility>
#include <vector>

#include "xenia/memory.h"
#include "xenia/patcher/patch_db.h"

namespace xe {
namespace patcher {

class Patcher {
 public:
  explicit Patcher(std::filesystem::path patches_dir);

  void ApplyPatch(Memory* memory, const PatchInfoEntry* patch);
  void ApplyPatchesForTitle(Memory* memory, const uint32_t title_id,
                            const std::optional<uint64_t> hash);

  bool IsAnyPatchApplied() { return is_any_patch_applied_; }

  // XenDroid (L10): the hashes the patches of [title_id] were matched against,
  // in loading order (the main executable first), so the app can tell a player
  // whether a patch file is for their game's version. At most kMaxModuleHashes.
  std::vector<uint64_t> ModuleHashes(uint32_t title_id) const;

 private:
  static constexpr size_t kMaxModuleHashes = 16;

  PatchDB* patch_db_;
  bool is_any_patch_applied_;
  mutable std::mutex module_hashes_mutex_;
  std::vector<std::pair<uint32_t, uint64_t>> module_hashes_;
};

}  // namespace patcher
}  // namespace xe
#endif
