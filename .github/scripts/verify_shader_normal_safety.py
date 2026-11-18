#!/usr/bin/env python3
"""Static regression checks for shader paths that reconstruct or export surface normals."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
SHADERS = ROOT / "src/main/resources/assets/photon/shaders"


def uncomment(source: str) -> str:
    return re.sub(r"/\*.*?\*/|//[^\n]*", "", source, flags=re.S)


shader_files = [*SHADERS.rglob("*.fsh"), *SHADERS.rglob("*.vsh"), *SHADERS.rglob("*.glsl")]
for path in shader_files:
    source = uncomment(path.read_text(encoding="utf-8"))
    if re.search(r"(?<![A-Za-z0-9_])normalize\s*\(", source):
        raise SystemExit(f"unguarded GLSL normalize call in {path.relative_to(ROOT)}")

soft_particles = uncomment((SHADERS / "include/soft_particle.glsl").read_text(encoding="utf-8"))
for token in (
    "abs(view.w) > 1e-8 && abs(view.w) < 1e30",
    "eye > 0.0 && eye < 1e30 ? eye : 1e30",
):
    if token not in soft_particles:
        raise SystemExit(f"soft-particle projection guard missing: {token}")

shared = uncomment((SHADERS / "include/dynamic_light_depth.glsl").read_text(encoding="utf-8"))
for token in (
    "photon_safe_screen_size()",
    "photon_safe_resolution_scale()",
    "abs(p.w) > 1e-8 && abs(p.w) < 1e30",
    "all(lessThan(abs(position), vec3(1e30)))",
):
    if token not in shared:
        raise SystemExit(f"dynamic-light projection guard missing: {token}")

exporter = ROOT / "src/main/java/com/lowdragmc/photon/client/gameobject/emitter/data/material/kila/export/KilaGraphExport.java"
if re.search(r"(?<![A-Za-z0-9_])normalize\s*\(", exporter.read_text(encoding="utf-8")):
    raise SystemExit("Kila graph exporter emits an unguarded GLSL normalize call")

for relative in (
    "include/dynamic_light_depth.glsl",
    "include/soft_particle.glsl",
    "include/particle.glsl",
    "core/dynamic_light_pass.fsh",
    "core/dynamic_light_composite.fsh",
    "core/dynamic_light_haze.fsh",
    "core/dynamic_light_visibility.fsh",
):
    path = SHADERS / relative
    source = uncomment(path.read_text(encoding="utf-8"))
    if source.count("{") != source.count("}"):
        raise SystemExit(f"unbalanced braces in {path.relative_to(ROOT)}")

print(f"Shader normal/projection safety checks passed across {len(shader_files)} shader sources.")
