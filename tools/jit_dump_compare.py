"""Compares the host code the JIT emitted for the same guest functions under
different options: the fndump_<address>.txt files of dump_functions_at, one
directory per variant. Counts the host instructions of each function and the
instruction kinds that change the most, so a JIT option can be judged by
code that doesn't depend on what the game happened to do during a launch.

Usage: jit_dump_compare.py <variant=dir>... [--weights addr=share,...]
--weights gives each function its share of the guest instructions (from an
instruction-sampled profile) for a weighted total.
"""
import collections
import os
import re
import sys

args = sys.argv[1:]
weights = {}
if "--weights" in args:
    i = args.index("--weights")
    for item in args[i + 1].split(","):
        address, share = item.split("=")
        weights[address.upper()] = float(share)
    del args[i:i + 2]
variants = []
for a in args:
    name, _, path = a.partition("=")
    variants.append((name, path))

line_re = re.compile(r"^\s*(?:[0-9A-F]{8}\s+)?[0-9A-F]{6,16}\s+(\S+)")


def load(path):
    functions = {}
    for f in sorted(os.listdir(path)):
        m = re.match(r"fndump_([0-9A-Fa-f]{8})\.txt$", f)
        if not m:
            continue
        kinds = collections.Counter()
        total = 0
        in_host = False
        for line in open(os.path.join(path, f), encoding="utf-8",
                         errors="replace"):
            if line.startswith("===== HOST MACHINE CODE"):
                in_host = True
                continue
            if not in_host:
                continue
            lm = line_re.match(line)
            if not lm:
                continue
            total += 1
            kinds[lm.group(1)] += 1
        functions[m.group(1).upper()] = (total, kinds)
    return functions


data = [(name, load(path)) for name, path in variants]
addresses = sorted(set().union(*(set(d) for _, d in data)))
print(f"{'function':10s}" + "".join(f"{name:>14s}" for name, _ in data))
weighted = [0.0] * len(data)
for address in addresses:
    row = f"{address:10s}"
    base = data[0][1].get(address, (0, None))[0]
    for vi, (name, d) in enumerate(data):
        total = d.get(address, (0, None))[0]
        if vi and base:
            row += f"{total:8d} {100 * (total - base) / base:+4.0f}%"
        else:
            row += f"{total:14d}"
        if base and address in weights:
            weighted[vi] += weights[address] * total / base
    print(row)
if weights:
    share = sum(weights[a] for a in addresses if a in weights)
    print("weighted (relative to the first variant, over "
          f"{share:.1%} of the guest instructions): " +
          "  ".join(f"{name} {w / share:.3f}" for (name, _), w in
                    zip(data, weighted)))
# The instruction kinds that changed the most between the first and the last
# variant, over all functions.
first = collections.Counter()
last = collections.Counter()
for address in addresses:
    if address in data[0][1]:
        first.update(data[0][1][address][1])
    if address in data[-1][1]:
        last.update(data[-1][1][address][1])
changes = sorted(set(first) | set(last), key=lambda k: last[k] - first[k])
print(f"\nkinds changed most ({data[0][0]} -> {data[-1][0]}):")
for k in changes[:12] + changes[-4:]:
    print(f"  {k:10s} {first[k]:7d} -> {last[k]:7d} ({last[k] - first[k]:+d})")
