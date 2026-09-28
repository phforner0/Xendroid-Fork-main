#!/usr/bin/env python3
"""Compare ir3stats CSVs of two translator variants shader by shader.

Usage: ir3cmp.py <A.csv> <B.csv> <FS|VS>
Sums and median ratios of the Turnip statistics over the shaders of that
stage present in both (pixel shaders for FS, vertex shaders for VS).
"""
import csv, signal, statistics, sys
from collections import defaultdict

KEYS = ["Instruction Count", "NOPs Count", "MOV Count", "cat2 instructions",
        "cat3 instructions", "cat4 instructions", "cat5 instructions",
        "Registers used", "Estimated cycles stalled on SY",
        "Preamble Instruction Count", "Max Waves Per Core", "Loops"]


def load(path, stage):
    data = defaultdict(dict)
    for row in csv.reader(open(path)):
        if len(row) != 4 or row[1] != stage:
            continue
        # Only the translated shader's own stage, not the partner's.
        if not row[0].startswith("ps_" if stage == "FS" else "vs_"):
            continue
        shader = row[0].rsplit("-", 1)[0]
        data[shader][row[2]] = float(row[3])
    return data


def main():
    signal.signal(signal.SIGPIPE, signal.SIG_DFL)  # Piping into head.
    a_path, b_path, stage = sys.argv[1], sys.argv[2], sys.argv[3]
    a, b = load(a_path, stage), load(b_path, stage)
    common = sorted(set(a) & set(b))
    print(f"{stage}: {len(common)} shaders in both ({len(a)} / {len(b)})")
    for key in KEYS:
        sa = sum(a[s].get(key, 0) for s in common)
        sb = sum(b[s].get(key, 0) for s in common)
        ratios = [b[s][key] / a[s][key] for s in common
                  if a[s].get(key) and key in b[s]]
        better = sum(1 for s in common if b[s].get(key, 0) < a[s].get(key, 0))
        worse = sum(1 for s in common if b[s].get(key, 0) > a[s].get(key, 0))
        med = statistics.median(ratios) if ratios else 0
        print(f"  {key:34s} A={sa:10.0f} B={sb:10.0f} "
              f"({100 * (sb / sa - 1) if sa else 0:+6.1f}%) "
              f"median ratio {med:.3f} lower={better} higher={worse}")
    key = "Instruction Count"
    deltas = sorted(((b[s][key] - a[s][key], s) for s in common
                     if key in a[s] and key in b[s]))
    print("  largest reductions:", ", ".join(
        f"{s}:{a[s][key]:.0f}->{b[s][key]:.0f}" for d, s in deltas[:5]))
    print("  largest increases:", ", ".join(
        f"{s}:{a[s][key]:.0f}->{b[s][key]:.0f}" for d, s in deltas[-5:][::-1]
        if d > 0))


if __name__ == "__main__":
    main()
