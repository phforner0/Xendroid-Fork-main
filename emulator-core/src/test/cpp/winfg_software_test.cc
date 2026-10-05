#include <vulkan/vulkan.h>
#include <algorithm>
#include <cassert>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <vector>
#include <chrono>
#include <thread>
#include <fstream>
#include "third_party/winfg/src/framegen.hpp"
#ifdef TEST_LSFG
#include "third_party/lsfg/lsfg_engine.h"
#include "third_party/lsfg/lsfg_vkd.h"
#endif

#define VK_CHECK(expr) do { const auto result = (expr); if (result != VK_SUCCESS) { std::fprintf(stderr, "%s: %d\n", #expr, int(result)); return 1; } } while (0)

struct Image { VkImage image{}; VkImageView view{}; VkDeviceMemory memory{}; };
int main(int argc, char** argv) {
#ifdef TEST_MOTION
  // motion-test [preset 0..2] [shift px] [model 3|4] [pairs] [pattern 0 square|1 noise pan] [size px]
  const uint32_t kSize = argc > 6 ? uint32_t(std::max(32, std::atoi(argv[6]))) : 64;
#else
  constexpr uint32_t kSize = 64;
#endif
  VkApplicationInfo app{VK_STRUCTURE_TYPE_APPLICATION_INFO}; app.apiVersion = VK_API_VERSION_1_3;
  VkInstanceCreateInfo instance_info{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO}; instance_info.pApplicationInfo = &app;
  VkInstance instance; VK_CHECK(vkCreateInstance(&instance_info, nullptr, &instance));
  uint32_t count = 0; VK_CHECK(vkEnumeratePhysicalDevices(instance, &count, nullptr));
  if (!count) { std::fprintf(stderr, "No software Vulkan device\n"); return 2; }
  std::vector<VkPhysicalDevice> devices(count); VK_CHECK(vkEnumeratePhysicalDevices(instance, &count, devices.data()));
  VkPhysicalDevice physical = devices[0];
  VkPhysicalDeviceProperties properties; vkGetPhysicalDeviceProperties(physical, &properties);
  std::printf("Vulkan test device: %s\n", properties.deviceName);
  vkGetPhysicalDeviceQueueFamilyProperties(physical, &count, nullptr);
  std::vector<VkQueueFamilyProperties> families(count); vkGetPhysicalDeviceQueueFamilyProperties(physical, &count, families.data());
  uint32_t family = 0; while (family < count && !(families[family].queueFlags & VK_QUEUE_COMPUTE_BIT)) ++family;
  assert(family < count);
  float priority = 1.0f;
  VkDeviceQueueCreateInfo queue_info{VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO};
  queue_info.queueFamilyIndex = family; queue_info.queueCount = 1; queue_info.pQueuePriorities = &priority;
  VkPhysicalDeviceFeatures features; vkGetPhysicalDeviceFeatures(physical, &features);
#ifdef TEST_LSFG
  assert(argc == 2 || argc == 3);
  const unsigned multiplier = argc == 3 ? unsigned(std::atoi(argv[2])) : 2;
  assert(multiplier >= 2 && multiplier <= 4);
  VkPhysicalDeviceVulkan12Features v12{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES};
  VkPhysicalDeviceRobustness2FeaturesEXT robustness{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_ROBUSTNESS_2_FEATURES_EXT};
  VkPhysicalDeviceFeatures2 supported{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2};
  supported.pNext = &v12; v12.pNext = &robustness;
  vkGetPhysicalDeviceFeatures2(physical, &supported);
  assert(robustness.nullDescriptor && v12.vulkanMemoryModel);
  const char* extension = VK_EXT_ROBUSTNESS_2_EXTENSION_NAME;
#endif
  VkDeviceCreateInfo device_info{VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO}; device_info.queueCreateInfoCount = 1;
  device_info.pQueueCreateInfos = &queue_info; device_info.pEnabledFeatures = &features;
#ifdef TEST_LSFG
  device_info.pNext = &v12; device_info.enabledExtensionCount = 1; device_info.ppEnabledExtensionNames = &extension;
#endif
  VkDevice device; VK_CHECK(vkCreateDevice(physical, &device_info, nullptr, &device));
  VkQueue queue; vkGetDeviceQueue(device, family, 0, &queue);
  winfg::DeviceDispatch dd;
#define LOAD(name) dd.name = reinterpret_cast<PFN_vk##name>(vkGetDeviceProcAddr(device, "vk" #name))
  LOAD(CreateImage); LOAD(DestroyImage); LOAD(CreateImageView); LOAD(DestroyImageView);
  LOAD(AllocateMemory); LOAD(FreeMemory); LOAD(BindImageMemory); LOAD(GetImageMemoryRequirements);
  LOAD(CreateBuffer); LOAD(DestroyBuffer); LOAD(GetBufferMemoryRequirements); LOAD(BindBufferMemory);
  LOAD(MapMemory); LOAD(UnmapMemory); LOAD(CreateSampler); LOAD(DestroySampler);
  LOAD(CreateShaderModule); LOAD(DestroyShaderModule); LOAD(CreateDescriptorSetLayout); LOAD(DestroyDescriptorSetLayout);
  LOAD(CreatePipelineLayout); LOAD(DestroyPipelineLayout); LOAD(CreateComputePipelines); LOAD(DestroyPipeline);
  LOAD(CreateDescriptorPool); LOAD(DestroyDescriptorPool); LOAD(ResetDescriptorPool); LOAD(AllocateDescriptorSets); LOAD(UpdateDescriptorSets);
  LOAD(CmdBindPipeline); LOAD(CmdBindDescriptorSets); LOAD(CmdDispatch); LOAD(CmdPipelineBarrier);
  LOAD(CmdCopyImage); LOAD(CmdBlitImage); LOAD(CmdClearColorImage); LOAD(DeviceWaitIdle);
#undef LOAD
  winfg::InstanceDispatch id; id.GetPhysicalDeviceMemoryProperties = vkGetPhysicalDeviceMemoryProperties;
#ifdef TEST_LSFG
  LsfgVkDispatch table;
#define LSFG_LOAD(name) table.name = reinterpret_cast<PFN_vk##name>(vkGetDeviceProcAddr(device, "vk" #name))
  LSFG_LOAD(AllocateDescriptorSets); LSFG_LOAD(AllocateMemory); LSFG_LOAD(BindBufferMemory); LSFG_LOAD(BindImageMemory);
  LSFG_LOAD(CmdBindDescriptorSets); LSFG_LOAD(CmdBindPipeline); LSFG_LOAD(CmdCopyImage); LSFG_LOAD(CmdDispatch); LSFG_LOAD(CmdPipelineBarrier);
  LSFG_LOAD(CreateBuffer); LSFG_LOAD(CreateComputePipelines); LSFG_LOAD(CreateDescriptorPool); LSFG_LOAD(CreateDescriptorSetLayout);
  LSFG_LOAD(CreateImage); LSFG_LOAD(CreateImageView); LSFG_LOAD(CreatePipelineLayout); LSFG_LOAD(CreateSampler); LSFG_LOAD(CreateShaderModule);
  LSFG_LOAD(DestroyBuffer); LSFG_LOAD(DestroyDescriptorPool); LSFG_LOAD(DestroyDescriptorSetLayout); LSFG_LOAD(DestroyImage); LSFG_LOAD(DestroyImageView);
  LSFG_LOAD(DestroyPipeline); LSFG_LOAD(DestroyPipelineLayout); LSFG_LOAD(DestroySampler); LSFG_LOAD(DestroyShaderModule); LSFG_LOAD(FreeMemory);
  LSFG_LOAD(GetBufferMemoryRequirements); LSFG_LOAD(GetImageMemoryRequirements); LSFG_LOAD(MapMemory); LSFG_LOAD(UnmapMemory); LSFG_LOAD(UpdateDescriptorSets);
#undef LSFG_LOAD
  table.GetPhysicalDeviceMemoryProperties = vkGetPhysicalDeviceMemoryProperties;
  assert(lsfgVkdInit(table));
#endif
  VkPhysicalDeviceMemoryProperties memory; vkGetPhysicalDeviceMemoryProperties(physical, &memory);
  auto memory_type = [&](uint32_t bits, VkMemoryPropertyFlags flags) {
    for (uint32_t i = 0; i < memory.memoryTypeCount; ++i) if ((bits & (1u << i)) && (memory.memoryTypes[i].propertyFlags & flags) == flags) return i;
    return uint32_t(0);
  };
  auto image = [&](Image& out) {
    VkImageCreateInfo info{VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO}; info.imageType = VK_IMAGE_TYPE_2D;
    info.format = VK_FORMAT_R8G8B8A8_UNORM; info.extent = {kSize, kSize, 1}; info.mipLevels = info.arrayLayers = 1;
    info.samples = VK_SAMPLE_COUNT_1_BIT; info.tiling = VK_IMAGE_TILING_OPTIMAL;
    info.usage = VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_STORAGE_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT;
    assert(vkCreateImage(device, &info, nullptr, &out.image) == VK_SUCCESS);
    VkMemoryRequirements requirements; vkGetImageMemoryRequirements(device, out.image, &requirements);
    VkMemoryAllocateInfo allocation{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO}; allocation.allocationSize = requirements.size;
    allocation.memoryTypeIndex = memory_type(requirements.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
    assert(vkAllocateMemory(device, &allocation, nullptr, &out.memory) == VK_SUCCESS);
    assert(vkBindImageMemory(device, out.image, out.memory, 0) == VK_SUCCESS);
    VkImageViewCreateInfo view{VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO}; view.image = out.image; view.viewType = VK_IMAGE_VIEW_TYPE_2D;
    view.format = info.format; view.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
    assert(vkCreateImageView(device, &view, nullptr, &out.view) == VK_SUCCESS);
  };
  Image previous, current, generated; image(previous); image(current); image(generated);
  VkBufferCreateInfo buffer_info{VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO}; buffer_info.size = VkDeviceSize(kSize) * kSize * 4;
  buffer_info.usage = VK_BUFFER_USAGE_TRANSFER_DST_BIT;
  VkBuffer buffer; VK_CHECK(vkCreateBuffer(device, &buffer_info, nullptr, &buffer));
  VkMemoryRequirements requirements; vkGetBufferMemoryRequirements(device, buffer, &requirements);
  VkMemoryAllocateInfo allocation{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO}; allocation.allocationSize = requirements.size;
  allocation.memoryTypeIndex = memory_type(requirements.memoryTypeBits, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
  VkDeviceMemory readback; VK_CHECK(vkAllocateMemory(device, &allocation, nullptr, &readback));
  VK_CHECK(vkBindBufferMemory(device, buffer, readback, 0));
  VkCommandPoolCreateInfo pool_info{VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO}; pool_info.queueFamilyIndex = family;
  pool_info.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
  VkCommandPool pool; VK_CHECK(vkCreateCommandPool(device, &pool_info, nullptr, &pool));
  VkCommandBufferAllocateInfo commands{VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO}; commands.commandPool = pool;
  commands.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY; commands.commandBufferCount = 1;
  VkCommandBuffer command; VK_CHECK(vkAllocateCommandBuffers(device, &commands, &command));
#ifdef TEST_LSFG
  auto lsfg_engine = std::make_unique<lsfg::Engine>();
  assert(lsfg_engine->init(device, physical, argv[1]));
  lsfg_engine->configure(multiplier, 0, 1.0f, 120.0f);
  assert(lsfg_engine->prepare(kSize, kSize, VK_FORMAT_R8G8B8A8_UNORM));
#elif !defined(TEST_COLOR)
  winfg::FrameGen engine; assert(engine.init(&dd, &id, physical, device, family, queue));
  winfg::Config cfg; cfg.enabled = true; cfg.model = 3; cfg.gmMode = 2; cfg.frMode = 2; cfg.perfPreset = 2;
#ifdef TEST_MOTION
  // motion-test [preset 0..2] [shift px] [model 3|4]: the presenter uses model 3.
  if (argc > 1) cfg.perfPreset = std::atoi(argv[1]);
  if (argc > 3) cfg.model = std::atoi(argv[3]);
#endif
  engine.configure(cfg); assert(engine.onResize({kSize, kSize}, VK_FORMAT_R8G8B8A8_UNORM));
#endif
#ifdef TEST_COLOR
  assert(argc == 3);
  const int32_t filter_mode = std::atoi(argv[2]);
  std::ifstream file(argv[1], std::ios::binary | std::ios::ate);
  assert(file && file.tellg() > 0);
  std::vector<uint32_t> shader_words(size_t(file.tellg()) / 4); file.seekg(0);
  file.read(reinterpret_cast<char*>(shader_words.data()), shader_words.size() * 4);
  VkDescriptorSetLayoutBinding bindings[2]{};
  for (unsigned i = 0; i < 2; ++i) { bindings[i].binding = i; bindings[i].descriptorCount = 1; bindings[i].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE; bindings[i].stageFlags = VK_SHADER_STAGE_COMPUTE_BIT; }
  VkDescriptorSetLayoutCreateInfo layout_info{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO}; layout_info.bindingCount = 2; layout_info.pBindings = bindings;
  VkDescriptorSetLayout layout; VK_CHECK(vkCreateDescriptorSetLayout(device, &layout_info, nullptr, &layout));
  VkPushConstantRange constants{VK_SHADER_STAGE_COMPUTE_BIT, 0, sizeof(filter_mode)};
  VkPipelineLayoutCreateInfo pipeline_layout_info{VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO};
  pipeline_layout_info.setLayoutCount = 1; pipeline_layout_info.pSetLayouts = &layout;
  pipeline_layout_info.pushConstantRangeCount = 1; pipeline_layout_info.pPushConstantRanges = &constants;
  VkPipelineLayout pipeline_layout; VK_CHECK(vkCreatePipelineLayout(device, &pipeline_layout_info, nullptr, &pipeline_layout));
  VkShaderModuleCreateInfo shader_info{VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO}; shader_info.codeSize = shader_words.size() * 4; shader_info.pCode = shader_words.data();
  VkShaderModule shader; VK_CHECK(vkCreateShaderModule(device, &shader_info, nullptr, &shader));
  VkComputePipelineCreateInfo compute{VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO}; compute.layout = pipeline_layout;
  compute.stage = {VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO}; compute.stage.stage = VK_SHADER_STAGE_COMPUTE_BIT; compute.stage.module = shader; compute.stage.pName = "main";
  VkPipeline pipeline; VK_CHECK(vkCreateComputePipelines(device, VK_NULL_HANDLE, 1, &compute, nullptr, &pipeline));
  VkDescriptorPoolSize pool_size{VK_DESCRIPTOR_TYPE_STORAGE_IMAGE, 2};
  VkDescriptorPoolCreateInfo descriptors_info{VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO}; descriptors_info.maxSets = 1; descriptors_info.poolSizeCount = 1; descriptors_info.pPoolSizes = &pool_size;
  VkDescriptorPool descriptor_pool; VK_CHECK(vkCreateDescriptorPool(device, &descriptors_info, nullptr, &descriptor_pool));
  VkDescriptorSetAllocateInfo set_info{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO}; set_info.descriptorPool = descriptor_pool; set_info.descriptorSetCount = 1; set_info.pSetLayouts = &layout;
  VkDescriptorSet descriptor_set; VK_CHECK(vkAllocateDescriptorSets(device, &set_info, &descriptor_set));
  VkDescriptorImageInfo images[2]{{VK_NULL_HANDLE, current.view, VK_IMAGE_LAYOUT_GENERAL}, {VK_NULL_HANDLE, generated.view, VK_IMAGE_LAYOUT_GENERAL}};
  VkWriteDescriptorSet writes[2]{};
  for (unsigned i = 0; i < 2; ++i) { writes[i].sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET; writes[i].dstSet = descriptor_set; writes[i].dstBinding = i; writes[i].descriptorCount = 1; writes[i].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE; writes[i].pImageInfo = &images[i]; }
  vkUpdateDescriptorSets(device, 2, writes, 0, nullptr);
#endif
  VkCommandBufferBeginInfo begin{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO}; VK_CHECK(vkBeginCommandBuffer(command, &begin));
  auto barrier = [&](VkImage img, VkImageLayout old_layout, VkImageLayout new_layout) {
    VkImageMemoryBarrier b{VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER}; b.oldLayout = old_layout; b.newLayout = new_layout;
    b.srcAccessMask = old_layout == VK_IMAGE_LAYOUT_UNDEFINED ? 0 : VK_ACCESS_MEMORY_WRITE_BIT;
    b.dstAccessMask = VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT;
    b.image = img; b.srcQueueFamilyIndex = b.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    b.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
    vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, 0, nullptr, 0, nullptr, 1, &b);
  };
  const VkClearColorValue color{{0.25f, 0.5f, 0.75f, 1.0f}};
  const VkImageSubresourceRange range{VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
#ifdef TEST_MOTION
  // Deterministic motion (F09). Pattern 0: a bright 8x8 square on a dark background;
  // pattern 1: smooth value noise over the whole frame (a camera pan). The content moves
  // `shift` px to the right per source frame, and `frames` consecutive pairs are generated
  // so the engine's temporal flow predictor sees steady motion. The last half-way frame
  // is compared with the ideal one (content half a step along), the current and the previous.
  constexpr int kDark = 32, kBright = 224;
  const int kSquareY = int(kSize) / 2 - 4, kSquareX = int(kSize) / 2 - 4;
  const int shift = argc > 2 ? std::atoi(argv[2]) : 8;
  const int frames = argc > 4 ? std::max(1, std::atoi(argv[4])) : 1;
  const int pattern = argc > 5 ? std::atoi(argv[5]) : 0;
  // The square sits at x = kSquareX..kSquareX+7 half-way through the last pair.
  const double square_base = kSquareX - shift / 2.0 - double(frames - 1) * shift;
  auto lattice = [](int gx, int gy) {
    uint32_t h = uint32_t(gx) * 73856093u ^ uint32_t(gy) * 19349663u;
    h ^= h >> 13; h *= 0x5bd1e995u; h ^= h >> 15;
    return double(h & 0xFF);
  };
  // Bilinear value noise on a `cell` px lattice, 0..255.
  auto noise = [&](double u, double v, double cell, int seed) {
    const double gx = std::floor(u / cell), gy = std::floor(v / cell);
    const double tx = u / cell - gx, ty = v / cell - gy;
    const int ix = int(gx) + seed * 7919, iy = int(gy) - seed * 104729;
    const double a = lattice(ix, iy), b = lattice(ix + 1, iy), c = lattice(ix, iy + 1), d = lattice(ix + 1, iy + 1);
    const double top = a + (b - a) * tx, bottom = c + (d - c) * tx;
    return top + (bottom - top) * ty;
  };
  auto content = [&](double offset, int x, int y) -> double {
    const double u = x - offset;
    // Pattern 1: one 6 px octave (fine detail only); pattern 2: four octaves, 48 to 6 px,
    // closer to real images, which carry detail at every scale. Both 48..208.
    if (pattern == 1) return 48.0 + noise(u, y, 6.0, 0) * 160.0 / 255.0;
    if (pattern == 2) {
      const double sum = 0.4 * noise(u, y, 48.0, 1) + 0.3 * noise(u, y, 24.0, 2) +
                         0.2 * noise(u, y, 12.0, 3) + 0.1 * noise(u, y, 6.0, 4);
      return 48.0 + sum * 160.0 / 255.0;
    }
    const double cover = (y >= kSquareY && y < kSquareY + 8)
        ? std::clamp(std::min(u + 1.0, square_base + 8.0) - std::max(u, square_base), 0.0, 1.0) : 0.0;
    return kDark + (kBright - kDark) * cover;
  };
  VkBufferCreateInfo staging_info{VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO}; staging_info.size = 2 * VkDeviceSize(kSize) * kSize * 4;
  staging_info.usage = VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
  VkBuffer staging; VK_CHECK(vkCreateBuffer(device, &staging_info, nullptr, &staging));
  VkMemoryRequirements staging_needs; vkGetBufferMemoryRequirements(device, staging, &staging_needs);
  VkMemoryAllocateInfo staging_memory_info{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO}; staging_memory_info.allocationSize = staging_needs.size;
  staging_memory_info.memoryTypeIndex = memory_type(staging_needs.memoryTypeBits, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
  VkDeviceMemory staging_memory; VK_CHECK(vkAllocateMemory(device, &staging_memory_info, nullptr, &staging_memory));
  VK_CHECK(vkBindBufferMemory(device, staging, staging_memory, 0));
  void* staging_pixels; VK_CHECK(vkMapMemory(device, staging_memory, 0, staging_info.size, 0, &staging_pixels));
  // Source pair `pair` (1-based): previous = content at (pair-1)*shift, current = pair*shift.
  auto upload_pair = [&](int pair, VkImageLayout from) {
    for (int frame = 0; frame < 2; ++frame) {
      auto* rgba = static_cast<uint8_t*>(staging_pixels) + size_t(frame) * kSize * kSize * 4;
      const double offset = double(pair - 1 + frame) * shift;
      for (int y = 0; y < int(kSize); ++y) for (int x = 0; x < int(kSize); ++x) {
        uint8_t* p = rgba + (size_t(y) * kSize + x) * 4;
        p[0] = p[1] = p[2] = uint8_t(std::lround(content(offset, x, y))); p[3] = 255;
      }
    }
    int frame_index = 0;
    for (auto input : {previous.image, current.image}) {
      barrier(input, from, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
      VkBufferImageCopy upload{}; upload.bufferOffset = VkDeviceSize(frame_index++) * kSize * kSize * 4;
      upload.imageSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1}; upload.imageExtent = {kSize, kSize, 1};
      vkCmdCopyBufferToImage(command, staging, input, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &upload);
      barrier(input, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
    }
  };
  upload_pair(1, VK_IMAGE_LAYOUT_UNDEFINED);
#else
  for (auto input : {previous.image, current.image}) {
    barrier(input, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
    vkCmdClearColorImage(command, input, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, &color, 1, &range);
    barrier(input, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
  }
#endif
  barrier(generated.image, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_GENERAL);
#ifdef TEST_LSFG
  VK_CHECK(vkEndCommandBuffer(command));
  VkSubmitInfo initial{VK_STRUCTURE_TYPE_SUBMIT_INFO}; initial.commandBufferCount = 1; initial.pCommandBuffers = &command;
  VK_CHECK(vkQueueSubmit(queue, 1, &initial, VK_NULL_HANDLE)); VK_CHECK(vkQueueWaitIdle(queue));
  unsigned generations = 0;
  for (int frame = 1; frame <= 6; ++frame) {
    VK_CHECK(vkResetCommandPool(device, pool, 0)); VK_CHECK(vkBeginCommandBuffer(command, &begin));
    barrier(current.image, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_IMAGE_LAYOUT_GENERAL);
    generations = lsfg_engine->planAt(multiplier - 1, frame, std::chrono::steady_clock::time_point(std::chrono::milliseconds(frame * 34)));
    lsfg_engine->process(command, current.image, kSize, kSize, generations);
    for (unsigned generation = 0; generation < generations; ++generation) {
      lsfg_engine->generateInto(command, generation, 0, generated.image, generated.view, kSize, kSize);
      // Multiple syntheses may reuse the readback target after a write/write barrier.
      barrier(generated.image, VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_LAYOUT_GENERAL);
    }
    barrier(current.image, VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
    if (frame < 6) {
      VK_CHECK(vkEndCommandBuffer(command));
      VK_CHECK(vkQueueSubmit(queue, 1, &initial, VK_NULL_HANDLE)); VK_CHECK(vkQueueWaitIdle(queue));
      std::this_thread::sleep_for(std::chrono::milliseconds(34));
    }
  }
  assert(generations == multiplier - 1);
#elif defined(TEST_COLOR)
  barrier(current.image, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_IMAGE_LAYOUT_GENERAL);
  vkCmdBindPipeline(command, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
  vkCmdBindDescriptorSets(command, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline_layout, 0, 1, &descriptor_set, 0, nullptr);
  vkCmdPushConstants(command, pipeline_layout, VK_SHADER_STAGE_COMPUTE_BIT, 0, sizeof(filter_mode), &filter_mode);
  vkCmdDispatch(command, 8, 8, 1);
#else
  assert(engine.record(command, previous.view, current.view, generated.view, 0.5f));
#ifdef TEST_MOTION
  for (int pair = 2; pair <= frames; ++pair) {
    VK_CHECK(vkEndCommandBuffer(command));
    VkSubmitInfo step{VK_STRUCTURE_TYPE_SUBMIT_INFO}; step.commandBufferCount = 1; step.pCommandBuffers = &command;
    VK_CHECK(vkQueueSubmit(queue, 1, &step, VK_NULL_HANDLE)); VK_CHECK(vkQueueWaitIdle(queue));
    VK_CHECK(vkResetCommandPool(device, pool, 0)); VK_CHECK(vkBeginCommandBuffer(command, &begin));
    upload_pair(pair, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
    barrier(generated.image, VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_LAYOUT_GENERAL);
    assert(engine.record(command, previous.view, current.view, generated.view, 0.5f));
  }
#endif
#endif
  barrier(generated.image, VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
  VkBufferImageCopy copy{}; copy.imageSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1}; copy.imageExtent = {kSize, kSize, 1};
  vkCmdCopyImageToBuffer(command, generated.image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, buffer, 1, &copy);
  VK_CHECK(vkEndCommandBuffer(command));
  VkSubmitInfo submission{VK_STRUCTURE_TYPE_SUBMIT_INFO}; submission.commandBufferCount = 1; submission.pCommandBuffers = &command;
  VK_CHECK(vkQueueSubmit(queue, 1, &submission, VK_NULL_HANDLE)); VK_CHECK(vkQueueWaitIdle(queue));
  void* mapped; VK_CHECK(vkMapMemory(device, readback, 0, VkDeviceSize(kSize) * kSize * 4, 0, &mapped));
  const auto pixels = static_cast<const uint8_t*>(mapped);
#ifdef TEST_MOTION
  {
    // Mean absolute error inside an 8 px margin (content enters and leaves at the edges)
    // against the ideal half-way frame, the current source and the previous one.
    const double last = double(frames);
    double error_ideal = 0, error_current = 0, error_previous = 0;
    int counted = 0;
    for (int y = 8; y < int(kSize) - 8; ++y) for (int x = 8; x < int(kSize) - 8; ++x, ++counted) {
      const double v = pixels[(size_t(y) * kSize + x) * 4];
      error_ideal += std::abs(v - content((last - 0.5) * shift, x, y));
      error_current += std::abs(v - content(last * shift, x, y));
      error_previous += std::abs(v - content((last - 1) * shift, x, y));
    }
    std::printf("Motion half-way frame (pattern %d, shift %d px, %d pairs, preset %d, model %d): mean abs error "
                "vs ideal %.2f, vs current %.2f, vs previous %.2f",
                pattern, shift, frames, cfg.perfPreset, cfg.model, error_ideal / counted, error_current / counted,
                error_previous / counted);
    if (pattern == 0) {
      // Full-brightness pixels and their centroid; half-bright ones are what a cross-fade leaves.
      int bright = 0, half = 0;
      double sum_x = 0;
      for (int y = 0; y < int(kSize); ++y) for (int x = 0; x < int(kSize); ++x) {
        const int v = pixels[(size_t(y) * kSize + x) * 4];
        if (v >= kDark + (kBright - kDark) * 3 / 4) { ++bright; sum_x += x; }
        else if (v >= kDark + (kBright - kDark) / 4) ++half;
      }
      std::printf("; square: %d bright pixels (64 expected), centroid x %.2f (ideal %.2f, current %.2f), %d half-bright",
                  bright, bright ? sum_x / bright : -1.0, kSquareX + 3.5, kSquareX + 3.5 + shift / 2.0, half);
    }
    // Regression guard on multi-scale content (pattern 2): a still image comes out unchanged,
    // and a moving one is estimated better by the half-way frame than by repeating either
    // source. Software Vulkan and synthetic content: a guard, not proof of quality in games.
    bool ok = true;
    if (pattern == 2) {
      ok = shift == 0 ? error_ideal / counted <= 1.0 : error_ideal < error_current && error_ideal < error_previous;
    }
    std::printf("%s\n", pattern == 2 ? (ok ? " -> passed" : " -> FAILED") : " (measurement only)");
    vkUnmapMemory(device, readback);
    vkDestroyBuffer(device, staging, nullptr); vkFreeMemory(device, staging_memory, nullptr);
    engine.destroy();
    for (auto img : {previous, current, generated}) { vkDestroyImageView(device, img.view, nullptr); vkDestroyImage(device, img.image, nullptr); vkFreeMemory(device, img.memory, nullptr); }
    vkDestroyBuffer(device, buffer, nullptr); vkFreeMemory(device, readback, nullptr); vkDestroyCommandPool(device, pool, nullptr);
    vkDestroyDevice(device, nullptr); vkDestroyInstance(instance, nullptr);
    return ok ? 0 : 4;
  }
#endif
  int expected[3] = {64, 128, 191};
#ifdef TEST_COLOR
  if (filter_mode == 1) expected[0] = expected[1] = expected[2] = 118;
  if (filter_mode == 2) { expected[0] = 56; expected[1] = 128; expected[2] = 199; }
  if (filter_mode == 3) { expected[0] = 67; expected[1] = 128; expected[2] = 176; }
  if (filter_mode == 4) { expected[0] = 42; expected[1] = 131; expected[2] = 217; }  // 15q: vivid
#endif
  for (int i = 0; i < int(kSize * kSize); ++i) {
    if (std::abs(int(pixels[i * 4]) - expected[0]) > 2 || std::abs(int(pixels[i * 4 + 1]) - expected[1]) > 2 ||
        std::abs(int(pixels[i * 4 + 2]) - expected[2]) > 2 || pixels[i * 4 + 3] < 253) {
      std::fprintf(stderr, "Invalid synthesis pixel %d: %u %u %u %u\n", i, pixels[i*4], pixels[i*4+1], pixels[i*4+2], pixels[i*4+3]); return 3;
    }
  }
  vkUnmapMemory(device, readback);
#ifdef TEST_LSFG
  lsfg_engine.reset();
#elif defined(TEST_COLOR)
  vkDestroyDescriptorPool(device, descriptor_pool, nullptr); vkDestroyPipeline(device, pipeline, nullptr);
  vkDestroyShaderModule(device, shader, nullptr); vkDestroyPipelineLayout(device, pipeline_layout, nullptr);
  vkDestroyDescriptorSetLayout(device, layout, nullptr);
#else
  engine.destroy();
#endif
  for (auto img : {previous, current, generated}) { vkDestroyImageView(device, img.view, nullptr); vkDestroyImage(device, img.image, nullptr); vkFreeMemory(device, img.memory, nullptr); }
  vkDestroyBuffer(device, buffer, nullptr); vkFreeMemory(device, readback, nullptr); vkDestroyCommandPool(device, pool, nullptr);
  vkDestroyDevice(device, nullptr); vkDestroyInstance(instance, nullptr);
#ifdef TEST_COLOR
  std::printf("SDR color filter mode %d: passed\n", filter_mode);
#else
  std::puts("Frame generation software Vulkan synthesis: passed");
#endif
}
