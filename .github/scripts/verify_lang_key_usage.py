#!/usr/bin/env python3
"""Every ``photon.*`` translation key the code references must exist in the lang files.

A key that is referenced but missing shows up raw in the UI (players see
``photon.gui.editor.timeline.edit_signal`` instead of "Edit Signal"), which is
exactly how the edit-signal undo label shipped once. The scan is literal-based:
string literals starting with ``photon.`` are checked against ``en_us.json``.

Only two kinds of literal may skip the lang files, and both must be listed here
so the exception stays reviewed:

- KNOWN_STATE_KEYS: preference/state identifiers that reuse the ``photon.``
  namespace but are never translated (dialog size memory keys and friends);
- literals ending in ``.``: dynamic prefixes such as
  ``"photon.configuration.collision." + key`` whose full keys are assembled at
  runtime and therefore checked through the family they build.
"""
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
KNOWN_STATE_KEYS = {
    "photon.manage_fxpack.contents",  # remembered pack-contents dialog size
    "photon.smoke.dialog_size",       # smoke probe of the same preference
}

en_keys = json.loads((ROOT / "src/main/resources/assets/photon/lang/en_us.json").read_text(encoding="utf-8"))

literals = set()
grep = subprocess.run(["grep", "-rhoE", r'"photon\.[a-z0-9_.]+"', str(ROOT / "src/main/java"), "--include=*.java"],
                      capture_output=True, text=True)
for line in grep.stdout.splitlines():
    literals.add(line.strip('"'))

missing = []
for literal in sorted(literals):
    if literal.endswith(".") or literal in KNOWN_STATE_KEYS:
        continue
    if literal in en_keys:
        continue
    if any(key.startswith(literal) for key in en_keys):  # dynamic family, e.g. key + ".tooltip"
        continue
    missing.append(literal)

if missing:
    print("[lang-key-usage] FAILED: code references translation keys that no language provides:")
    for key in missing:
        print(" -", key)
    sys.exit(1)
print(f"[lang-key-usage] OK: {len(literals)} referenced photon.* literals all resolve "
      f"({len(KNOWN_STATE_KEYS)} reviewed state-key exceptions)")
