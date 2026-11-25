#!/usr/bin/env python3
"""Guard the settings screen against orphaned config entries and lang keys.

Two directions are checked, because Photon has been bitten by both halves:

1. every top-level ``PhotonConfig`` value (anything outside a nested
   ``builder.push`` section, which the LDLib2 section screens render instead)
   must have a row in ``PhotonConfigScreen`` — a spec entry with no row is a
   setting players can only reach by hand-editing the toml, and with no reader
   it silently does nothing;
2. every screen row must ship its label and tooltip in all three languages.

The one-shot migration guard is not a player setting and is allow-listed.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CONFIG = ROOT / "src/main/java/com/lowdragmc/photon/PhotonConfig.java"
SCREEN = ROOT / "src/main/java/com/lowdragmc/photon/client/PhotonConfigScreen.java"
LANGS = ["en_us", "es_es", "zh_cn"]
NOT_SCREEN_OPTIONS = {"migratedOldBloomDefaults"}

config_src = CONFIG.read_text(encoding="utf-8")
screen_src = SCREEN.read_text(encoding="utf-8")

# Map every ConfigValue field to the nested section its builder.define* call lives in
# (None = top level). The field declarations sit at class level; the section ownership is
# decided by the definition site inside the constructor's push/pop blocks.
fields = {}
section = None
for line in config_src.splitlines():
    code = line.split("//")[0]
    pushed = re.search(r'builder\.push\("([^"]+)"\)', code)
    if pushed:
        section = pushed.group(1)
        continue
    if "builder.pop()" in code:
        section = None
        continue
    defined = re.search(r"(\w+)\s*=\s*builder\.define\w*\(", code)
    if defined:
        fields[defined.group(1)] = section

failures = []

top_level = [name for name, sec in fields.items()
             if sec is None and name not in NOT_SCREEN_OPTIONS]
used = set(re.findall(r"\bconfig\.(\w+)", screen_src))

for name in sorted(top_level):
    if name not in used:
        failures.append(f"PhotonConfig.{name} has no row in PhotonConfigScreen — "
                        "players cannot change it and it may silently do nothing")
for name in sorted(used):
    if name not in fields:
        failures.append(f"PhotonConfigScreen references config.{name}, "
                        "which PhotonConfig does not define")

rows = re.findall(r'add(?:Boolean|Int|Double|Enum)\("([^"]+)"', screen_src)
if not rows:
    failures.append("found no option rows in PhotonConfigScreen; the parser went blind")
for lang in LANGS:
    data = json.loads((ROOT / f"src/main/resources/assets/photon/lang/{lang}.json").read_text(encoding="utf-8"))
    for key in rows:
        if key not in data:
            failures.append(f"{lang}.json is missing the row label {key}")
        if f"{key}.tooltip" not in data:
            failures.append(f"{lang}.json is missing the row tooltip {key}.tooltip")

if failures:
    print("[config-screen-coverage] FAILED:")
    for failure in failures:
        print(" -", failure)
    sys.exit(1)
print(f"[config-screen-coverage] OK: {len(top_level)} top-level settings all have screen rows, "
      f"{len(rows)} rows labelled and documented in {len(LANGS)} languages")
