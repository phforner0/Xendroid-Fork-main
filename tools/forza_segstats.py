"""Cut runtime A/B arms out of one xe.log by the emulator's own property-switch
lines and average the per-second GpuFrame / VkFrameSync / VkFences / SpinPark
reports of each arm.

Usage: forza_segstats.py <xe.log> <switch marker regex> [skip reports] [out.json]
The marker regex must capture the new value, e.g.
  "wait_reg_mem_backoff = (\\w+)"  or  "fake screen extents mode (\\d+)".
"""
import json, math, re, statistics, sys

path, marker = sys.argv[1], re.compile(sys.argv[2])
skip = int(sys.argv[3]) if len(sys.argv) > 3 else 8
out = sys.argv[4] if len(sys.argv) > 4 else None
num = r"([-\d.]+)"
gf_re = re.compile(r"GpuFrame: (\d+) frames, interval avg=" + num +
                   r"ms max=" + num + r"ms \| per frame: exec=" + num +
                   r"ms draws=" + num + r" draw=" + num + r"ms swap=" + num +
                   r"ms stall=" + num + r"ms")
wrm_re = re.compile(r"wait_reg_mem unmet=" + num + r" waited=" + num + r"ms")
vk_re = re.compile(r"VkFrameSync: (\d+) frames .*?submissions=" + num +
                   r".*?gpu exec avg=" + num + r"ms max=" + num +
                   r"ms gap avg=" + num + r"ms")
replay_re = re.compile(r"replay=" + num + r"ms")
fence_re = re.compile(r"VkFences: per frame: polls=" + num + r" slow=" + num +
                      r" poll=" + num + r"ms waits=" + num + r" wait=" + num +
                      r"ms")
park_re = re.compile(r"SpinPark: mode (\d+) \| waits/s=" + num + r" avg=" +
                     num + r"ms waiting=" + num + r"% \| parks/s=" + num +
                     r" parked=" + num + r"% woken_by_progress=" + num + r"%")
# Frame intervals over 37 / 50 ms, counted per report (newer builds).
long_re = re.compile(r"intervals >37ms=(\d+) >50ms=(\d+)")

arms, cur = [], None
in_config_dump = False
for line in open(path, encoding="utf-8", errors="replace"):
    # The startup config dump lists every option as "name = value", which
    # matches the switch markers: it would open an "arm" spanning the menus
    # and the loading screens (GPU ~17 ms/frame), skewing that value's mean.
    if "----------- CONFIG DUMP" in line:
        in_config_dump = True
        continue
    if in_config_dump:
        in_config_dump = "END OF CONFIG DUMP" not in line
        continue
    m = marker.search(line)
    if m:
        cur = {"value": m.group(1), "gf": [], "vk": [], "replay": [],
               "fence": [], "park": []}
        arms.append(cur)
        continue
    if cur is None:
        continue
    if (m := gf_re.search(line)):
        w = wrm_re.search(line)
        lg = long_re.search(line)
        cur["gf"].append((float(m.group(2)), float(m.group(4)),
                          float(m.group(5)), float(m.group(6)),
                          float(w.group(1)) if w else float("nan"),
                          float(w.group(2)) if w else float("nan"),
                          float(m.group(7)), float(m.group(3)),
                          float(m.group(1)),
                          float(lg.group(1)) if lg else float("nan"),
                          float(lg.group(2)) if lg else float("nan")))
    elif (m := vk_re.search(line)):
        cur["vk"].append(float(m.group(2)) * float(m.group(3)))
        r = replay_re.search(line)
        if r:
            cur["replay"].append(float(r.group(1)))
    elif (m := fence_re.search(line)):
        cur["fence"].append(tuple(float(m.group(i)) for i in range(1, 6)))
    elif (m := park_re.search(line)):
        # waits/s, avg ms, waiting %, parks/s, parked %, woken %.
        cur["park"].append(tuple(float(m.group(i)) for i in range(2, 8)))


def fmean(xs):
    xs = list(xs)
    return statistics.fmean(xs) if xs else float("nan")


rows = []
for i, a in enumerate(arms, 1):
    gf, vk = a["gf"][skip:], a["vk"][skip:]
    if not gf:
        continue
    # The other reports come once per second too; skip proportionally.
    fence = a["fence"][skip:]
    replay = a["replay"][skip:]
    park = a["park"][max(0, skip // 2):]
    interval = fmean(x[0] for x in gf)
    frames = sum(x[8] for x in gf)
    rows.append({
        "arm": i, "value": a["value"], "reports": len(gf),
        "fps": 1000.0 / interval, "interval_ms": interval,
        # Mean of the per-second worst frame, and the share of frames over
        # 37 / 50 ms.
        "max_ms": fmean(x[7] for x in gf),
        "slow37_pct": 100.0 * sum(x[9] for x in gf) / frames,
        "slow50_pct": 100.0 * sum(x[10] for x in gf) / frames,
        "gpu_ms": fmean(vk),
        "cp_exec_ms": fmean(x[1] for x in gf), "draws": fmean(x[2] for x in gf),
        "cp_draw_ms": fmean(x[3] for x in gf),
        "swap_ms": fmean(x[6] for x in gf),
        "wrm_unmet": fmean(x[4] for x in gf),
        "wrm_waited_ms": fmean(x[5] for x in gf),
        "replay_ms": fmean(replay),
        "fence_polls": fmean(x[0] for x in fence),
        "fence_slow": fmean(x[1] for x in fence),
        "fence_poll_ms": fmean(x[2] for x in fence),
        "fence_wait_ms": fmean(x[4] for x in fence),
        "gwait_pct": fmean(x[2] for x in park),
        "gwait_avg_ms": fmean(x[1] for x in park),
        "parked_pct": fmean(x[4] for x in park),
        "woken_pct": fmean(x[5] for x in park),
    })
cols = ["arm", "value", "reports", "fps", "interval_ms", "max_ms",
        "slow37_pct", "slow50_pct", "gpu_ms",
        "cp_exec_ms", "draws", "cp_draw_ms", "swap_ms", "wrm_unmet",
        "wrm_waited_ms", "replay_ms", "fence_polls", "fence_slow",
        "fence_poll_ms", "fence_wait_ms", "gwait_pct", "gwait_avg_ms",
        "parked_pct", "woken_pct"]
# Drop columns without any data (older logs).
cols = [c for c in cols if any(
    not (isinstance(r[c], float) and math.isnan(r[c])) for r in rows)]
print("\t".join(cols))
for r in rows:
    print("\t".join(f"{r[c]:.2f}" if isinstance(r[c], float) else str(r[c])
                    for c in cols))
by_value = {}
for r in rows:
    by_value.setdefault(r["value"], []).append(r)
for v, rs in by_value.items():
    extra = ""
    if "fence_poll_ms" in cols:
        extra += (f" fence_poll {fmean(r['fence_poll_ms'] for r in rs):.2f} ms"
                  f" replay {fmean(r['replay_ms'] for r in rs):.1f} ms")
    if "gwait_pct" in cols:
        extra += (f" guest_wait {fmean(r['gwait_pct'] for r in rs):.0f}%"
                  f" parked {fmean(r['parked_pct'] for r in rs):.0f}%")
    if "slow37_pct" in cols:
        extra += (f" max {fmean(r['max_ms'] for r in rs):.1f} ms"
                  f" slow>37ms {fmean(r['slow37_pct'] for r in rs):.1f}%"
                  f" >50ms {fmean(r['slow50_pct'] for r in rs):.2f}%")
    print(f"value {v}: fps {fmean(r['fps'] for r in rs):.2f} "
          f"gpu {fmean(r['gpu_ms'] for r in rs):.1f} ms "
          f"wrm_waited {fmean(r['wrm_waited_ms'] for r in rs):.2f} ms"
          f"{extra} ({len(rs)} arms)")
if out:
    json.dump(rows, open(out, "w"), indent=2)
