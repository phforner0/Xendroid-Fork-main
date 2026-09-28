"""Compare whole runs (one xe.log per game launch) by the per-second GPU
reports of their last N seconds: frame interval, GPU time, and - with
log_gpu_frame_time_breakdown_passes - GPU time per render pass size
(VkPassTime), per resolve kind and size (VkResolveTime) and per other GPU work
category (VkMiscTime). Used for A/Bs of options read at startup, where each arm
is a separate launch (tools/forza_auto_ab.ps1 -RestartArms).

Usage: forza_passres.py [--last N] [--top K] <label=xe.log | xe.log>...
Logs with the same label are averaged together.
"""
import collections, re, statistics, sys

args = sys.argv[1:]
last_n, top = 22, 10
while args and args[0].startswith("--"):
    if args[0] == "--last":
        last_n = int(args[1])
    elif args[0] == "--top":
        top = int(args[1])
    args = args[2:]

num = r"([-\d.]+)"
sync_re = re.compile(r"VkFrameSync: (\d+) frames .*?submissions=" + num +
                     r".*?resolves=" + num + r".*?gpu exec avg=" + num +
                     r"ms.*?rp_begins=" + num)
gf_re = re.compile(r"GpuFrame: \d+ frames, interval avg=" + num)
pass_re = re.compile(r"VkPassTime: (xfer )?(\d+)x(\d+) : " + num +
                     r"ms/fr \(" + num + r"pass " + num + r"draw/fr")
res_re = re.compile(r"VkResolveTime: copy=(\w+)(\+clear)?( direct)? (\d+)x(\d+)"
                    r" : " + num + r"ms/fr \(" + num + r"/fr")
# Split of each resolve kind at the end of its copy (newer builds).
split_re = re.compile(r"\| copy " + num + r"ms/fr clear " + num + r"ms/fr")
misc_re = re.compile(r"VkMiscTime: (.+?) : " + num + r"ms/fr \(" + num + r"/fr")
# Render passes ended per frame by reason (newer builds).
ends_re = re.compile(r"VkPassEnd: per frame: (.*)")


def parse(path):
    reports, intervals, cur = [], [], None
    for line in open(path, encoding="utf-8", errors="replace"):
        if (m := gf_re.search(line)):
            intervals.append(float(m.group(1)))
        if (m := sync_re.search(line)):
            cur = {"gpu": float(m.group(2)) * float(m.group(4)),
                   "resolves": float(m.group(3)), "rp": float(m.group(5)),
                   "pass": {}, "res": {}, "misc": {}, "split": {},
                   "ends": {}}
            reports.append(cur)
        elif cur is None:
            continue
        elif (m := pass_re.search(line)):
            cur["pass"][f"{m.group(1) or ''}{m.group(2)}x{m.group(3)}"] = (
                float(m.group(4)))
        elif (m := res_re.search(line)):
            key = (f"{m.group(1)}{m.group(2) or ''}{m.group(3) or ''} "
                   f"{m.group(4)}x{m.group(5)}")
            cur["res"][key] = float(m.group(6))
            if (sm := split_re.search(line)):
                cur["split"][f"{key} copy"] = float(sm.group(1))
                cur["split"][f"{key} clear"] = float(sm.group(2))
        elif (m := misc_re.search(line)):
            cur["misc"][m.group(1).strip()] = float(m.group(2))
        elif (m := ends_re.search(line)):
            before, _, barriers = m.group(1).partition("| barriers:")
            for k, v in re.findall(r"(\w+)=([-\d.]+)", before):
                cur["ends"][k] = float(v)
            for k, v in re.findall(r"(\w+)=([-\d.]+)", barriers):
                cur["ends"][f"{k} barriers"] = float(v)
    reports = reports[-last_n:]
    intervals = intervals[-last_n:]
    if not reports or not intervals:
        return None
    n = len(reports)
    run = {"interval": statistics.fmean(intervals),
           "gpu": sum(r["gpu"] for r in reports) / n,
           "resolves": sum(r["resolves"] for r in reports) / n,
           "rp": sum(r["rp"] for r in reports) / n}
    for kind in ("pass", "res", "misc", "split", "ends"):
        agg = collections.defaultdict(float)
        for r in reports:
            for k, v in r[kind].items():
                agg[k] += v
        run[kind] = {k: v / n for k, v in agg.items()}
    return run


groups = collections.OrderedDict()
for arg in args:
    label, _, path = arg.rpartition("=")
    label = label or path
    run = parse(path)
    if run is None:
        print(f"no reports in {path}")
        continue
    groups.setdefault(label, []).append(run)


def mean(runs, key, sub=None):
    vals = [(r[key].get(sub, 0.0) if sub else r[key]) for r in runs]
    return statistics.fmean(vals) if vals else float("nan")


labels = list(groups)
print("label".ljust(24) + "runs    fps  interval   GPU/fr  resolves  passes")
for label in labels:
    runs = groups[label]
    iv = mean(runs, "interval")
    print(f"{label[:23]:<24}{len(runs):>4} {1000 / iv:6.2f} {iv:8.1f}ms "
          f"{mean(runs, 'gpu'):7.1f}ms {mean(runs, 'resolves'):8.1f} "
          f"{mean(runs, 'rp'):7.0f}")

for kind, title in (("pass", "render passes, ms/frame"),
                    ("res", "resolves, ms/frame"),
                    ("split", "resolves split at the end of the copy, ms/frame"),
                    ("misc", "GPU work outside passes and resolves, ms/frame"),
                    ("ends", "render passes ended by reason, per frame")):
    keys = collections.Counter()
    for runs in groups.values():
        for r in runs:
            for k, v in r[kind].items():
                keys[k] += v
    if not keys:
        continue
    print(f"\n{title} (top {top} + total):")
    print(" " * 36 + "".join(f"{label[:12]:>13}" for label in labels))
    for k, _ in keys.most_common(top):
        print(f"  {k[:34]:<34}" +
              "".join(f"{mean(groups[label], kind, k):13.2f}" for label in labels))
    print(f"  {'total':<34}" + "".join(
        f"{statistics.fmean(sum(r[kind].values()) for r in groups[label]):13.2f}"
        for label in labels))
