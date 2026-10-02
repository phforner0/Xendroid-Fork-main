#!/usr/bin/env python3
"""Build tool for original XenDroid shaders, never imported LSFG runtime data."""
import argparse
import struct
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("input")
parser.add_argument("output")
parser.add_argument("symbol")
args = parser.parse_args()
data = Path(args.input).read_bytes()
if len(data) % 4 or len(data) < 20 or struct.unpack_from("<I", data)[0] != 0x07230203:
    raise SystemExit("Invalid SPIR-V")
words = struct.unpack("<" + "I" * (len(data) // 4), data)
header = "// Generated from original XenDroid GLSL.\nstatic const uint32_t " + args.symbol + "[] = {\n"
header += "\n".join("  " + ", ".join(f"0x{word:08x}u" for word in words[i:i + 8]) + "," for i in range(0, len(words), 8))
Path(args.output).parent.mkdir(parents=True, exist_ok=True)
Path(args.output).write_text(header + "\n};\n")
