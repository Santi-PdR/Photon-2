#!/usr/bin/env python3
"""Language parity gate for Photon's lang files.

Checks, for every language file under assets/photon/lang:
  * the key set is EXACTLY the en_us key set (no missing, no orphan keys);
  * every {placeholder} of the English string also appears in the
    translation (and no new placeholders appear);
  * the JSON parses (strict UTF-8).

Fails with a non-zero exit code and a per-language report otherwise.
Run from the repository root (as CI does).
"""
import json
import re
import sys
from pathlib import Path

LANG_DIR = Path("src/main/resources/assets/photon/lang")
BASE = "en_us.json"
LANGS = ["zh_cn.json", "es_es.json"]
PLACEHOLDER = re.compile(r"\{[^{}]+\}")


def load(name: str):
    with (LANG_DIR / name).open(encoding="utf-8") as f:
        return json.load(f)


def main() -> int:
    try:
        base = load(BASE)
    except Exception as exc:  # noqa: BLE001
        print(f"[lang-parity] cannot parse {BASE}: {exc}")
        return 1

    base_keys = set(base)
    failed = False

    for name in LANGS:
        path = LANG_DIR / name
        if not path.is_file():
            print(f"[lang-parity] MISSING FILE {path}")
            failed = True
            continue
        try:
            data = load(name)
        except Exception as exc:  # noqa: BLE001
            print(f"[lang-parity] cannot parse {name}: {exc}")
            failed = True
            continue

        missing = sorted(base_keys - set(data))
        orphan = sorted(set(data) - base_keys)
        if missing:
            failed = True
            print(f"[lang-parity] {name}: {len(missing)} missing keys, e.g. {missing[:10]}")
        if orphan:
            failed = True
            print(f"[lang-parity] {name}: {len(orphan)} orphan keys, e.g. {orphan[:10]}")

        bad_placeholders = []
        for key in sorted(base_keys & set(data)):
            want = set(PLACEHOLDER.findall(base[key]))
            got = set(PLACEHOLDER.findall(data[key]))
            if want != got:
                bad_placeholders.append((key, sorted(want), sorted(got)))
        if bad_placeholders:
            failed = True
            print(f"[lang-parity] {name}: {len(bad_placeholders)} placeholder mismatches:")
            for key, want, got in bad_placeholders[:10]:
                print(f"    {key}: en has {want}, translation has {got}")

        if not missing and not orphan and not bad_placeholders:
            print(f"[lang-parity] {name}: OK ({len(data)} keys, parity with {BASE})")

    if failed:
        print("[lang-parity] FAILED")
        return 1
    print("[lang-parity] all languages in parity")
    return 0


if __name__ == "__main__":
    sys.exit(main())
