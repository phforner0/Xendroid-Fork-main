"""Summarize interleaved A/B arms: presented FPS (SurfaceFlinger) plus the
once-per-second VkFrameSync / GpuFrame diagnostics logged during each arm."""
import json, pathlib, re, statistics, sys

ROOT = pathlib.Path(r"C:\Users\Administrator\Desktop\Xendroid-Fork-main\performance-tests")
prefix = sys.argv[1]
window = int(sys.argv[2]) if len(sys.argv) > 2 else 40  # last N one-second reports

num = r"([-\d.]+)"
vk_re = re.compile(
    r"VkFrameSync: (\d+) frames .*?submissions=" + num + r" resolves=" + num +
    r".*?gpu exec avg=" + num + r"ms max=" + num + r"ms gap avg=" + num +
    r"ms .*?draws=" + num + r" rp_begins=" + num + r" splits=" + num +
    r" replay=" + num + r"ms \| resolve_clears=" + num + r" in_guest_pass=" + num)
gf_re = re.compile(
    r"GpuFrame: (\d+) frames, interval avg=" + num + r"ms max=" + num +
    r"ms \| per frame: exec=" + num + r"ms draws=" + num + r" draw=" + num +
    r"ms swap=" + num + r"ms stall=" + num + r"ms")
# Newer builds append how long the command processor sat in unmet
# PM4_WAIT_REG_MEM waits.
wrm_re = re.compile(r"wait_reg_mem unmet=" + num + r" waited=" + num + r"ms")

rows = []
for d in sorted(ROOT.glob(prefix + "-*"), key=lambda p: int(p.name.split("-")[1])):
    s = json.loads((d / "summary.json").read_text())
    log = (d / "xe.log").read_text(encoding="utf-8", errors="replace").splitlines()
    vk = [m for l in log if (m := vk_re.search(l))][-window:]
    gf = [m for l in log if (m := gf_re.search(l))][-window:]
    wrm = [m for l in log if "GpuFrame" in l and (m := wrm_re.search(l))][-window:]

    def mean(ms, idx):
        vals = [float(m.group(idx)) for m in ms]
        return statistics.fmean(vals) if vals else float("nan")

    subm = mean(vk, 2)
    rows.append({
        "arm": d.name,
        "fps": s["presented_fps"],
        "p95_ms": s["presented_frame_ms_p95"],
        "gpu_ms_per_frame": mean(vk, 4) * subm if vk else float("nan"),
        "gpu_gap_ms_per_frame": mean(vk, 6) * subm if vk else float("nan"),
        "rp_begins": mean(vk, 8),
        "resolves": mean(vk, 3),
        "resolve_clears": mean(vk, 11),
        "in_guest_pass": mean(vk, 12),
        "replay_ms": mean(vk, 10),
        "cp_exec_ms": mean(gf, 4),
        "cp_draw_ms": mean(gf, 6),
        "cp_stall_ms": mean(gf, 8),
        "guest_interval_ms": mean(gf, 2),
        "wrm_unmet": mean(wrm, 1),
        "wrm_waited_ms": mean(wrm, 2),
        "vk_samples": len(vk), "gf_samples": len(gf),
    })

cols = ["arm", "fps", "p95_ms", "gpu_ms_per_frame", "gpu_gap_ms_per_frame",
        "rp_begins", "resolves", "resolve_clears", "in_guest_pass", "replay_ms",
        "cp_exec_ms", "cp_draw_ms", "cp_stall_ms", "guest_interval_ms",
        "wrm_unmet", "wrm_waited_ms", "vk_samples", "gf_samples"]
print("\t".join(cols))
for r in rows:
    print("\t".join(f"{r[c]:.2f}" if isinstance(r[c], float) else str(r[c]) for c in cols))
(ROOT / f"{prefix}-report.json").write_text(json.dumps(rows, indent=2))
