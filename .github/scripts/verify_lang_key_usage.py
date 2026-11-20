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

Inspector labels are checked too: every ``@Configurable(name = ..., tips = ...)``
string literal is a translation key LDLib2 renders verbatim when it is missing
(the Custom Shader material once listed its curves under a raw "curves" row),
whatever namespace it uses (``ParticleConfig.duration``, ``photon.*``, ...).
A ``@Configurable`` without ``name`` is checked the same way: LDLib2 then uses
the Java field name as the label key, so ``depthTest`` must be a lang key or
the inspector shows "depthTest" (the Material slot shipped three such rows).
``showName = false`` hides the label and is exempt.
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

# @Configurable(name = "...", tips = "..." | {"...", "..."}) labels: rendered raw by LDLib2 when untranslated.
# Without name = ..., the label key is the field name (ConfiguratorParser: name().isEmpty() ? field.getName()).
ANNOTATION = re.compile(r"@Configurable\b(?:\s*\(((?:[^()]|\([^()]*\))*)\))?", re.S)
ATTRIBUTE = re.compile(r"\b(name|tips)\s*=\s*(?:\"([^\"]*)\"|\{([^}]*)\})")
FIELD = re.compile(r"^\s*(?:(?:public|protected|private|static|final|transient|volatile)\s+)*[\w<>\[\],.?\s]+?\s+(\w+)\s*(?:=|;)")
annotation_keys = 0
annotation_missing = []


def labelled_field(source, start):
    """Name of the field declared after the annotation at ``start`` (skipping other annotations), or None."""
    for line in source[start:start + 800].split("\n")[1:]:
        stripped = line.strip()
        if not stripped or stripped.startswith("@") or stripped.startswith("//"):
            continue
        match = FIELD.match(line)
        return match.group(1) if match else None
    return None


for path in sorted((ROOT / "src/main/java").rglob("*.java")):
    source = path.read_text(encoding="utf-8", errors="replace")
    for annotation in ANNOTATION.finditer(source):
        line_start = source.rfind("\n", 0, annotation.start()) + 1
        prefix = source[line_start:annotation.start()].lstrip()
        if prefix.startswith("//") or prefix.startswith("*") or prefix.startswith("/*"):
            continue  # javadoc / comment mention, not a declaration
        line = source.count("\n", 0, annotation.start()) + 1
        arguments = annotation.group(1) or ""
        has_name = False
        for attribute in ATTRIBUTE.finditer(arguments):
            keys = [attribute.group(2)] if attribute.group(2) is not None else re.findall(r'"([^"]*)"', attribute.group(3))
            if attribute.group(1) == "name":
                has_name = True
            for key in keys:
                if not key:
                    continue
                annotation_keys += 1
                if key not in en_keys:
                    annotation_missing.append(f"{path.relative_to(ROOT)}:{line}: @Configurable {attribute.group(1)} = \"{key}\"")
        if has_name or re.search(r"\bshowName\s*=\s*false", arguments):
            continue
        field = labelled_field(source, annotation.end())
        if field is None:
            continue  # class-level @Configurable: the type's simple name is shown only inside the type registry UI
        annotation_keys += 1
        if field not in en_keys:
            annotation_missing.append(f"{path.relative_to(ROOT)}:{line}: @Configurable without name on field \"{field}\" "
                                      f"(LDLib2 labels it with the field name; add name = \"...\" and a lang key)")

if missing or annotation_missing:
    print("[lang-key-usage] FAILED: code references translation keys that no language provides:")
    for key in missing:
        print(" -", key)
    for entry in annotation_missing:
        print(" -", entry)
    sys.exit(1)
print(f"[lang-key-usage] OK: {len(literals)} referenced photon.* literals and {annotation_keys} @Configurable "
      f"name/tips keys all resolve ({len(KNOWN_STATE_KEYS)} reviewed state-key exceptions)")
