#include <cassert>
#include <cstdio>
#include <fstream>
#include <unistd.h>
#include "third_party/lsfg/lsfg_dll.h"

int main(int argc, char** argv) {
  assert(lsfg::validateDll("/does-not-exist/Lossless.dll") == lsfg::DllStatus::NotInstalled);
  std::vector<uint32_t> malformed{0, 1, 2};
  assert(!lsfg::downgradeSpirv(malformed, lsfg::kSpirv15));
  assert(lsfg::shaderIds().size() == 25);
  // A crafted cache must fail before allocating its declared oversized module.
  char name[] = "/tmp/xendroid-lsfg-cache-test-XXXXXX";
  int fd = mkstemp(name); assert(fd >= 0); close(fd);
  struct Header { uint32_t magic, version; uint64_t size, hash; uint32_t count, variant; };
  {
    std::ofstream bad(name, std::ios::binary);
    Header header{0x4C534642u, 1, 1, 0, 25, 3};
    uint32_t id = 255, oversized = 0xffffffffu;
    bad.write(reinterpret_cast<const char*>(&header), sizeof(header));
    bad.write(reinterpret_cast<const char*>(&id), sizeof(id));
    bad.write(reinterpret_cast<const char*>(&oversized), sizeof(oversized));
  }
  lsfg::ModuleSet rejected;
  assert(lsfg::loadModules(name, rejected) == lsfg::DllStatus::CacheUnusable);
  assert(rejected.modules.empty()); std::remove(name);
  if (argc == 3) {
    auto status = lsfg::validateDll(argv[1]);
    std::printf("User DLL validation: %s\n", lsfg::statusName(status));
    if (status != lsfg::DllStatus::Ok) return 2;
    status = lsfg::buildCache(argv[1], argv[2], true);
    std::printf("User DLL translation: %s\n", lsfg::statusName(status));
    if (status != lsfg::DllStatus::Ok) return 3;
    lsfg::ModuleSet modules;
    assert(lsfg::loadModules(argv[2], modules) == lsfg::DllStatus::Ok && modules.complete());
    bool matches = false;
    assert(lsfg::cacheMatchesSource(argv[2], argv[1], matches) == lsfg::DllStatus::Ok && matches);
    std::printf("Translated shader modules: %zu\n", modules.modules.size());
  }
  std::puts("LSFG DLL parsing/cache: passed");
}
