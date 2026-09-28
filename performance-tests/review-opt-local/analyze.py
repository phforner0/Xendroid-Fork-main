"""Summarize the captured opt-local session without changing the phone."""
import json
from collections import Counter
from pathlib import Path
import re
import struct


root = Path(__file__).resolve().parent
text = (root / "previous-session/xe.log").read_text()
cpu_pattern = re.compile(
    r"f:(\d+).*GpuFrame: (\d+) frames, interval avg=([\d.]+)ms max=([\d.]+)ms"
    r".*exec=([\d.]+)ms draws=(\d+) draw=([\d.]+)ms swap=([\d.]+)ms stall=([\d.]+)ms")
gpu_pattern = re.compile(
    r"f:(\d+).*VkFrameSync: (\d+) frames.*submissions=([\d.]+) resolves=([\d.]+)"
    r".*gpu exec avg=([\d.]+)ms max=([\d.]+)ms gap avg=([\d.]+)ms"
    r".*draws=(\d+) rp_begins=(\d+) splits=([\d.]+) replay=([\d.]+)ms"
    r".*resolve_clears=([\d.]+) in_guest_pass=([\d.]+)")
cpu = [list(map(float, m.groups())) for m in cpu_pattern.finditer(text)]
gpu = [list(map(float, m.groups())) for m in gpu_pattern.finditer(text)]


def weighted(rows, index):
    return sum(r[1] * r[index] for r in rows) / sum(r[1] for r in rows)


def summarize(start, end):
    c = [r for r in cpu if start <= r[0] <= end]
    g = [r for r in gpu if start <= r[0] <= end]
    result = {"frame_range": [start, end], "cpu_samples": len(c), "gpu_samples": len(g)}
    if c:
        result.update({
            "logged_frames": int(sum(r[1] for r in c)),
            "logged_duration_s": sum(r[1] * r[2] for r in c) / 1000,
            "guest_fps": 1000 / weighted(c, 2),
            "sample_fps_min": min(1000 / r[2] for r in c),
            "sample_fps_max": max(1000 / r[2] for r in c),
            "worst_logged_frame_ms": max(r[3] for r in c),
            "command_thread_exec_ms": weighted(c, 4),
            "draws_per_frame": weighted(c, 5),
            "draw_processing_ms": weighted(c, 6),
            "swap_ms": weighted(c, 7),
            "ringbuffer_idle_ms": weighted(c, 8),
        })
    if g:
        result.update({
            "submissions_per_frame": weighted(g, 2),
            "resolves_per_frame": weighted(g, 3),
            "gpu_exec_ms_per_submission": weighted(g, 4),
            "gpu_gap_ms_per_submission": weighted(g, 6),
            "render_pass_begins_per_frame": weighted(g, 8),
            "replay_and_submit_cpu_ms": weighted(g, 10),
            "resolve_clears_per_frame": weighted(g, 11),
            "clears_in_guest_pass_per_frame": weighted(g, 12),
            "clear_fast_path_percent": 100 * weighted(g, 12) / weighted(g, 11),
        })
    return result


report = {
    "source": "previous-session/xe.log",
    "cpu_samples_total": len(cpu), "gpu_samples_total": len(gpu),
    "windows": {
        "heavy_scene_4800_to_end": summarize(4800, 6827),
        "final_heavy_scene_6400_to_end": summarize(6400, 6827),
    },
    "notes": [
        "Frame ranges exclude the earlier light intro and the final pause.",
        "CPU/GPU reports are asynchronous and rounded; FPS is an estimate from logged intervals.",
        "These are logged observations, not a controlled original-versus-optimized A/B.",
        "resolve_ms=0 is unmeasured when log_gpu_frame_time_breakdown_passes=false.",
    ],
}
# Layout verified against this build's packed Vulkan PipelineStoredDescription:
# hash(8) + four shader uint64s(32) + RenderPassKey(4) + packed state(6) + RTs(16).
pipeline_path = root / "4D5309C9.fbo.vk.xpso"
if pipeline_path.exists():
    data = pipeline_path.read_bytes()
    assert data[:4] == b"XEPS" and (len(data) - 12) % 66 == 0
    modes = Counter()
    for offset in range(12, len(data), 66):
        pixel_hash, pixel_mod = struct.unpack_from("<QQ", data, offset + 24)
        if pixel_hash:
            modes[(pixel_mod >> 45) & 7] += 1
    report["cached_pipeline_pixel_depth_modes"] = dict(sorted(modes.items()))
    report["cached_no_alpha_pipelines"] = modes[7]
    report["pipeline_cache_note"] = (
        "Mode 7 is kNoAlphaTests. Counts prove cached specialization use, not draw frequency.")
(root / "summary.json").write_text(json.dumps(report, indent=2))
print(json.dumps(report, indent=2))
