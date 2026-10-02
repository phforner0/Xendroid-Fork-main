"""CPU work per frame from the per-thread hardware counters of simpleperf
(`simpleperf stat --per-thread -e instructions,cpu-cycles`, written by
fh_auto.sh with STAT=<seconds>): instructions and cycles per frame for the
guest CPU threads, the GPU command thread, XMA, audio and the rest, averaged
per label with the spread between the files of a label.

Instructions per frame are the stable measure of CPU work: within a launch two
10 s windows agreed within ~1%, while CPU time moved 1.6x between launches of
the same build with the core and clock the scheduler picked. Cycles per frame
show what the work costs at the IPC it ran with.

Usage: forza_cpustat.py [--fps F] <label=stat.txt | stat.txt>...
Files with the same label are averaged. --fps defaults to the 30 fps cap.
"""
import collections
import re
import sys

args = sys.argv[1:]
fps = 30.0
while args and args[0].startswith("--"):
    if args[0] == "--fps":
        fps = float(args[1])
    args = args[2:]
if not args:
    sys.exit(__doc__)

line_re = re.compile(r"^\s+(.+?)\s+(\d+)\s+(\d+)\s+([\d,]+)\s+(\S+)\s+#\s*([\d.]+)\s*(\S*)")
time_re = re.compile(r"Total test time:\s+([\d.]+)\s+seconds")


def group_of(name):
    if name.startswith("Guest CPU "):
        return "guest " + name[len("Guest CPU "):].strip()
    if name.startswith("GPU Commands"):
        return "commands"
    if name.startswith("XMA Decoder"):
        return "xma"
    if name.startswith("Audio") or name == "AudioTrack":
        return "audio"
    return "other"


def load(path):
    counts = collections.defaultdict(lambda: collections.defaultdict(int))
    seconds = None
    for line in open(path, encoding="utf-8", errors="replace"):
        m = time_re.search(line)
        if m:
            seconds = float(m.group(1))
            continue
        m = line_re.match(line)
        if not m:
            continue
        event = m.group(5)
        if event not in ("instructions", "cpu-cycles"):
            continue
        count = int(m.group(4).replace(",", ""))
        counts[group_of(m.group(1))][event] += count
        # The cycles' comment is count / runtime: the thread's mean clock.
        if event == "cpu-cycles" and m.group(7) == "GHz" and float(m.group(6)):
            counts[group_of(m.group(1))]["runtime_ns"] += int(
                count / float(m.group(6)))
    if not seconds:
        sys.exit(f"{path}: no 'Total test time' line")
    per_frame = {}
    for group, events in counts.items():
        per_frame[group] = {e: v / seconds / fps / 1e6 for e, v in events.items()
                            if e != "runtime_ns"}
        # CPU time in milliseconds per frame.
        per_frame[group]["ms"] = events.get("runtime_ns", 0) / seconds / fps / 1e6
    # Totals.
    guest = collections.defaultdict(float)
    total = collections.defaultdict(float)
    for group, events in per_frame.items():
        for e, v in events.items():
            total[e] += v
            if group.startswith("guest "):
                guest[e] += v
    per_frame["guest"] = dict(guest)
    per_frame["total"] = dict(total)
    return per_frame


runs = collections.OrderedDict()
for a in args:
    label, _, path = a.rpartition("=")
    label = label or path
    runs.setdefault(label, []).append(load(path))

order = ["total", "guest"] + [f"guest {i}" for i in range(6)] + [
    "commands", "xma", "audio", "other"]
labels = list(runs)
print(f"M instructions / M cycles per frame at {fps:g} fps "
      "(mean over the label's files; [min-max] when several)")
print(f"{'':12s}" + "".join(f"{l[:26]:>28s}" for l in labels))
for group in order:
    cells = []
    present = False
    for label in labels:
        ins = [r[group]["instructions"] for r in runs[label]
               if group in r and "instructions" in r[group]]
        cyc = [r[group].get("cpu-cycles", 0.0) for r in runs[label]
               if group in r]
        if not ins:
            cells.append(f"{'-':>28s}")
            continue
        present = True
        mi = sum(ins) / len(ins)
        mc = sum(cyc) / len(cyc) if cyc else 0.0
        cell = f"{mi:7.1f} / {mc:6.1f}"
        if len(ins) > 1:
            cell += f" [{(max(ins) - min(ins)) / mi * 100:4.1f}%]"
        cells.append(f"{cell:>28s}")
    if present:
        print(f"{group:12s}" + "".join(cells))
print("\nCPU ms per frame / mean GHz (cycles over the time they ran in):")
for group in ("total", "guest", "guest 5", "commands", "xma"):
    cells = []
    for label in labels:
        ms = [r[group]["ms"] for r in runs[label] if group in r]
        cyc = [r[group].get("cpu-cycles", 0.0) for r in runs[label]
               if group in r]
        if not ms or not sum(ms):
            cells.append(f"{'-':>28s}")
            continue
        m_ms = sum(ms) / len(ms)
        ghz = sum(cyc) / sum(ms)  # M cycles per ms = GHz
        cells.append(f"{m_ms:8.1f} ms {ghz:5.2f} GHz".rjust(28))
    print(f"{group:12s}" + "".join(cells))
if len(labels) > 1:
    base = labels[0]
    print(f"\ninstructions per frame against '{base}':")
    for group in ("total", "guest", "commands"):
        b = [r[group]["instructions"] for r in runs[base]]
        b = sum(b) / len(b)
        out = []
        for label in labels[1:]:
            v = [r[group]["instructions"] for r in runs[label]]
            v = sum(v) / len(v)
            out.append(f"{label}: {v - b:+.2f} M ({(v - b) / b * 100:+.2f}%)")
        print(f"  {group:9s} " + "   ".join(out))
