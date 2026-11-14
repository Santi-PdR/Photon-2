#!/usr/bin/env python3
"""Check that every built-in Kila preset is registered and translated in shipped locales."""

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PRESETS = ROOT / "src/main/java/com/lowdragmc/photon/client/gameobject/emitter/data/material/kila/KilaPresets.java"
LANG_DIR = ROOT / "src/main/resources/assets/photon/lang"
LOCALES = ("en_us", "es_es", "zh_cn")


def fail(message: str) -> None:
    print(f"Kila preset catalog check failed: {message}", file=sys.stderr)
    raise SystemExit(1)


def read_locale(path: Path) -> dict[str, object]:
    duplicate_keys: list[str] = []

    def collect_pairs(pairs: list[tuple[str, object]]) -> dict[str, object]:
        result: dict[str, object] = {}
        for key, value in pairs:
            if key.startswith("kila.preset.") and key in result:
                duplicate_keys.append(key)
            result[key] = value
        return result

    try:
        entries = json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=collect_pairs)
    except (OSError, json.JSONDecodeError) as error:
        fail(f"cannot read {path.relative_to(ROOT)}: {error}")
    if duplicate_keys:
        fail(f"duplicate Kila preset translation keys in {path.name}: {sorted(set(duplicate_keys))}")
    return entries


def main() -> None:
    source = PRESETS.read_text(encoding="utf-8")
    definitions = re.findall(
        r'public\s+static\s+final\s+Preset\s+(\w+)\s*=\s*new Preset\("([a-z0-9_]+)"',
        source,
    )
    if not definitions:
        fail(f"no preset definitions found in {PRESETS.relative_to(ROOT)}")
    field_names = [field for field, _ in definitions]
    preset_ids = [preset_id for _, preset_id in definitions]
    if len(set(field_names)) != len(field_names):
        fail("duplicate preset constant fields")
    if len(set(preset_ids)) != len(preset_ids):
        fail("duplicate preset ids")

    all_match = re.search(r"public\s+static\s+final\s+List<Preset>\s+ALL\s*=\s*List\.of\((.*?)\);", source, re.DOTALL)
    if all_match is None:
        fail("could not find KilaPresets.ALL")
    registered_fields = re.findall(r"\b[A-Z][A-Z0-9_]*\b", all_match.group(1))
    if len(set(registered_fields)) != len(registered_fields):
        fail("KilaPresets.ALL registers a preset more than once")
    missing = sorted(set(field_names) - set(registered_fields))
    unknown = sorted(set(registered_fields) - set(field_names))
    if missing or unknown:
        fail(f"KilaPresets.ALL mismatch; missing={missing}, unknown={unknown}")

    for locale in LOCALES:
        entries = read_locale(LANG_DIR / f"{locale}.json")
        missing_keys = [
            key
            for preset_id in preset_ids
            for key in (f"kila.preset.{preset_id}", f"kila.preset.{preset_id}.desc")
            if not isinstance(entries.get(key), str) or not entries[key].strip()
        ]
        if missing_keys:
            fail(f"{locale} is missing preset names/descriptions: {missing_keys}")
        print(f"{locale}: {len(preset_ids)} preset names and descriptions verified")

    print(f"Kila preset catalog: {len(preset_ids)} unique presets are all registered and translated")


if __name__ == "__main__":
    main()
