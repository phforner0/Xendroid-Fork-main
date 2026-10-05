"""edram_trace.py <xe.log>: classifies the EDRAM ownership transfers of the
last frame of an EdramTrace (edram_trace_frames, debug.xendroid.edram_trace)
by what happens next on their destination - the state of the first draw of
the binding they were made for - and by whether their source was cleared by
a resolve after its last draw. Transfers skipped as overwritten
(skip_overwritten_transfers) are counted apart."""
import collections
import re
import sys

PREFIX = "EdramTrace: "
lines = []
for line in open(sys.argv[1], encoding="utf-8", errors="replace"):
    at = line.find(PREFIX)
    if at >= 0:
        lines.append(line[at + len(PREFIX):].strip())

# Frames end at "frame" / "end"; the first one starts mid-frame.
frames, cur = [], []
for l in lines:
    if l in ("frame", "end") or l.startswith("start"):
        if cur:
            frames.append(cur)
        cur = []
        continue
    cur.append(l)
if cur:
    frames.append(cur)
if not frames:
    sys.exit("no EdramTrace lines")
frame = frames[-1]

xfer_re = re.compile(r"(skipped )?transfer \[(.*?)\] -> \[(.*?)\], tiles "
                     r"(\d+)-(\d+)(.*)")
first_re = re.compile(r"first draw: depth test (\d) write (\d) func (\d), "
                      r"stencil (\d) write mask (\w+), color mask (\w+), "
                      r"(\d+) rows")
draws_re = re.compile(r"draws (\d+):")

pending, rows = [], []
for i, l in enumerate(frame):
    m = xfer_re.match(l)
    if m:
        skipped, src, dst, a, b, rest = m.groups()
        pending.append(dict(src=src, dst=dst, tiles=int(b) - int(a),
                            skipped=bool(skipped),
                            host_depth="host depth" in rest,
                            cleared="source cleared" in rest))
        continue
    m = first_re.match(l)
    if m and pending:
        zt, zw, zf, st, _, cm, nrows = m.groups()
        draws = None
        for l2 in frame[i + 1:i + 3]:
            d = draws_re.match(l2)
            if d:
                draws = int(d.group(1))
                break
        for p in pending:
            p.update(zt=int(zt), zw=int(zw), zf=int(zf), st=int(st),
                     cm=int(cm, 16), rows=int(nrows), draws=draws)
            rows.append(p)
        pending = []
        continue
    if l.startswith(("resolve", "clear")) and pending:
        # The transfers of a resolve's clear (outside the cleared rectangle).
        for p in pending:
            p["resolve"] = True
            rows.append(p)
        pending = []


def category(p):
    if p["skipped"]:
        return "skipped as overwritten by the draw"
    if p.get("resolve"):
        return "for a resolve clear"
    if " kD24" in " " + p["dst"] and p["zt"] and p["zw"] and p["zf"] == 7:
        return ("depth destination, first draw depth always + write" +
                (" + stencil" if p["st"] else ", no stencil"))
    if p["cleared"]:
        return "source cleared after its last draw"
    return "other"


total, count = collections.Counter(), collections.Counter()
for p in rows:
    total[category(p)] += p["tiles"]
    count[category(p)] += 1
all_tiles = sum(total.values()) or 1
print(f"last traced frame: {len(rows)} transfers, {all_tiles} tiles")
for c, t in total.most_common():
    print(f"  {t:6d} tiles ({100.0 * t / all_tiles:4.1f}%) "
          f"{count[c]:3d} transfers: {c}")
print()
for p in rows:
    state = ("resolve" if p.get("resolve") else
             f"z{p['zt']}w{p['zw']}f{p['zf']} s{p['st']} c{p['cm']:X} "
             f"rows {p['rows']} draws {p['draws']}")
    flags = (("S" if p["skipped"] else " ") + ("C" if p["cleared"] else " ") +
             ("H" if p["host_depth"] else " "))
    print(f"{p['tiles']:5d}  {p['src'][:44]:44s} -> {p['dst'][:44]:44s} "
          f"{flags} {state}")
