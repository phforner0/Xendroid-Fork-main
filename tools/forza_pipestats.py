"""Join PipeStats (Turnip shader statistics per pipeline, logged at creation
with vulkan_pipeline_statistics) with PipeUse (draws per shader pair and pass
size in pm4_bin_trace frames) and rank the shader pairs of each pass size.

Usage: forza_pipestats.py <stats xe.log> [use xe.log] [top N]
The two logs may come from different runs: shader hashes are stable.
PipeStats needs `vulkan_pipeline_statistics = true` ([Vulkan]) at startup;
PipeUse comes from `adb shell setprop debug.xendroid.pm4_bin_trace N`.
"""
import collections, re, statistics, sys

stats_path = sys.argv[1]
use_path = sys.argv[2] if len(sys.argv) > 2 and not sys.argv[2].isdigit() \
    else stats_path
top = int(sys.argv[-1]) if sys.argv[-1].isdigit() else 25
stats_re = re.compile(r"PipeStats: VS ([0-9A-F]{16}) PS ([0-9A-F]{16}) mod "
                      r"([0-9A-F]{16}) (\w+)((?: \| [^|]+)+)$")
use_re = re.compile(r"PipeUse: VS ([0-9A-F]{16}) PS ([0-9A-F]{16}) pass "
                    r"(\d+)x(\d+) draws=(\d+) verts=(\d+)")
# Pixel shader texture bindings by component signs, per pass size (newer
# builds): unsigned / signed / biased / gamma.
signs_re = re.compile(r"TexSigns frame \d+: pass (\d+)x(\d+) draws=\d+ "
                      r"textures=(\d+) unsigned=([\d.]+)% signed=([\d.]+)% "
                      r"biased=([\d.]+)% gamma=([\d.]+)%")

# (vs, ps) -> stage -> list of {stat: value} (one per pixel shader variant).
stats = collections.defaultdict(lambda: collections.defaultdict(list))
for line in open(stats_path, encoding="utf-8", errors="replace"):
    m = stats_re.search(line.rstrip())
    if not m:
        continue
    d = {}
    for kv in m.group(5).split(" | ")[1:]:
        k, _, v = kv.rpartition("=")
        try:
            d[k.strip()] = float(v)
        except ValueError:
            pass
    stats[(m.group(1), m.group(2))][m.group(4)].append(d)

use = collections.defaultdict(lambda: [0, 0])
# pass -> [textures, unsigned, signed, biased, gamma] (texture counts).
signs = collections.defaultdict(lambda: [0.0] * 5)
frames = 0
for line in open(use_path, encoding="utf-8", errors="replace"):
    if "PipeUse frame" in line:
        frames += 1
    m = signs_re.search(line)
    if m:
        s = signs[f"{m.group(1)}x{m.group(2)}"]
        textures = int(m.group(3))
        s[0] += textures
        for i in range(4):
            s[1 + i] += textures * float(m.group(4 + i)) / 100
    m = use_re.search(line)
    if m:
        u = use[(m.group(1), m.group(2), f"{m.group(3)}x{m.group(4)}")]
        u[0] += int(m.group(5))
        u[1] += int(m.group(6))
frames = max(frames, 1)


def stat(vs, ps, stage, name):
    # Max over the variants (alpha specialization etc.) of that shader pair.
    vals = [d[name] for d in stats.get((vs, ps), {}).get(stage, []) if name in d]
    return max(vals) if vals else None


print(f"PipeStats: {sum(len(v2) for v in stats.values() for v2 in v.values())}"
      f" executables for {len(stats)} shader pairs; PipeUse frames: {frames}")
fs_all = [d for v in stats.values() for d in v.get("FS", [])]
if fs_all:
    for name in ("Instruction Count", "NOPs Count", "Registers used",
                 "Max Waves Per Core", "cat5 instructions",
                 "Estimated cycles stalled on SY", "Loops"):
        vals = [d[name] for d in fs_all if name in d]
        if vals:
            print(f"  FS {name}: median {statistics.median(vals):.0f} "
                  f"p90 {sorted(vals)[int(len(vals) * 0.9)]:.0f} "
                  f"max {max(vals):.0f}")

f = lambda x: "-" if x is None else f"{x:.0f}"
by_pass = collections.defaultdict(list)
for (vs, ps, p), (draws, verts) in use.items():
    by_pass[p].append((draws, verts, vs, ps))
for p, rows in sorted(by_pass.items(), key=lambda kv: -sum(r[0] for r in kv[1])):
    rows.sort(reverse=True)
    total = sum(r[0] for r in rows)
    print(f"\n== pass {p}: {total / frames:.0f} draws/frame, {len(rows)} "
          f"shader pairs")
    if signs[p][0]:
        t = signs[p][0]
        print(f"  texture bindings/frame {t / frames:.0f}: unsigned "
              f"{100 * signs[p][1] / t:.0f}% signed {100 * signs[p][2] / t:.0f}% "
              f"biased {100 * signs[p][3] / t:.0f}% gamma {100 * signs[p][4] / t:.0f}%")
    print("  draws/fr verts/fr | FS instr nops regs waves tex sy_stall loops "
          "| VS instr | VS / PS")
    for draws, verts, vs, ps in rows[:top]:
        print(f"  {draws / frames:8.0f} {verts / frames:8.0f} | "
              f"{f(stat(vs, ps, 'FS', 'Instruction Count')):>8} "
              f"{f(stat(vs, ps, 'FS', 'NOPs Count')):>4} "
              f"{f(stat(vs, ps, 'FS', 'Registers used')):>4} "
              f"{f(stat(vs, ps, 'FS', 'Max Waves Per Core')):>5} "
              f"{f(stat(vs, ps, 'FS', 'cat5 instructions')):>3} "
              f"{f(stat(vs, ps, 'FS', 'Estimated cycles stalled on SY')):>8} "
              f"{f(stat(vs, ps, 'FS', 'Loops')):>5} | "
              f"{f(stat(vs, ps, 'VS', 'Instruction Count')):>8} | {vs} / {ps}")
