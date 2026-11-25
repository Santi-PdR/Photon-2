#!/usr/bin/env python3
"""Keep renderer call-site hooks optional when another mod replaces the vanilla invocation."""
from pathlib import Path

source = Path("src/main/java/com/lowdragmc/photon/core/mixins/GameRendererMixin.java").read_text(encoding="utf-8")
expected_methods = ("photon$prepareTimelinePostEffects", "photon$afterLevelRender")
for method in expected_methods:
    method_at = source.find("private void " + method)
    if method_at < 0:
        raise SystemExit(f"[renderer-mixin-fallback] missing hook method {method}")
    inject_at = source.rfind("@Inject(", 0, method_at)
    if inject_at < 0:
        raise SystemExit(f"[renderer-mixin-fallback] {method} has no @Inject")
    annotation = source[inject_at:method_at]
    if "renderLevel(FJLcom/mojang/blaze3d/vertex/PoseStack;)V" not in annotation:
        raise SystemExit(f"[renderer-mixin-fallback] {method} no longer targets GameRenderer.renderLevel")
    if "LevelRenderer;renderLevel(" not in annotation:
        raise SystemExit(f"[renderer-mixin-fallback] {method} no longer anchors at LevelRenderer.renderLevel")
    if "require = 0" not in annotation or "expect = 1" not in annotation:
        raise SystemExit(f"[renderer-mixin-fallback] {method} must be fail-soft (require=0) and expect one vanilla match")

print("[renderer-mixin-fallback] both LevelRenderer call-site hooks tolerate a renderer mod replacing the invocation")
