// SGSR (Snapdragon Game Super Resolution 1) and Lanczos-2 (15k) as the presenter runs
// them: the real guest output shaders (rectangle vertex shader, SGSR, Lanczos and bilinear
// pixel shaders, compiled from their XESL sources by tools/test-presentation-host.sh) drawn
// on software Vulkan with the presenter's push constant layout, a 32x32 image upscaled to
// 64x64 and 48x48. Each must keep flat areas as they are, never overshoot the texels around
// an edge, and make an edge steeper than bilinear filtering does.

#include <vulkan/vulkan.h>

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <vector>

#include "guest_output_bilinear_ps.h"
#include "guest_output_lanczos_ps.h"
#include "guest_output_sgsr_ps.h"
#include "guest_output_triangle_strip_rect_vs.h"

#define VK_CHECK(expr)                                                     \
  do {                                                                     \
    const VkResult result_ = (expr);                                       \
    if (result_ != VK_SUCCESS) {                                           \
      std::fprintf(stderr, "%s: %d\n", #expr, int(result_));               \
      std::exit(1);                                                        \
    }                                                                      \
  } while (0)

namespace {

int failures = 0;

void expect(bool ok, const char* what) {
  if (!ok) {
    std::fprintf(stderr, "FAILED: %s\n", what);
    ++failures;
  }
}

// The presenter's layouts (presenter.h, vulkan_presenter.cc).
struct RectangleConstants {
  float x, y, width, height;
};
struct BilinearConstants {
  int32_t output_offset[2];
  float output_size_inv[2];
};
struct SgsrConstants {
  int32_t output_offset[2];
  float output_size_inv[2];
  float viewport_info[4];
  float edge_sharpness;
};

// Lanczos-2 shares SGSR's layout and constants (presenter.h); the sharpness is unused.
enum class Filter { kBilinear, kSgsr, kLanczos };
const char* Name(Filter filter) {
  return filter == Filter::kSgsr ? "SGSR" : filter == Filter::kLanczos ? "Lanczos" : "bilinear";
}

constexpr uint32_t kInput = 32;
constexpr uint8_t kDark = 51, kBright = 204;

struct Vulkan {
  VkInstance instance{};
  VkPhysicalDevice physical{};
  VkDevice device{};
  VkQueue queue{};
  uint32_t family = 0;
  VkPhysicalDeviceMemoryProperties memory{};
  VkCommandPool pool{};

  uint32_t MemoryType(uint32_t bits, VkMemoryPropertyFlags flags) const {
    for (uint32_t i = 0; i < memory.memoryTypeCount; ++i) {
      if ((bits & (1u << i)) && (memory.memoryTypes[i].propertyFlags & flags) == flags) return i;
    }
    std::fprintf(stderr, "no memory type\n");
    std::exit(1);
  }
};

struct Image {
  VkImage image{};
  VkImageView view{};
  VkDeviceMemory memory{};
};

Image MakeImage(const Vulkan& vk, uint32_t width, uint32_t height, VkImageUsageFlags usage) {
  Image out;
  VkImageCreateInfo info{VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO};
  info.imageType = VK_IMAGE_TYPE_2D;
  info.format = VK_FORMAT_R8G8B8A8_UNORM;
  info.extent = {width, height, 1};
  info.mipLevels = info.arrayLayers = 1;
  info.samples = VK_SAMPLE_COUNT_1_BIT;
  info.tiling = VK_IMAGE_TILING_OPTIMAL;
  info.usage = usage;
  VK_CHECK(vkCreateImage(vk.device, &info, nullptr, &out.image));
  VkMemoryRequirements requirements;
  vkGetImageMemoryRequirements(vk.device, out.image, &requirements);
  VkMemoryAllocateInfo allocation{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
  allocation.allocationSize = requirements.size;
  allocation.memoryTypeIndex = vk.MemoryType(requirements.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
  VK_CHECK(vkAllocateMemory(vk.device, &allocation, nullptr, &out.memory));
  VK_CHECK(vkBindImageMemory(vk.device, out.image, out.memory, 0));
  VkImageViewCreateInfo view{VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};
  view.image = out.image;
  view.viewType = VK_IMAGE_VIEW_TYPE_2D;
  view.format = info.format;
  view.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
  VK_CHECK(vkCreateImageView(vk.device, &view, nullptr, &out.view));
  return out;
}

struct Buffer {
  VkBuffer buffer{};
  VkDeviceMemory memory{};
  void* mapped = nullptr;
};

Buffer MakeBuffer(const Vulkan& vk, VkDeviceSize size, VkBufferUsageFlags usage) {
  Buffer out;
  VkBufferCreateInfo info{VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};
  info.size = size;
  info.usage = usage;
  VK_CHECK(vkCreateBuffer(vk.device, &info, nullptr, &out.buffer));
  VkMemoryRequirements requirements;
  vkGetBufferMemoryRequirements(vk.device, out.buffer, &requirements);
  VkMemoryAllocateInfo allocation{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
  allocation.allocationSize = requirements.size;
  allocation.memoryTypeIndex = vk.MemoryType(
      requirements.memoryTypeBits, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
  VK_CHECK(vkAllocateMemory(vk.device, &allocation, nullptr, &out.memory));
  VK_CHECK(vkBindBufferMemory(vk.device, out.buffer, out.memory, 0));
  VK_CHECK(vkMapMemory(vk.device, out.memory, 0, VK_WHOLE_SIZE, 0, &out.mapped));
  return out;
}

void Barrier(VkCommandBuffer command, VkImage image, VkImageLayout from, VkImageLayout to) {
  VkImageMemoryBarrier b{VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
  b.oldLayout = from;
  b.newLayout = to;
  b.srcAccessMask = from == VK_IMAGE_LAYOUT_UNDEFINED ? 0 : VK_ACCESS_MEMORY_WRITE_BIT;
  b.dstAccessMask = VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT;
  b.image = image;
  b.srcQueueFamilyIndex = b.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
  b.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
  vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, 0,
                       nullptr, 0, nullptr, 1, &b);
}

VkShaderModule Module(const Vulkan& vk, const uint32_t* code, size_t size) {
  VkShaderModuleCreateInfo info{VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO};
  info.codeSize = size;
  info.pCode = code;
  VkShaderModule module;
  VK_CHECK(vkCreateShaderModule(vk.device, &info, nullptr, &module));
  return module;
}

// Draws [pixel] over the whole [width]x[height] target from [source] (sampled, read-only
// layout) like the presenter's final effect pass, and returns the target's green channel.
std::vector<uint8_t> Paint(const Vulkan& vk, const Image& source, uint32_t width, uint32_t height, Filter filter) {
  // The presenter's descriptor set: the guest output image and an immutable linear sampler.
  VkSamplerCreateInfo sampler_info{VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO};
  sampler_info.magFilter = sampler_info.minFilter = VK_FILTER_LINEAR;
  sampler_info.mipmapMode = VK_SAMPLER_MIPMAP_MODE_NEAREST;
  sampler_info.addressModeU = sampler_info.addressModeV = sampler_info.addressModeW =
      VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
  sampler_info.maxLod = 0.25f;
  VkSampler sampler;
  VK_CHECK(vkCreateSampler(vk.device, &sampler_info, nullptr, &sampler));
  VkDescriptorSetLayoutBinding bindings[2]{};
  bindings[0].binding = 0;
  bindings[0].descriptorType = VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE;
  bindings[0].descriptorCount = 1;
  bindings[0].stageFlags = VK_SHADER_STAGE_FRAGMENT_BIT;
  bindings[1].binding = 1;
  bindings[1].descriptorType = VK_DESCRIPTOR_TYPE_SAMPLER;
  bindings[1].descriptorCount = 1;
  bindings[1].stageFlags = VK_SHADER_STAGE_FRAGMENT_BIT;
  bindings[1].pImmutableSamplers = &sampler;
  VkDescriptorSetLayoutCreateInfo set_layout_info{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO};
  set_layout_info.bindingCount = 2;
  set_layout_info.pBindings = bindings;
  VkDescriptorSetLayout set_layout;
  VK_CHECK(vkCreateDescriptorSetLayout(vk.device, &set_layout_info, nullptr, &set_layout));
  const bool sgsr_layout = filter != Filter::kBilinear;
  const uint32_t effect_size = sgsr_layout ? sizeof(SgsrConstants) : sizeof(BilinearConstants);
  VkPushConstantRange ranges[2] = {{VK_SHADER_STAGE_VERTEX_BIT, 0, sizeof(RectangleConstants)},
                                   {VK_SHADER_STAGE_FRAGMENT_BIT, sizeof(RectangleConstants), effect_size}};
  VkPipelineLayoutCreateInfo layout_info{VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO};
  layout_info.setLayoutCount = 1;
  layout_info.pSetLayouts = &set_layout;
  layout_info.pushConstantRangeCount = 2;
  layout_info.pPushConstantRanges = ranges;
  VkPipelineLayout layout;
  VK_CHECK(vkCreatePipelineLayout(vk.device, &layout_info, nullptr, &layout));

  VkAttachmentDescription attachment{};
  attachment.format = VK_FORMAT_R8G8B8A8_UNORM;
  attachment.samples = VK_SAMPLE_COUNT_1_BIT;
  attachment.loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
  attachment.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
  attachment.stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
  attachment.stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
  attachment.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
  attachment.finalLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
  VkAttachmentReference reference{0, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL};
  VkSubpassDescription subpass{};
  subpass.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
  subpass.colorAttachmentCount = 1;
  subpass.pColorAttachments = &reference;
  VkRenderPassCreateInfo pass_info{VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO};
  pass_info.attachmentCount = 1;
  pass_info.pAttachments = &attachment;
  pass_info.subpassCount = 1;
  pass_info.pSubpasses = &subpass;
  VkRenderPass pass;
  VK_CHECK(vkCreateRenderPass(vk.device, &pass_info, nullptr, &pass));

  VkShaderModule vs = Module(vk, guest_output_triangle_strip_rect_vs, sizeof(guest_output_triangle_strip_rect_vs));
  VkShaderModule ps = filter == Filter::kSgsr    ? Module(vk, guest_output_sgsr_ps, sizeof(guest_output_sgsr_ps))
                     : filter == Filter::kLanczos ? Module(vk, guest_output_lanczos_ps, sizeof(guest_output_lanczos_ps))
                                                  : Module(vk, guest_output_bilinear_ps, sizeof(guest_output_bilinear_ps));
  VkPipelineShaderStageCreateInfo stages[2]{};
  stages[0].sType = stages[1].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
  stages[0].stage = VK_SHADER_STAGE_VERTEX_BIT;
  stages[0].module = vs;
  stages[0].pName = "main";
  stages[1].stage = VK_SHADER_STAGE_FRAGMENT_BIT;
  stages[1].module = ps;
  stages[1].pName = "main";
  VkPipelineVertexInputStateCreateInfo vertex_input{VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO};
  VkPipelineInputAssemblyStateCreateInfo assembly{VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO};
  assembly.topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP;
  VkViewport viewport{0.0f, 0.0f, float(width), float(height), 0.0f, 1.0f};
  VkRect2D scissor{{0, 0}, {width, height}};
  VkPipelineViewportStateCreateInfo viewport_state{VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO};
  viewport_state.viewportCount = 1;
  viewport_state.pViewports = &viewport;
  viewport_state.scissorCount = 1;
  viewport_state.pScissors = &scissor;
  VkPipelineRasterizationStateCreateInfo raster{VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO};
  raster.polygonMode = VK_POLYGON_MODE_FILL;
  raster.cullMode = VK_CULL_MODE_NONE;
  raster.lineWidth = 1.0f;
  VkPipelineMultisampleStateCreateInfo multisample{VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO};
  multisample.rasterizationSamples = VK_SAMPLE_COUNT_1_BIT;
  VkPipelineColorBlendAttachmentState blend_attachment{};
  blend_attachment.colorWriteMask = VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT |
                                    VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT;
  VkPipelineColorBlendStateCreateInfo blend{VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO};
  blend.attachmentCount = 1;
  blend.pAttachments = &blend_attachment;
  VkGraphicsPipelineCreateInfo pipeline_info{VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO};
  pipeline_info.stageCount = 2;
  pipeline_info.pStages = stages;
  pipeline_info.pVertexInputState = &vertex_input;
  pipeline_info.pInputAssemblyState = &assembly;
  pipeline_info.pViewportState = &viewport_state;
  pipeline_info.pRasterizationState = &raster;
  pipeline_info.pMultisampleState = &multisample;
  pipeline_info.pColorBlendState = &blend;
  pipeline_info.layout = layout;
  pipeline_info.renderPass = pass;
  VkPipeline pipeline;
  VK_CHECK(vkCreateGraphicsPipelines(vk.device, VK_NULL_HANDLE, 1, &pipeline_info, nullptr, &pipeline));

  Image target = MakeImage(vk, width, height,
                           VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT);
  VkFramebufferCreateInfo framebuffer_info{VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO};
  framebuffer_info.renderPass = pass;
  framebuffer_info.attachmentCount = 1;
  framebuffer_info.pAttachments = &target.view;
  framebuffer_info.width = width;
  framebuffer_info.height = height;
  framebuffer_info.layers = 1;
  VkFramebuffer framebuffer;
  VK_CHECK(vkCreateFramebuffer(vk.device, &framebuffer_info, nullptr, &framebuffer));

  VkDescriptorPoolSize sizes[2] = {{VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE, 1}, {VK_DESCRIPTOR_TYPE_SAMPLER, 1}};
  VkDescriptorPoolCreateInfo descriptor_pool_info{VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO};
  descriptor_pool_info.maxSets = 1;
  descriptor_pool_info.poolSizeCount = 2;
  descriptor_pool_info.pPoolSizes = sizes;
  VkDescriptorPool descriptor_pool;
  VK_CHECK(vkCreateDescriptorPool(vk.device, &descriptor_pool_info, nullptr, &descriptor_pool));
  VkDescriptorSetAllocateInfo set_info{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO};
  set_info.descriptorPool = descriptor_pool;
  set_info.descriptorSetCount = 1;
  set_info.pSetLayouts = &set_layout;
  VkDescriptorSet set;
  VK_CHECK(vkAllocateDescriptorSets(vk.device, &set_info, &set));
  VkDescriptorImageInfo image_info{VK_NULL_HANDLE, source.view, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL};
  VkWriteDescriptorSet write{VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET};
  write.dstSet = set;
  write.dstBinding = 0;
  write.descriptorCount = 1;
  write.descriptorType = VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE;
  write.pImageInfo = &image_info;
  vkUpdateDescriptorSets(vk.device, 1, &write, 0, nullptr);

  Buffer readback = MakeBuffer(vk, VkDeviceSize(width) * height * 4, VK_BUFFER_USAGE_TRANSFER_DST_BIT);
  VkCommandBufferAllocateInfo command_info{VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};
  command_info.commandPool = vk.pool;
  command_info.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
  command_info.commandBufferCount = 1;
  VkCommandBuffer command;
  VK_CHECK(vkAllocateCommandBuffers(vk.device, &command_info, &command));
  VkCommandBufferBeginInfo begin{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
  VK_CHECK(vkBeginCommandBuffer(command, &begin));
  VkClearValue clear{};
  VkRenderPassBeginInfo pass_begin{VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO};
  pass_begin.renderPass = pass;
  pass_begin.framebuffer = framebuffer;
  pass_begin.renderArea = scissor;
  pass_begin.clearValueCount = 1;
  pass_begin.pClearValues = &clear;
  vkCmdBeginRenderPass(command, &pass_begin, VK_SUBPASS_CONTENTS_INLINE);
  vkCmdBindPipeline(command, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
  vkCmdBindDescriptorSets(command, VK_PIPELINE_BIND_POINT_GRAPHICS, layout, 0, 1, &set, 0, nullptr);
  const RectangleConstants rectangle{-1.0f, -1.0f, 2.0f, 2.0f};
  vkCmdPushConstants(command, layout, VK_SHADER_STAGE_VERTEX_BIT, 0, sizeof(rectangle), &rectangle);
  if (sgsr_layout) {
    const SgsrConstants constants{{0, 0}, {1.0f / float(width), 1.0f / float(height)},
                                  {1.0f / kInput, 1.0f / kInput, float(kInput), float(kInput)}, 2.0f};
    vkCmdPushConstants(command, layout, VK_SHADER_STAGE_FRAGMENT_BIT, sizeof(rectangle), sizeof(constants), &constants);
  } else {
    const BilinearConstants constants{{0, 0}, {1.0f / float(width), 1.0f / float(height)}};
    vkCmdPushConstants(command, layout, VK_SHADER_STAGE_FRAGMENT_BIT, sizeof(rectangle), sizeof(constants), &constants);
  }
  vkCmdDraw(command, 4, 1, 0, 0);
  vkCmdEndRenderPass(command);
  VkBufferImageCopy region{};
  region.imageSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
  region.imageExtent = {width, height, 1};
  vkCmdCopyImageToBuffer(command, target.image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, readback.buffer, 1, &region);
  VK_CHECK(vkEndCommandBuffer(command));
  VkSubmitInfo submit{VK_STRUCTURE_TYPE_SUBMIT_INFO};
  submit.commandBufferCount = 1;
  submit.pCommandBuffers = &command;
  VK_CHECK(vkQueueSubmit(vk.queue, 1, &submit, VK_NULL_HANDLE));
  VK_CHECK(vkQueueWaitIdle(vk.queue));

  std::vector<uint8_t> green(size_t(width) * height);
  const auto* pixels = static_cast<const uint8_t*>(readback.mapped);
  bool gray = true;
  for (size_t i = 0; i < green.size(); ++i) {
    green[i] = pixels[i * 4 + 1];
    gray = gray && pixels[i * 4] == pixels[i * 4 + 1] && pixels[i * 4 + 2] == pixels[i * 4 + 1] &&
           pixels[i * 4 + 3] == 255;
  }
  expect(gray, "a gray input stays gray and opaque");
  // The test process ends right after; Vulkan objects are reclaimed with it.
  return green;
}

}  // namespace

int main() {
  Vulkan vk;
  VkApplicationInfo app{VK_STRUCTURE_TYPE_APPLICATION_INFO};
  app.apiVersion = VK_API_VERSION_1_1;
  VkInstanceCreateInfo instance_info{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};
  instance_info.pApplicationInfo = &app;
  VK_CHECK(vkCreateInstance(&instance_info, nullptr, &vk.instance));
  uint32_t count = 0;
  VK_CHECK(vkEnumeratePhysicalDevices(vk.instance, &count, nullptr));
  if (!count) {
    std::fprintf(stderr, "No software Vulkan device\n");
    return 2;
  }
  std::vector<VkPhysicalDevice> devices(count);
  VK_CHECK(vkEnumeratePhysicalDevices(vk.instance, &count, devices.data()));
  vk.physical = devices[0];
  VkPhysicalDeviceProperties properties;
  vkGetPhysicalDeviceProperties(vk.physical, &properties);
  std::printf("Vulkan test device: %s\n", properties.deviceName);
  vkGetPhysicalDeviceQueueFamilyProperties(vk.physical, &count, nullptr);
  std::vector<VkQueueFamilyProperties> families(count);
  vkGetPhysicalDeviceQueueFamilyProperties(vk.physical, &count, families.data());
  while (vk.family < count && !(families[vk.family].queueFlags & VK_QUEUE_GRAPHICS_BIT)) ++vk.family;
  float priority = 1.0f;
  VkDeviceQueueCreateInfo queue_info{VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO};
  queue_info.queueFamilyIndex = vk.family;
  queue_info.queueCount = 1;
  queue_info.pQueuePriorities = &priority;
  VkDeviceCreateInfo device_info{VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO};
  device_info.queueCreateInfoCount = 1;
  device_info.pQueueCreateInfos = &queue_info;
  VK_CHECK(vkCreateDevice(vk.physical, &device_info, nullptr, &vk.device));
  vkGetDeviceQueue(vk.device, vk.family, 0, &vk.queue);
  vkGetPhysicalDeviceMemoryProperties(vk.physical, &vk.memory);
  VkCommandPoolCreateInfo pool_info{VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO};
  pool_info.queueFamilyIndex = vk.family;
  VK_CHECK(vkCreateCommandPool(vk.device, &pool_info, nullptr, &vk.pool));

  // The guest output: dark on the left, bright from column 16 on, one vertical edge.
  Image source = MakeImage(vk, kInput, kInput, VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT);
  Buffer upload = MakeBuffer(vk, kInput * kInput * 4, VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
  auto* texels = static_cast<uint8_t*>(upload.mapped);
  for (uint32_t y = 0; y < kInput; ++y) {
    for (uint32_t x = 0; x < kInput; ++x) {
      const uint8_t value = x < kInput / 2 ? kDark : kBright;
      uint8_t* texel = texels + (y * kInput + x) * 4;
      texel[0] = texel[1] = texel[2] = value;
      texel[3] = 255;
    }
  }
  VkCommandBufferAllocateInfo command_info{VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};
  command_info.commandPool = vk.pool;
  command_info.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
  command_info.commandBufferCount = 1;
  VkCommandBuffer command;
  VK_CHECK(vkAllocateCommandBuffers(vk.device, &command_info, &command));
  VkCommandBufferBeginInfo begin{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
  VK_CHECK(vkBeginCommandBuffer(command, &begin));
  Barrier(command, source.image, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
  VkBufferImageCopy region{};
  region.imageSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
  region.imageExtent = {kInput, kInput, 1};
  vkCmdCopyBufferToImage(command, upload.buffer, source.image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &region);
  Barrier(command, source.image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
  VK_CHECK(vkEndCommandBuffer(command));
  VkSubmitInfo submit{VK_STRUCTURE_TYPE_SUBMIT_INFO};
  submit.commandBufferCount = 1;
  submit.pCommandBuffers = &command;
  VK_CHECK(vkQueueSubmit(vk.queue, 1, &submit, VK_NULL_HANDLE));
  VK_CHECK(vkQueueWaitIdle(vk.queue));

  for (Filter filter : {Filter::kSgsr, Filter::kLanczos})
  for (uint32_t size : {64u, 48u}) {
    const std::vector<uint8_t> bilinear = Paint(vk, source, size, size, Filter::kBilinear);
    const std::vector<uint8_t> sgsr = Paint(vk, source, size, size, filter);
    const uint32_t row = size / 2;
    std::printf("  %s %ux%u row %u around the edge (x: bilinear / filter):", Name(filter), size, size, row);
    for (uint32_t x = size / 2 - 4; x < size / 2 + 4; ++x) {
      std::printf(" %u:%u/%u", x, bilinear[row * size + x], sgsr[row * size + x]);
    }
    std::printf("\n");
    int changed = 0;
    bool in_range = true, flat_kept = true;
    for (uint32_t y = 0; y < size; ++y) {
      for (uint32_t x = 0; x < size; ++x) {
        const int b = bilinear[y * size + x], s = sgsr[y * size + x];
        in_range = in_range && s >= kDark - 1 && s <= kBright + 1;
        // Four or more output pixels from the edge, both filters read only one side.
        if (x + 4 < size / 2 || x >= size / 2 + 4) flat_kept = flat_kept && std::abs(s - b) <= 1;
        if (std::abs(s - b) >= 2) ++changed;
      }
    }
    expect(in_range, "the filter never overshoots the texels around the edge");
    expect(flat_kept, "flat areas come out as bilinear filtering leaves them");
    expect(changed > 0, "the filter changes the pixels at the edge");
    // Steeper: the two pixels that straddle the edge are further apart than with bilinear.
    const uint32_t left = row * size + size / 2 - 1, right = left + 1;
    expect(int(sgsr[right]) - int(sgsr[left]) >= int(bilinear[right]) - int(bilinear[left]),
           "the edge is at least as steep as with bilinear filtering");
    int steeper_rows = 0;
    for (uint32_t y = 0; y < size; ++y) {
      const uint32_t l = y * size + size / 2 - 1;
      if (int(sgsr[l + 1]) - int(sgsr[l]) > int(bilinear[l + 1]) - int(bilinear[l])) ++steeper_rows;
    }
    std::printf("  %s %ux%u: %d pixels changed, %d of %u rows steeper at the edge\n", Name(filter), size, size,
                changed, steeper_rows, size);
    expect(steeper_rows > int(size / 2), "most rows come out steeper than with bilinear filtering");
  }

  if (failures) {
    std::fprintf(stderr, "sgsr_software_test: %d failure(s)\n", failures);
    return 1;
  }
  std::printf("SGSR and Lanczos: passed\n");
  return 0;
}
