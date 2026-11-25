#!/usr/bin/env python3
"""Floating-point text formatting must not depend on the JVM's default locale.

``String.format("%.2f", x)`` and ``"%.2f".formatted(x)`` use
``Locale.getDefault()``: on a Spanish/German/French Windows that is ``1,50``,
while ``Float.parseFloat``/``Double.parseDouble`` and the editor's dot-only
input validators only understand ``1.50``. That is how the timeline's time box
shipped once: it mirrored the playhead as ``1,50`` and the value typed back
could never be parsed, so jumping the playhead from the box silently did
nothing on a Spanish PC (same for the curve range editor).

Rule: every format string with a floating-point conversion (``%f``, ``%e``,
``%g``, ``%a``, optionally with flags/width/precision) must go through
``String.format(Locale.ROOT, ...)`` (or another explicit ``Locale`` constant).
Display-only text follows the same rule so labels and the fields next to them
agree. ``%d``/``%s`` are locale-independent and not checked.

Scope: everything a player can see, i.e. all of ``src/main/java`` except the
``uitest`` scenarios, which are CI-only diagnostics on a fixed-locale runner.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA_ROOT = ROOT / "src/main/java"

FLOAT_CONVERSION = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[feEgGaA]")
# "...%.2f...".formatted(  |  String.format("...%.2f...  |  String.format(Locale.X, "...%.2f..."
FORMATTED_CALL = re.compile(r'"((?:[^"\\]|\\.)*)"\s*\.formatted\s*\(')
STRING_FORMAT_CALL = re.compile(r'String\.format\s*\(\s*(?:(Locale\.[A-Z_]+|java\.util\.Locale\.[A-Z_]+)\s*,\s*)?"((?:[^"\\]|\\.)*)"')


def main():
    failures = []
    checked = 0
    for path in sorted(JAVA_ROOT.rglob("*.java")):
        if "uitest" in path.relative_to(JAVA_ROOT).parts:
            continue
        src = path.read_text(encoding="utf-8", errors="replace")
        for match in FORMATTED_CALL.finditer(src):
            if FLOAT_CONVERSION.search(match.group(1)):
                line = src.count("\n", 0, match.start()) + 1
                failures.append(f"{path.relative_to(ROOT)}:{line}: \"{match.group(1)}\".formatted(...) uses the "
                                f"default locale; use String.format(Locale.ROOT, ...)")
        for match in STRING_FORMAT_CALL.finditer(src):
            locale, template = match.group(1), match.group(2)
            if not FLOAT_CONVERSION.search(template):
                continue
            checked += 1
            if locale is None:
                line = src.count("\n", 0, match.start()) + 1
                failures.append(f"{path.relative_to(ROOT)}:{line}: String.format(\"{template}\", ...) has no explicit "
                                f"Locale; use String.format(Locale.ROOT, ...)")
    if failures:
        print("[locale-safe-formatting] FAILED: floating-point formatting depends on the JVM default locale:")
        for failure in failures:
            print(" -", failure)
        sys.exit(1)
    print(f"[locale-safe-formatting] OK: {checked} floating-point format calls all pin an explicit Locale")


if __name__ == "__main__":
    main()
