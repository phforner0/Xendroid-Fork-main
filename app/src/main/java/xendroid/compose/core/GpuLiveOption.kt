package xendroid.compose.core

/**
 * GPU options the in-game menu changes while a title runs, in the core's order
 * (xenia/gpu/gpu_live_options.h): each is the cvar under [key] ("Section|name"), which is also
 * what the menu keeps for the game, and [default] is the core's default (switches as 1 and 0).
 */
enum class GpuLiveOption(val key: String, val default: Int) {
    /** vulkan_async_skip_draws: draws wait for no shader (brief pop-in) instead of stalling. */
    ASYNC_SKIP_DRAWS("Vulkan|vulkan_async_skip_draws", 1),
    /** msaa_4x_as_2x: 4x MSAA stored with 2 samples. */
    MSAA_4X_AS_2X("GPU|msaa_4x_as_2x", 0),
    /** alpha_to_coverage_as_alpha_test: cut-out edges instead of blended ones. */
    ALPHA_TO_COVERAGE_AS_TEST("GPU|alpha_to_coverage_as_alpha_test", 0),
    /** vulkan_shading_rate: 0 per pixel, 1 per 2x1, 2 per 1x2, 3 per 2x2 pixels. */
    SHADING_RATE("GPU|vulkan_shading_rate", 0),
}
