#!/usr/bin/env python3
"""Keep Java's ordinal-uploaded Kila blend presets aligned with the fragment shader."""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "src/main/java/com/lowdragmc/photon/client/gameobject/emitter/data/material/BlendMode.java"
SHADER = ROOT / "src/main/resources/assets/photon/shaders/core/kila.fsh"

java = JAVA.read_text(encoding="utf-8")
shader = SHADER.read_text(encoding="utf-8")
match = re.search(r"public enum Preset\s*\{(.*?)\n\s*public BlendMode create\(", java, re.S)
if not match:
    raise SystemExit("Could not find BlendMode.Preset enum constants")
# Strip comments before reading constants, so documentation cannot affect the ordinal map.
body = re.sub(r"/\*.*?\*/|//[^\n]*", "", match.group(1), flags=re.S)
java_names = re.findall(r"\b(ALPHA|ADDITIVE|PREMULTIPLIED|MULTIPLY|OPAQUE)\b", body)
if len(java_names) != 5 or len(set(java_names)) != 5:
    raise SystemExit(f"Unexpected Java preset constants: {java_names}")

shader_values = {
    name: int(value)
    for name, value in re.findall(r"^\s*#define KILA_BLEND_(ALPHA|ADDITIVE|PREMULTIPLIED|MULTIPLY|OPAQUE)\s+(\d+)\s*$", shader, re.M)
}
expected_values = {name: index for index, name in enumerate(java_names)}
if shader_values != expected_values:
    raise SystemExit(f"Java ordinal values {expected_values} do not match shader values {shader_values}")

# Guard the semantic use of each ordinal too: the map alone would not catch a swapped shader branch.
shader_compact = re.sub(r"\s+", " ", shader)
branch_checks = {
    "PREMULTIPLIED": r"if \(preset == KILA_BLEND_PREMULTIPLIED\)",
    "MULTIPLY": r"else if \(preset == KILA_BLEND_MULTIPLY\)",
    "OPAQUE": r"else if \(preset == KILA_BLEND_OPAQUE\)",
    "ADDITIVE stack source": r"float stackSrc = preset <= KILA_BLEND_ADDITIVE \? c\.a : 1\.0;",
    "ADDITIVE stack destination": r"float stackDst = preset == KILA_BLEND_ADDITIVE \? 1\.0",
    "ALPHA/PREMULTIPLIED stack destination":
        r"preset == KILA_BLEND_ALPHA \|\| preset == KILA_BLEND_PREMULTIPLIED \? 1\.0 - c\.a : 0\.0;",
}
for label, pattern in branch_checks.items():
    if not re.search(pattern, shader_compact):
        raise SystemExit(f"Kila shader preset behavior changed or lost its named mapping: {label}")
print("Kila blend ordinals: Java preset order and shader behavior match named constants")
