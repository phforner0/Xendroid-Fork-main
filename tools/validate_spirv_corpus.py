#!/usr/bin/env python3
"""Validate both outputs of spirv_alpha_regression with Khronos SPIRV-Tools."""

import argparse
import json
from pathlib import Path
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--validator", default="spirv-val")
    args = parser.parse_args()
    generic = sorted(args.directory.glob("*-generic.spv"))
    if not generic:
        parser.error("no generic/specialized shader pairs found")
    failures = []
    sizes = {"generic": 0, "opaque": 0}
    for source in generic:
        for variant in sizes:
            shader = source.with_name(source.name.replace("-generic.spv", f"-{variant}.spv"))
            if not shader.is_file():
                failures.append({"shader": shader.name, "error": "missing variant"})
                continue
            result = subprocess.run(
                [args.validator, "--target-env", "vulkan1.0", str(shader)],
                capture_output=True, text=True, timeout=30)
            sizes[variant] += shader.stat().st_size
            if result.returncode:
                failures.append({"shader": shader.name, "error": result.stderr.strip()})
    report = {
        "shader_pairs": len(generic), "byte_counts": sizes,
        "byte_reduction_percent": 100 * (1 - sizes["opaque"] / sizes["generic"]),
        "validation_failures": failures,
        "note": "Bytecode reduction is not an FPS measurement or proof of pixel equivalence.",
    }
    print(json.dumps(report, indent=2))
    (args.directory / "validation.json").write_text(json.dumps(report, indent=2))
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
