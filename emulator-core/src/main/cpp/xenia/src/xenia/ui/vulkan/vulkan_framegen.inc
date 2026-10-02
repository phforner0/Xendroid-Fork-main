// XenDroid host-side Win-FG integration. Included in namespace xe::ui::vulkan.
// Off-by-default. Only the scheduled presenter thread records interpolation;
// producer threads publish notifications and never sleep for synthetic frames.
static int64_t FrameGenerationNowNs() {
  return std::chrono::duration_cast<std::chrono::nanoseconds>(
      std::chrono::steady_clock::now().time_since_epoch()).count();
}

static VulkanDevice* winfg_native_device = nullptr;
static VkResult VKAPI_CALL WinfgNativeWaitIdle(VkDevice device) {
  if (!winfg_native_device) return VK_ERROR_DEVICE_LOST;
  std::vector<std::unique_ptr<VulkanDevice::Queue::Acquisition>> locks;
  for (const auto& family : winfg_native_device->queue_families()) {
    for (const auto& queue : family.queues) locks.push_back(std::make_unique<VulkanDevice::Queue::Acquisition>(*queue));
  }
  return winfg_native_device->functions().vkDeviceWaitIdle(device);
}

struct VulkanPresenter::FrameGenContext {
  winfg::DeviceDispatch dd;
  winfg::InstanceDispatch id;
  winfg::FrameGen engine;
  std::unique_ptr<lsfg::Engine> lsfg_engine;
  int kind = 0;
  uint64_t source_frames = 0;
  uint64_t epoch = 0;
  uint32_t planned_generations = 0;
  std::chrono::steady_clock::time_point last_input;
  std::shared_ptr<GuestOutputImage> previous, current, generated;
  GuestOutputProperties properties;
  bool have_previous = false;
  bool current_initialized = false;
  bool previous_initialized = false;
  bool generated_initialized = false;
  bool query_pending = false;
  VkQueryPool timestamps = VK_NULL_HANDLE;
  uint32_t timestamp_valid_bits = 0;  // of the graphics/compute queue family
  const VulkanDevice* device;
  int preset = -1;
  std::vector<std::shared_ptr<GuestOutputImage>> source_references;

  explicit FrameGenContext(VulkanDevice* d) : device(d) {
    const auto& f = d->functions();
#define WFG_BIND(name) dd.name = f.vk##name
    WFG_BIND(CreateImage); WFG_BIND(DestroyImage); WFG_BIND(CreateImageView); WFG_BIND(DestroyImageView);
    WFG_BIND(AllocateMemory); WFG_BIND(FreeMemory); WFG_BIND(BindImageMemory); WFG_BIND(GetImageMemoryRequirements);
    WFG_BIND(CreateBuffer); WFG_BIND(DestroyBuffer); WFG_BIND(GetBufferMemoryRequirements); WFG_BIND(BindBufferMemory);
    WFG_BIND(MapMemory); WFG_BIND(UnmapMemory); WFG_BIND(CreateSampler); WFG_BIND(DestroySampler);
    WFG_BIND(CreateShaderModule); WFG_BIND(DestroyShaderModule); WFG_BIND(CreateDescriptorSetLayout); WFG_BIND(DestroyDescriptorSetLayout);
    WFG_BIND(CreatePipelineLayout); WFG_BIND(DestroyPipelineLayout); WFG_BIND(CreateComputePipelines); WFG_BIND(DestroyPipeline);
    WFG_BIND(CreateDescriptorPool); WFG_BIND(DestroyDescriptorPool); WFG_BIND(ResetDescriptorPool); WFG_BIND(AllocateDescriptorSets); WFG_BIND(UpdateDescriptorSets);
    WFG_BIND(CmdBindPipeline); WFG_BIND(CmdBindDescriptorSets); WFG_BIND(CmdDispatch); WFG_BIND(CmdPipelineBarrier);
    WFG_BIND(CmdCopyImage); WFG_BIND(CmdBlitImage); WFG_BIND(CmdClearColorImage);
#undef WFG_BIND
    winfg_native_device = d;
    dd.DeviceWaitIdle = &WinfgNativeWaitIdle;
    id.GetPhysicalDeviceMemoryProperties = d->vulkan_instance()->functions().vkGetPhysicalDeviceMemoryProperties;
  }
  ~FrameGenContext() {
    engine.destroy();
    // destroy() above is the last user of the wait-idle hook for this device.
    if (winfg_native_device == device) winfg_native_device = nullptr;
    if (timestamps) device->functions().vkDestroyQueryPool(device->device(), timestamps, nullptr);
  }
};

bool VulkanPresenter::ScheduleGuestOutput() {
  if (!RuntimePresentation().frame_generation_requested.load(std::memory_order_relaxed)) return false;
  if (!CanScheduleGuestOutput()) {
    RuntimePresentation().frame_generation_state = int(FrameGenerationState::kUnsupported);
    RuntimePresentation().frame_generation_error = 9;
    return false;
  }
  const int64_t now = FrameGenerationNowNs();
  {
    std::lock_guard<std::mutex> lock(fg_mutex_);
    if (!fg_thread_.joinable()) fg_thread_ = std::thread(&VulkanPresenter::FrameGenerationThread, this);
    ++fg_notification_;
    fg_arrival_ns_ = now;
  }
  fg_condition_.notify_one();
  return true;
}

// The timing decisions live in FrameGenerationSchedule (presentation_runtime.h,
// covered by the host tests); this loop only waits and paints. Lock order:
// fg_mutex_ is never held while painting, and painting takes paint_mode_mutex_
// (PaintGuestFromScheduledThread) - so the producer (ScheduleGuestOutput, which
// takes only fg_mutex_) can never wait on a paint.
void VulkanPresenter::FrameGenerationThread() {
  FrameGenerationSchedule schedule;
  auto& runtime = RuntimePresentation();
  std::unique_lock<std::mutex> lock(fg_mutex_);
  while (!fg_shutdown_) {
    fg_condition_.wait(lock, [&] { return fg_shutdown_ || schedule.HasPending(fg_notification_); });
    if (fg_shutdown_) break;
    const int engine_multiplier = runtime.frame_generation_engine.load() == 1
        ? runtime.frame_generation_multiplier.load() : 2;
    const FrameGenerationSchedule::Cycle cycle = schedule.Begin(
        fg_notification_, fg_arrival_ns_, runtime.configuration_epoch.load(), engine_multiplier,
        runtime.display_hz.load(), FrameGenerationNowNs());
    if (cycle.dropped) runtime.dropped_guest_notifications.fetch_add(cycle.dropped);
    if (cycle.stop) {
      runtime.frame_generation_error = 5;
      runtime.frame_generation_state = int(FrameGenerationState::kFailed);
      runtime.frame_generation_requested = false;
    }
    lock.unlock();
    bool shutdown = false;
    for (int index = 1; index < cycle.multiplier && !shutdown; ++index) {
      if (runtime.frame_generation_requested.load()) {
        if (FrameGenerationSchedule::LateSynthetic(cycle, index, FrameGenerationNowNs())) {
          runtime.late_synthetic_skips.fetch_add(1, std::memory_order_relaxed);
        } else {
          PaintGuestFromScheduledThread(index);
        }
      }
      lock.lock();
      // At most one cycle is in flight; a newer notification waits for the next
      // cycle and replaces any older pending one.
      fg_condition_.wait_until(
          lock,
          std::chrono::steady_clock::time_point(
              std::chrono::duration_cast<std::chrono::steady_clock::duration>(
                  std::chrono::nanoseconds(FrameGenerationSchedule::Deadline(cycle, index + 1)))),
          [&] { return fg_shutdown_; });
      shutdown = fg_shutdown_;
      lock.unlock();
    }
    if (shutdown) break;
    PaintGuestFromScheduledThread(0);
    lock.lock();
  }
}

void VulkanPresenter::StopFrameGenerationThread() {
  { std::lock_guard<std::mutex> lock(fg_mutex_); fg_shutdown_ = true; }
  fg_condition_.notify_all();
  if (fg_thread_.joinable()) fg_thread_.join();
}

void VulkanPresenter::ResetFrameGeneration() {
  if (!fg_context_) return;
  paint_context_.completion_timeline.AwaitAllSubmissions();
  fg_context_.reset();
}

bool VulkanPresenter::PrepareGeneratedFrame(VkCommandBuffer cmd,
    std::shared_ptr<GuestOutputImage>& source, GuestOutputProperties& properties) {
  auto& runtime = RuntimePresentation();
  const auto& f = vulkan_device_->functions();
  const auto& ifn = vulkan_device_->vulkan_instance()->functions();
  const auto device = vulkan_device_->device();
  // The upstream engine's descriptor scratch is single-flight. Preserve that
  // contract rather than updating a UBO/descriptor still being read by the GPU.
  if (!paint_context_.completion_timeline.AwaitAllSubmissions()) return false;
  if (fg_context_ && (fg_context_->kind != runtime.frame_generation_engine.load() ||
      fg_context_->epoch != runtime.configuration_epoch.load())) ResetFrameGeneration();
  const auto now = std::chrono::steady_clock::now();
  if (fg_context_ && fg_generated_phase_ && fg_context_->last_input.time_since_epoch().count() &&
      now - fg_context_->last_input > std::chrono::milliseconds(250)) ResetFrameGeneration();
  if (fg_context_) fg_context_->source_references.clear();
  if (!fg_context_) {
    VkFormatProperties source_format, rgba_format, float_format, flow_format;
    ifn.vkGetPhysicalDeviceFormatProperties(vulkan_device_->physical_device(), kGuestOutputFormat, &source_format);
    ifn.vkGetPhysicalDeviceFormatProperties(vulkan_device_->physical_device(), VK_FORMAT_R8G8B8A8_UNORM, &rgba_format);
    ifn.vkGetPhysicalDeviceFormatProperties(vulkan_device_->physical_device(), VK_FORMAT_R32_SFLOAT, &float_format);
    ifn.vkGetPhysicalDeviceFormatProperties(vulkan_device_->physical_device(), VK_FORMAT_R16G16B16A16_SFLOAT, &flow_format);
    if (!(source_format.optimalTilingFeatures & VK_FORMAT_FEATURE_BLIT_SRC_BIT) ||
        !(rgba_format.optimalTilingFeatures & VK_FORMAT_FEATURE_BLIT_DST_BIT) ||
        !(rgba_format.optimalTilingFeatures & VK_FORMAT_FEATURE_STORAGE_IMAGE_BIT) ||
        !(float_format.optimalTilingFeatures & VK_FORMAT_FEATURE_STORAGE_IMAGE_BIT) ||
        !(flow_format.optimalTilingFeatures & VK_FORMAT_FEATURE_STORAGE_IMAGE_BIT)) {
      runtime.frame_generation_error = 1; return false;
    }
    fg_context_ = std::make_shared<FrameGenContext>(vulkan_device_);
    fg_context_->kind = runtime.frame_generation_engine.load();
    fg_context_->epoch = runtime.configuration_epoch.load();
    auto queue = vulkan_device_->AcquireQueue(vulkan_device_->queue_family_graphics_compute(), 0);
    if (fg_context_->kind == 0 && !fg_context_->engine.init(&fg_context_->dd, &fg_context_->id, vulkan_device_->physical_device(), device,
        vulkan_device_->queue_family_graphics_compute(), queue.queue())) {
      runtime.frame_generation_error = 2; return false;
    }
    if (fg_context_->kind == 1) {
      if (!vulkan_device_->properties().nullDescriptor || !vulkan_device_->properties().vulkanMemoryModel) {
        runtime.frame_generation_error = 8; return false;
      }
      LsfgVkDispatch lsfg_dispatch;
#define LSFG_COPY(name) lsfg_dispatch.name = f.vk##name
      LSFG_COPY(AllocateDescriptorSets); LSFG_COPY(AllocateMemory); LSFG_COPY(BindBufferMemory); LSFG_COPY(BindImageMemory);
      LSFG_COPY(CmdBindDescriptorSets); LSFG_COPY(CmdBindPipeline); LSFG_COPY(CmdCopyImage); LSFG_COPY(CmdDispatch); LSFG_COPY(CmdPipelineBarrier);
      LSFG_COPY(CreateBuffer); LSFG_COPY(CreateComputePipelines); LSFG_COPY(CreateDescriptorPool); LSFG_COPY(CreateDescriptorSetLayout);
      LSFG_COPY(CreateImage); LSFG_COPY(CreateImageView); LSFG_COPY(CreatePipelineLayout); LSFG_COPY(CreateSampler); LSFG_COPY(CreateShaderModule);
      LSFG_COPY(DestroyBuffer); LSFG_COPY(DestroyDescriptorPool); LSFG_COPY(DestroyDescriptorSetLayout); LSFG_COPY(DestroyImage); LSFG_COPY(DestroyImageView);
      LSFG_COPY(DestroyPipeline); LSFG_COPY(DestroyPipelineLayout); LSFG_COPY(DestroySampler); LSFG_COPY(DestroyShaderModule); LSFG_COPY(FreeMemory);
      LSFG_COPY(GetBufferMemoryRequirements); LSFG_COPY(GetImageMemoryRequirements); LSFG_COPY(MapMemory); LSFG_COPY(UnmapMemory); LSFG_COPY(UpdateDescriptorSets);
#undef LSFG_COPY
      lsfg_dispatch.GetPhysicalDeviceMemoryProperties = ifn.vkGetPhysicalDeviceMemoryProperties;
      std::string cache;
      { std::lock_guard<std::mutex> configuration(runtime.configuration_mutex); cache = runtime.lsfg_cache; }
      fg_context_->lsfg_engine = std::make_unique<lsfg::Engine>();
      const uint32_t target = vulkan_device_->properties().apiVersion >= VK_API_VERSION_1_3 ? lsfg::kSpirv16 : lsfg::kSpirv15;
      if (!lsfgVkdInit(lsfg_dispatch) || !fg_context_->lsfg_engine->init(device, vulkan_device_->physical_device(), cache, target)) {
        runtime.frame_generation_error = 7; return false;
      }
      fg_context_->lsfg_engine->configure(runtime.frame_generation_multiplier.load(), 0, 1.0f, runtime.display_hz.load());
    }
    uint32_t families = 0;
    ifn.vkGetPhysicalDeviceQueueFamilyProperties(vulkan_device_->physical_device(), &families, nullptr);
    std::vector<VkQueueFamilyProperties> queues(families);
    ifn.vkGetPhysicalDeviceQueueFamilyProperties(vulkan_device_->physical_device(), &families, queues.data());
    const uint32_t valid_bits = queues[vulkan_device_->queue_family_graphics_compute()].timestampValidBits;
    if (valid_bits && vulkan_device_->properties().timestampPeriod > 0) {
      VkQueryPoolCreateInfo info{VK_STRUCTURE_TYPE_QUERY_POOL_CREATE_INFO};
      info.queryType = VK_QUERY_TYPE_TIMESTAMP; info.queryCount = 2;
      if (f.vkCreateQueryPool(device, &info, nullptr, &fg_context_->timestamps) == VK_SUCCESS) {
        fg_context_->timestamp_valid_bits = valid_bits;
      } else {
        fg_context_->timestamps = VK_NULL_HANDLE;
      }
    }
  }
  auto& context = *fg_context_;
  // F08: the previous pass's GPU time (its submission completed above). Masked to the
  // queue's valid bits and wrap-safe; a failed read is "unavailable", never the old value.
  if (context.query_pending && context.timestamps) {
    uint64_t timestamps[2];
    const VkResult read = f.vkGetQueryPoolResults(device, context.timestamps, 0, 2, sizeof(timestamps), timestamps,
        sizeof(uint64_t), VK_QUERY_RESULT_64_BIT);
    RecordGenerationGpu(runtime, read == VK_SUCCESS
        ? TimestampElapsedNs(timestamps[0], timestamps[1], context.timestamp_valid_bits,
                             vulkan_device_->properties().timestampPeriod)
        : -1);
    context.query_pending = false;
  }
  if (!fg_generated_phase_ && context.current && context.current_initialized) {
    source = context.current; properties = context.properties;
    return true;
  }
  if (fg_generation_index_ > 1 && context.kind == 1 && context.current_initialized) {
    properties = context.properties;
    source = context.current;
    if (uint32_t(fg_generation_index_) > context.planned_generations) return true;
    VkImageMemoryBarrier before{VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
    before.image = context.generated->image(); before.oldLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
    before.newLayout = VK_IMAGE_LAYOUT_GENERAL; before.srcAccessMask = VK_ACCESS_SHADER_READ_BIT;
    before.dstAccessMask = VK_ACCESS_SHADER_WRITE_BIT; before.srcQueueFamilyIndex = before.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    before.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
    f.vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, 0, nullptr, 0, nullptr, 1, &before);
    context.lsfg_engine->generateInto(cmd, fg_generation_index_ - 1, 0, context.generated->image(), context.generated->view(),
        context.current->extent().width, context.current->extent().height);
    std::swap(before.oldLayout, before.newLayout); std::swap(before.srcAccessMask, before.dstAccessMask);
    f.vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, 0, nullptr, 0, nullptr, 1, &before);
    source = context.generated; fg_actual_synthetic_ = true;
    return true;
  }
  const auto extent = source->extent();
  context.last_input = now;
  if (!context.current || context.current->extent().width != extent.width || context.current->extent().height != extent.height) {
    context.previous = GuestOutputImage::Create(vulkan_device_, extent.width, extent.height, VK_FORMAT_R8G8B8A8_UNORM,
        VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_STORAGE_BIT);
    context.current = GuestOutputImage::Create(vulkan_device_, extent.width, extent.height, VK_FORMAT_R8G8B8A8_UNORM,
        VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_STORAGE_BIT);
    context.generated = GuestOutputImage::Create(vulkan_device_, extent.width, extent.height, VK_FORMAT_R8G8B8A8_UNORM,
        VK_IMAGE_USAGE_STORAGE_BIT);
    context.have_previous = false; context.current_initialized = false; context.previous_initialized = false; context.generated_initialized = false;
    if (context.lsfg_engine) context.lsfg_engine->forgetTargets();
    if (!context.current || !context.previous || !context.generated) { runtime.frame_generation_error = 3; return false; }
    if (context.kind == 0 && !context.engine.onResize(extent, VK_FORMAT_R8G8B8A8_UNORM)) { runtime.frame_generation_error = 3; return false; }
    if (context.kind == 1 && !context.lsfg_engine->prepare(extent.width, extent.height, VK_FORMAT_R8G8B8A8_UNORM)) {
      runtime.frame_generation_error = 3; return false;
    }
  }
  if (context.current_initialized) {
    std::swap(context.current, context.previous);
    std::swap(context.current_initialized, context.previous_initialized);
    context.have_previous = context.previous_initialized;
  }
  context.properties = properties;
  auto barrier = [&](VkImage image, VkImageLayout from, VkImageLayout to, VkAccessFlags src, VkAccessFlags dst) {
    VkImageMemoryBarrier b{VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
    b.oldLayout = from; b.newLayout = to; b.srcAccessMask = src; b.dstAccessMask = dst;
    b.srcQueueFamilyIndex = b.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED; b.image = image;
    b.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
    f.vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
        0, 0, nullptr, 0, nullptr, 1, &b);
  };
  barrier(source->image(), kGuestOutputInternalLayout, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
      VK_ACCESS_MEMORY_WRITE_BIT | VK_ACCESS_SHADER_READ_BIT, VK_ACCESS_TRANSFER_READ_BIT);
  barrier(context.current->image(), context.current_initialized ? VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL : VK_IMAGE_LAYOUT_UNDEFINED,
      VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_ACCESS_SHADER_READ_BIT, VK_ACCESS_TRANSFER_WRITE_BIT);
  VkImageBlit blit{};
  blit.srcSubresource = blit.dstSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
  blit.srcOffsets[1] = blit.dstOffsets[1] = {int32_t(extent.width), int32_t(extent.height), 1};
  f.vkCmdBlitImage(cmd, source->image(), VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, context.current->image(),
      VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &blit, VK_FILTER_NEAREST);
  barrier(source->image(), VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, kGuestOutputInternalLayout, VK_ACCESS_TRANSFER_READ_BIT, VK_ACCESS_SHADER_READ_BIT);
  barrier(context.current->image(), VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_ACCESS_TRANSFER_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT);
  context.current_initialized = true;
  context.source_references.push_back(source);
  source = context.current;
  if (context.kind == 1) {
    const auto generations = context.lsfg_engine->plan(runtime.frame_generation_multiplier.load() - 1, ++context.source_frames);
    context.planned_generations = generations;
    // Timed: this source frame's processing plus its first generated frame. A warm-up
    // pass (no generation yet) is not a generation pass and stays out of the figures.
    if (context.timestamps && generations) {
      f.vkCmdResetQueryPool(cmd, context.timestamps, 0, 2);
      f.vkCmdWriteTimestamp(cmd, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, context.timestamps, 0);
    }
    barrier(context.current->image(), VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_IMAGE_LAYOUT_GENERAL,
        VK_ACCESS_TRANSFER_WRITE_BIT | VK_ACCESS_SHADER_READ_BIT, VK_ACCESS_MEMORY_READ_BIT);
    context.lsfg_engine->process(cmd, context.current->image(), extent.width, extent.height, generations);
    barrier(context.current->image(), VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
        VK_ACCESS_MEMORY_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT);
    if (!generations) { runtime.frame_generation_state = int(FrameGenerationState::kWarmingUp); return true; }
    barrier(context.generated->image(), context.generated_initialized ? VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL : VK_IMAGE_LAYOUT_UNDEFINED,
        VK_IMAGE_LAYOUT_GENERAL, VK_ACCESS_SHADER_READ_BIT, VK_ACCESS_SHADER_WRITE_BIT);
    context.lsfg_engine->generateInto(cmd, 0, 0, context.generated->image(), context.generated->view(), extent.width, extent.height);
    if (context.timestamps) {
      f.vkCmdWriteTimestamp(cmd, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, context.timestamps, 1);
      context.query_pending = true;
    }
    barrier(context.generated->image(), VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_ACCESS_SHADER_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT);
    context.generated_initialized = true; source = context.generated;
    fg_actual_synthetic_ = true;
    runtime.frame_generation_state = int(FrameGenerationState::kRunning);
    return true;
  }
  if (!context.have_previous) {
    runtime.frame_generation_state = int(FrameGenerationState::kWarmingUp);
    return true;
  }
  const int preset = runtime.frame_generation_preset.load();
  if (context.preset != preset) {
    winfg::Config cfg; cfg.enabled = true; cfg.model = 3; cfg.multiplier = 2; cfg.perfPreset = preset;
    context.engine.configure(cfg); context.preset = preset;
  }
  barrier(context.generated->image(), context.generated_initialized ? VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL : VK_IMAGE_LAYOUT_UNDEFINED,
      VK_IMAGE_LAYOUT_GENERAL, VK_ACCESS_SHADER_READ_BIT, VK_ACCESS_SHADER_WRITE_BIT);
  if (context.timestamps) {
    f.vkCmdResetQueryPool(cmd, context.timestamps, 0, 2);
    f.vkCmdWriteTimestamp(cmd, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, context.timestamps, 0);
  }
  if (!context.engine.record(cmd, context.previous->view(), context.current->view(), context.generated->view(), 0.5f, 0)) {
    runtime.frame_generation_error = 6; return false;
  }
  if (context.timestamps) {
    f.vkCmdWriteTimestamp(cmd, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, context.timestamps, 1);
    context.query_pending = true;
  }
  barrier(context.generated->image(), VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
      VK_ACCESS_SHADER_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT);
  context.generated_initialized = true;
  source = context.generated;
  fg_actual_synthetic_ = true;
  runtime.frame_generation_state = int(FrameGenerationState::kRunning);
  return true;
}
