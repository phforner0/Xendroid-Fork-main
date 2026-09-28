// Compiles translated guest shaders (from corpus_tool) into graphics
// pipelines on a Vulkan driver (meant for Turnip over the freedreno noop
// drm-shim) and prints the driver's pipeline executable statistics.
// Usage: ir3stats <partner.vert.spv> <partner.frag.spv> <file.spv>...
//   ps_*.spv are paired with the partner vertex shader (16 vec4 outputs),
//   vs_*.spv with the partner fragment shader (reads 16 vec4 inputs).
// Output: CSV lines "file,stage,statistic,value".
#include <vulkan/vulkan.h>

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <map>
#include <string>
#include <vector>

namespace {

std::vector<uint32_t> ReadSpirv(const char* path) {
  std::vector<uint32_t> words;
  FILE* f = std::fopen(path, "rb");
  if (!f) return words;
  std::fseek(f, 0, SEEK_END);
  long size = std::ftell(f);
  std::fseek(f, 0, SEEK_SET);
  words.resize(size_t(size) / 4);
  if (std::fread(words.data(), 4, words.size(), f) != words.size()) {
    words.clear();
  }
  std::fclose(f);
  return words;
}

struct Binding {
  VkDescriptorType type;
  uint32_t count;
};

// set -> binding -> descriptor
using Layout = std::map<uint32_t, std::map<uint32_t, Binding>>;

struct Reflection {
  Layout layout;
  uint32_t color_outputs = 0;  // Location mask of fragment outputs.
  bool writes_depth = false;
  bool writes_stencil = false;
};

bool Reflect(const std::vector<uint32_t>& w, Reflection& out) {
  if (w.size() < 5 || w[0] != 0x07230203) return false;
  std::map<uint32_t, uint32_t> set, binding, location, builtin;
  std::map<uint32_t, bool> block, buffer_block;
  struct Type {
    uint32_t op = 0;
    std::vector<uint32_t> operands;
  };
  std::map<uint32_t, Type> types;
  std::map<uint32_t, uint32_t> constants;
  struct Var {
    uint32_t type, storage;
  };
  std::map<uint32_t, Var> vars;
  for (size_t i = 5; i < w.size();) {
    uint32_t length = w[i] >> 16, op = w[i] & 0xFFFF;
    if (!length || i + length > w.size()) return false;
    const uint32_t* o = &w[i + 1];
    switch (op) {
      case 71:  // OpDecorate
        if (length >= 4) {
          if (o[1] == 34) set[o[0]] = o[2];
          if (o[1] == 33) binding[o[0]] = o[2];
          if (o[1] == 30) location[o[0]] = o[2];
          if (o[1] == 11) builtin[o[0]] = o[2];
        }
        if (length >= 3) {
          if (o[1] == 2) block[o[0]] = true;
          if (o[1] == 3) buffer_block[o[0]] = true;
        }
        break;
      case 43:  // OpConstant
        if (length >= 4) constants[o[1]] = o[2];
        break;
      case 59:  // OpVariable
        vars[o[1]] = {o[0], o[2]};
        break;
      default:
        if ((op >= 19 && op <= 39) || op == 322) {  // OpType*
          Type t;
          t.op = op;
          t.operands.assign(o + 1, o + length - 1);
          types[o[0]] = t;
        }
        break;
    }
    i += length;
  }
  for (const auto& [id, var] : vars) {
    // Pointer -> pointee.
    auto ptr = types.find(var.type);
    if (ptr == types.end() || ptr->second.op != 32) continue;
    uint32_t type_id = ptr->second.operands[1];
    uint32_t count = 1;
    while (types.count(type_id) &&
           (types[type_id].op == 28 || types[type_id].op == 29)) {
      if (types[type_id].op == 28) {
        count *= constants[types[type_id].operands[1]];
      }
      type_id = types[type_id].operands[0];
    }
    const Type& t = types[type_id];
    if (var.storage == 3) {  // Output
      if (builtin.count(id)) {
        if (builtin[id] == 22) out.writes_depth = true;       // FragDepth
        if (builtin[id] == 5014) out.writes_stencil = true;  // FragStencilRef
      } else if (location.count(id)) {
        out.color_outputs |= 1u << location[id];
      }
      continue;
    }
    if (!set.count(id) || !binding.count(id)) continue;
    VkDescriptorType type;
    if (t.op == 30) {  // OpTypeStruct
      if (var.storage == 12 || buffer_block[type_id]) {
        type = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
      } else {
        type = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
      }
    } else if (t.op == 25) {  // OpTypeImage: sampled type, dim, depth,
                              // arrayed, ms, sampled, format
      bool buffer = t.operands[1] == 5;
      bool storage = t.operands[5] == 2;
      type = buffer ? (storage ? VK_DESCRIPTOR_TYPE_STORAGE_TEXEL_BUFFER
                               : VK_DESCRIPTOR_TYPE_UNIFORM_TEXEL_BUFFER)
                    : (storage ? VK_DESCRIPTOR_TYPE_STORAGE_IMAGE
                               : VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE);
    } else if (t.op == 26) {
      type = VK_DESCRIPTOR_TYPE_SAMPLER;
    } else if (t.op == 27) {
      type = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    } else {
      continue;
    }
    out.layout[set[id]][binding[id]] = {type, count};
  }
  return true;
}

#define CHECK(x)                                                     \
  do {                                                               \
    VkResult r_ = (x);                                               \
    if (r_ != VK_SUCCESS) {                                          \
      std::fprintf(stderr, "%s:%d %s -> %d\n", __FILE__, __LINE__, #x, \
                   int(r_));                                         \
      return false;                                                  \
    }                                                                \
  } while (0)

struct Context {
  VkInstance instance;
  VkPhysicalDevice physical_device;
  VkDevice device;
  PFN_vkGetPipelineExecutablePropertiesKHR get_properties;
  PFN_vkGetPipelineExecutableStatisticsKHR get_statistics;
};

bool CreateModule(const Context& c, const std::vector<uint32_t>& code,
                  VkShaderModule& module) {
  VkShaderModuleCreateInfo info = {VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO};
  info.codeSize = code.size() * 4;
  info.pCode = code.data();
  CHECK(vkCreateShaderModule(c.device, &info, nullptr, &module));
  return true;
}

bool Compile(const Context& c, const char* name,
             const std::vector<uint32_t>& vs, const std::vector<uint32_t>& fs,
             const Reflection& reflection) {
  // Descriptor set layouts for every set up to the highest used one.
  uint32_t set_count =
      reflection.layout.empty() ? 0 : reflection.layout.rbegin()->first + 1;
  std::vector<VkDescriptorSetLayout> set_layouts(set_count);
  for (uint32_t s = 0; s < set_count; ++s) {
    std::vector<VkDescriptorSetLayoutBinding> bindings;
    auto it = reflection.layout.find(s);
    if (it != reflection.layout.end()) {
      for (const auto& [b, d] : it->second) {
        VkDescriptorSetLayoutBinding lb = {};
        lb.binding = b;
        lb.descriptorType = d.type;
        lb.descriptorCount = d.count;
        lb.stageFlags = VK_SHADER_STAGE_ALL_GRAPHICS;
        bindings.push_back(lb);
      }
    }
    VkDescriptorSetLayoutCreateInfo info = {
        VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO};
    info.bindingCount = uint32_t(bindings.size());
    info.pBindings = bindings.data();
    CHECK(vkCreateDescriptorSetLayout(c.device, &info, nullptr,
                                      &set_layouts[s]));
  }
  VkPipelineLayoutCreateInfo layout_info = {
      VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO};
  layout_info.setLayoutCount = set_count;
  layout_info.pSetLayouts = set_layouts.data();
  VkPipelineLayout layout;
  CHECK(vkCreatePipelineLayout(c.device, &layout_info, nullptr, &layout));

  VkShaderModule modules[2];
  if (!CreateModule(c, vs, modules[0]) || !CreateModule(c, fs, modules[1])) {
    return false;
  }
  VkPipelineShaderStageCreateInfo stages[2] = {};
  for (int i = 0; i < 2; ++i) {
    stages[i].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    stages[i].stage = i ? VK_SHADER_STAGE_FRAGMENT_BIT : VK_SHADER_STAGE_VERTEX_BIT;
    stages[i].module = modules[i];
    stages[i].pName = "main";
  }
  VkPipelineVertexInputStateCreateInfo vertex_input = {
      VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO};
  VkPipelineInputAssemblyStateCreateInfo input_assembly = {
      VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO};
  input_assembly.topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
  VkPipelineViewportStateCreateInfo viewport = {
      VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO};
  viewport.viewportCount = 1;
  viewport.scissorCount = 1;
  VkPipelineRasterizationStateCreateInfo raster = {
      VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO};
  raster.polygonMode = VK_POLYGON_MODE_FILL;
  raster.lineWidth = 1.0f;
  VkPipelineMultisampleStateCreateInfo multisample = {
      VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO};
  multisample.rasterizationSamples = VK_SAMPLE_COUNT_1_BIT;
  VkPipelineDepthStencilStateCreateInfo depth_stencil = {
      VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO};
  depth_stencil.depthTestEnable = VK_TRUE;
  depth_stencil.depthWriteEnable = VK_TRUE;
  depth_stencil.depthCompareOp = VK_COMPARE_OP_LESS_OR_EQUAL;
  uint32_t color_count = 0;
  for (uint32_t i = 0; i < 8; ++i) {
    if (reflection.color_outputs & (1u << i)) color_count = i + 1;
  }
  std::vector<VkPipelineColorBlendAttachmentState> blend(color_count);
  std::vector<VkFormat> color_formats(color_count,
                                      VK_FORMAT_R16G16B16A16_SFLOAT);
  for (auto& b : blend) {
    b = {};
    b.colorWriteMask = 0xF;
  }
  VkPipelineColorBlendStateCreateInfo color_blend = {
      VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO};
  color_blend.attachmentCount = color_count;
  color_blend.pAttachments = blend.data();
  VkDynamicState dynamic_states[] = {VK_DYNAMIC_STATE_VIEWPORT,
                                     VK_DYNAMIC_STATE_SCISSOR};
  VkPipelineDynamicStateCreateInfo dynamic = {
      VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO};
  dynamic.dynamicStateCount = 2;
  dynamic.pDynamicStates = dynamic_states;
  VkPipelineRenderingCreateInfo rendering = {
      VK_STRUCTURE_TYPE_PIPELINE_RENDERING_CREATE_INFO};
  rendering.colorAttachmentCount = color_count;
  rendering.pColorAttachmentFormats = color_formats.data();
  rendering.depthAttachmentFormat = VK_FORMAT_D24_UNORM_S8_UINT;
  rendering.stencilAttachmentFormat = VK_FORMAT_D24_UNORM_S8_UINT;
  VkGraphicsPipelineCreateInfo info = {
      VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO};
  info.pNext = &rendering;
  info.flags = VK_PIPELINE_CREATE_CAPTURE_STATISTICS_BIT_KHR |
               (getenv("IR3STATS_DUMP") ? VK_PIPELINE_CREATE_CAPTURE_INTERNAL_REPRESENTATIONS_BIT_KHR : 0);
  info.stageCount = 2;
  info.pStages = stages;
  info.pVertexInputState = &vertex_input;
  info.pInputAssemblyState = &input_assembly;
  info.pViewportState = &viewport;
  info.pRasterizationState = &raster;
  info.pMultisampleState = &multisample;
  info.pDepthStencilState = &depth_stencil;
  info.pColorBlendState = &color_blend;
  info.pDynamicState = &dynamic;
  info.layout = layout;
  VkPipeline pipeline;
  CHECK(vkCreateGraphicsPipelines(c.device, VK_NULL_HANDLE, 1, &info, nullptr,
                                  &pipeline));
  VkPipelineInfoKHR pipeline_info = {VK_STRUCTURE_TYPE_PIPELINE_INFO_KHR};
  pipeline_info.pipeline = pipeline;
  uint32_t executable_count = 0;
  CHECK(c.get_properties(c.device, &pipeline_info, &executable_count, nullptr));
  std::vector<VkPipelineExecutablePropertiesKHR> executables(
      executable_count, {VK_STRUCTURE_TYPE_PIPELINE_EXECUTABLE_PROPERTIES_KHR});
  CHECK(c.get_properties(c.device, &pipeline_info, &executable_count,
                         executables.data()));
  for (uint32_t e = 0; e < executable_count; ++e) {
    VkPipelineExecutableInfoKHR executable_info = {
        VK_STRUCTURE_TYPE_PIPELINE_EXECUTABLE_INFO_KHR};
    executable_info.pipeline = pipeline;
    executable_info.executableIndex = e;
    uint32_t count = 0;
    CHECK(c.get_statistics(c.device, &executable_info, &count, nullptr));
    std::vector<VkPipelineExecutableStatisticKHR> statistics(
        count, {VK_STRUCTURE_TYPE_PIPELINE_EXECUTABLE_STATISTIC_KHR});
    CHECK(c.get_statistics(c.device, &executable_info, &count,
                           statistics.data()));
    if (getenv("IR3STATS_DUMP")) {
      auto get_ir = PFN_vkGetPipelineExecutableInternalRepresentationsKHR(
          vkGetDeviceProcAddr(c.device, "vkGetPipelineExecutableInternalRepresentationsKHR"));
      uint32_t ir_count = 0;
      get_ir(c.device, &executable_info, &ir_count, nullptr);
      std::vector<VkPipelineExecutableInternalRepresentationKHR> irs(
          ir_count, {VK_STRUCTURE_TYPE_PIPELINE_EXECUTABLE_INTERNAL_REPRESENTATION_KHR});
      get_ir(c.device, &executable_info, &ir_count, irs.data());
      std::vector<std::vector<char>> data(ir_count);
      for (uint32_t j = 0; j < ir_count; ++j) {
        data[j].resize(irs[j].dataSize + 1, 0);
        irs[j].pData = data[j].data();
      }
      get_ir(c.device, &executable_info, &ir_count, irs.data());
      std::string path = std::string(getenv("IR3STATS_DUMP")) + "/" + name + "." + executables[e].name + ".txt";
      FILE* f = std::fopen(path.c_str(), "w");
      for (uint32_t j = 0; j < ir_count; ++j) {
        std::fprintf(f, "==== %s\n%s\n", irs[j].name, data[j].data());
      }
      std::fclose(f);
    }
    for (const auto& s : statistics) {
      double value = 0;
      switch (s.format) {
        case VK_PIPELINE_EXECUTABLE_STATISTIC_FORMAT_BOOL32_KHR:
          value = s.value.b32;
          break;
        case VK_PIPELINE_EXECUTABLE_STATISTIC_FORMAT_INT64_KHR:
          value = double(s.value.i64);
          break;
        case VK_PIPELINE_EXECUTABLE_STATISTIC_FORMAT_UINT64_KHR:
          value = double(s.value.u64);
          break;
        case VK_PIPELINE_EXECUTABLE_STATISTIC_FORMAT_FLOAT64_KHR:
          value = s.value.f64;
          break;
        default:
          break;
      }
      std::printf("%s,%s,%s,%g\n", name, executables[e].name, s.name, value);
    }
  }
  vkDestroyPipeline(c.device, pipeline, nullptr);
  vkDestroyShaderModule(c.device, modules[0], nullptr);
  vkDestroyShaderModule(c.device, modules[1], nullptr);
  vkDestroyPipelineLayout(c.device, layout, nullptr);
  for (auto l : set_layouts) vkDestroyDescriptorSetLayout(c.device, l, nullptr);
  return true;
}

bool Init(Context& c) {
  VkApplicationInfo app = {VK_STRUCTURE_TYPE_APPLICATION_INFO};
  app.apiVersion = VK_API_VERSION_1_3;
  VkInstanceCreateInfo instance_info = {
      VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};
  instance_info.pApplicationInfo = &app;
  CHECK(vkCreateInstance(&instance_info, nullptr, &c.instance));
  uint32_t count = 1;
  VkResult r = vkEnumeratePhysicalDevices(c.instance, &count,
                                          &c.physical_device);
  if ((r != VK_SUCCESS && r != VK_INCOMPLETE) || !count) {
    std::fprintf(stderr, "no physical device\n");
    return false;
  }
  VkPhysicalDeviceProperties properties;
  vkGetPhysicalDeviceProperties(c.physical_device, &properties);
  std::fprintf(stderr, "device: %s\n", properties.deviceName);
  // Enable every supported core and extension feature the translated
  // shaders may need.
  VkPhysicalDeviceFragmentShaderBarycentricFeaturesKHR barycentric = {
      VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FRAGMENT_SHADER_BARYCENTRIC_FEATURES_KHR};
  VkPhysicalDevicePipelineExecutablePropertiesFeaturesKHR executable = {
      VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PIPELINE_EXECUTABLE_PROPERTIES_FEATURES_KHR};
  executable.pNext = &barycentric;
  VkPhysicalDeviceVulkan13Features features13 = {
      VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_3_FEATURES};
  features13.pNext = &executable;
  VkPhysicalDeviceVulkan12Features features12 = {
      VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES};
  features12.pNext = &features13;
  VkPhysicalDeviceVulkan11Features features11 = {
      VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_1_FEATURES};
  features11.pNext = &features12;
  VkPhysicalDeviceFeatures2 features = {
      VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2};
  features.pNext = &features11;
  vkGetPhysicalDeviceFeatures2(c.physical_device, &features);
  std::fprintf(stderr, "barycentric=%u executable_info=%u\n",
               barycentric.fragmentShaderBarycentric,
               executable.pipelineExecutableInfo);
  const char* extensions[] = {
      VK_KHR_PIPELINE_EXECUTABLE_PROPERTIES_EXTENSION_NAME,
      VK_KHR_FRAGMENT_SHADER_BARYCENTRIC_EXTENSION_NAME};
  float priority = 1.0f;
  VkDeviceQueueCreateInfo queue = {VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO};
  queue.queueCount = 1;
  queue.pQueuePriorities = &priority;
  VkDeviceCreateInfo device_info = {VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO};
  device_info.pNext = &features;
  device_info.queueCreateInfoCount = 1;
  device_info.pQueueCreateInfos = &queue;
  device_info.enabledExtensionCount = barycentric.fragmentShaderBarycentric ? 2 : 1;
  device_info.ppEnabledExtensionNames = extensions;
  CHECK(vkCreateDevice(c.physical_device, &device_info, nullptr, &c.device));
  c.get_properties = PFN_vkGetPipelineExecutablePropertiesKHR(
      vkGetDeviceProcAddr(c.device, "vkGetPipelineExecutablePropertiesKHR"));
  c.get_statistics = PFN_vkGetPipelineExecutableStatisticsKHR(
      vkGetDeviceProcAddr(c.device, "vkGetPipelineExecutableStatisticsKHR"));
  return c.get_properties && c.get_statistics;
}

}  // namespace

int main(int argc, char** argv) {
  if (argc < 4) {
    std::fprintf(stderr,
                 "Usage: %s <partner.vert.spv> <partner.frag.spv> <file.spv>...\n",
                 argv[0]);
    return 2;
  }
  Context c;
  if (!Init(c)) return 1;
  std::vector<uint32_t> partner_vs = ReadSpirv(argv[1]);
  std::vector<uint32_t> partner_fs = ReadSpirv(argv[2]);
  int failures = 0;
  for (int i = 3; i < argc; ++i) {
    std::vector<uint32_t> code = ReadSpirv(argv[i]);
    std::string name = argv[i];
    std::string base = name.substr(name.rfind('/') + 1);
    bool is_pixel = base.rfind("ps_", 0) == 0;
    Reflection reflection;
    if (code.empty() || !Reflect(code, reflection)) {
      std::fprintf(stderr, "bad spirv %s\n", argv[i]);
      ++failures;
      continue;
    }
    if (!is_pixel) {
      // The partner fragment shader writes location 0 only.
      reflection.color_outputs = 1;
    }
    if (!Compile(c, base.c_str(), is_pixel ? partner_vs : code,
                 is_pixel ? code : partner_fs, reflection)) {
      std::fprintf(stderr, "compile failed %s\n", argv[i]);
      ++failures;
    }
    std::fflush(stdout);
  }
  std::fprintf(stderr, "done, %d failures\n", failures);
  return failures ? 1 : 0;
}
