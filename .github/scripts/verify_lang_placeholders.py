#!/usr/bin/env python3
"""Translation keys with placeholders ("Add %s") must always be rendered with their arguments.

Minecraft's ``TranslatableContents`` has no partial formatting: when a template
contains more ``%s`` than arguments were passed, decomposition throws internally
and the component falls back to the *raw template*, so the player reads
"Add %s" instead of "Add Size". That is how the timeline's add/remove-property
undo labels shipped once: pushed through ``pushEdit(key, ...)``, which
translated the key with no arguments at all.

Two checks, both static:

1. Every Java occurrence of a placeholder key literal must sit inside a call
   that forwards arguments (``Component.translatable``, ``I18n.get``,
   ``Component.translatableWithFallback``) with at least as many arguments as
   the template has placeholders. A placeholder key handed to any other API
   (``pushEdit``, ``tooltips``, menu leaves, plain assignment, ...) fails,
   because those translate the key argument-free.
2. ``es_es``/``zh_cn`` must keep the same placeholder count as ``en_us`` for
   every key: a translation that drops a ``%s`` silently loses an argument, one
   that adds a ``%s`` renders raw.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LANG_DIR = ROOT / "src/main/resources/assets/photon/lang"
JAVA_ROOT = ROOT / "src/main/java"

# enclosing call -> number of positional arguments between the key and the format arguments
FORWARDING_CALLS = {
    "translatable": 0,              # Component.translatable(key, args...)
    "translatableWithFallback": 1,  # Component.translatableWithFallback(key, fallback, args...)
    "I18n.get": 0,                  # I18n.get(key, args...)
}
PLACEHOLDER = re.compile(r"%(?:(\d+)\$)?[sd]")


def placeholder_count(template):
    """Arguments a template needs: sequential ``%s`` count or the highest ``%n$s`` index."""
    sequential = 0
    highest_indexed = 0
    for match in PLACEHOLDER.finditer(template):
        if match.group(1):
            highest_indexed = max(highest_indexed, int(match.group(1)))
        else:
            sequential += 1
    return max(sequential, highest_indexed)


def load(name):
    return json.loads((LANG_DIR / name).read_text(encoding="utf-8"))


def strip_comments(src):
    """Blank out // and /* */ comments while keeping offsets/line numbers stable."""
    out = []
    i = 0
    n = len(src)
    while i < n:
        c = src[i]
        if c == '"':
            j = i + 1
            while j < n and src[j] != '"':
                j += 2 if src[j] == "\\" else 1
            out.append(src[i:j + 1])
            i = j + 1
        elif src.startswith("//", i):
            j = src.find("\n", i)
            j = n if j < 0 else j
            out.append(" " * (j - i))
            i = j
        elif src.startswith("/*", i):
            j = src.find("*/", i + 2)
            j = n if j < 0 else j + 2
            out.append("".join("\n" if ch == "\n" else " " for ch in src[i:j]))
            i = j
        else:
            out.append(c)
            i += 1
    return "".join(out)


def enclosing_call(src, pos):
    """The call expression that contains ``pos``: (callee, open_paren_index) or None."""
    depth = 0
    i = pos - 1
    in_string = False
    while i >= 0:
        c = src[i]
        if c == '"' and not (i > 0 and src[i - 1] == "\\"):
            in_string = not in_string
        elif not in_string:
            if c in ")]}":
                depth += 1
            elif c in "([{":
                if depth == 0:
                    if c != "(":
                        return None
                    j = i - 1
                    while j >= 0 and src[j].isspace():
                        j -= 1
                    k = j
                    while k >= 0 and (src[k].isalnum() or src[k] in "_.$"):
                        k -= 1
                    callee = src[k + 1:j + 1]
                    if not callee:
                        return None
                    return callee, i
                depth -= 1
        i -= 1
    return None


def split_args(src, open_paren):
    """Top-level argument spans of the call starting at ``open_paren``."""
    args = []
    depth = 0
    start = open_paren + 1
    i = start
    in_string = False
    while i < len(src):
        c = src[i]
        if in_string:
            if c == "\\":
                i += 1
            elif c == '"':
                in_string = False
        elif c == '"':
            in_string = True
        elif c in "([{":
            depth += 1
        elif c in ")]}":
            if depth == 0:
                args.append((start, i))
                return args
            depth -= 1
        elif c == "," and depth == 0:
            args.append((start, i))
            start = i + 1
        i += 1
    return args


def main():
    en = load("en_us.json")
    needs = {key: placeholder_count(value) for key, value in en.items()
             if isinstance(value, str) and placeholder_count(value) > 0}
    failures = []

    # 2) placeholder parity across languages
    for name in ("es_es.json", "zh_cn.json"):
        other = load(name)
        for key, value in en.items():
            if not isinstance(value, str) or key not in other or not isinstance(other[key], str):
                continue  # presence parity is verify_lang_parity's job
            expected, got = placeholder_count(value), placeholder_count(other[key])
            if expected != got:
                failures.append(f"{name}: {key} has {got} placeholder(s), en_us has {expected}")

    # 1) every Java use of a placeholder key forwards enough arguments
    checked = 0
    literal = re.compile(r'"(' + "|".join(re.escape(k) for k in sorted(needs, key=len, reverse=True)) + r')"')
    for path in sorted(JAVA_ROOT.rglob("*.java")):
        raw = path.read_text(encoding="utf-8", errors="replace")
        src = strip_comments(raw)
        for match in literal.finditer(src):
            key = match.group(1)
            line = src.count("\n", 0, match.start()) + 1
            where = f"{path.relative_to(ROOT)}:{line}"
            checked += 1
            call = enclosing_call(src, match.start())
            if call is None:
                failures.append(f"{where}: '{key}' needs {needs[key]} argument(s) but is not passed to a "
                                f"translating call (store the Component, not the key)")
                continue
            callee, open_paren = call
            short = callee.rsplit(".", 1)[-1]
            forwarding = FORWARDING_CALLS.get(callee, FORWARDING_CALLS.get(short) if short != "get" else None)
            if forwarding is None:
                failures.append(f"{where}: '{key}' needs {needs[key]} argument(s) but is handed to "
                                f"{callee}(...), which translates it argument-free")
                continue
            args = split_args(src, open_paren)
            key_index = next((i for i, (a, b) in enumerate(args) if a <= match.start() < b), None)
            if key_index is None:
                failures.append(f"{where}: could not locate '{key}' among the arguments of {callee}(...)")
                continue
            supplied = len(args) - key_index - 1 - forwarding
            if supplied < needs[key]:
                failures.append(f"{where}: {callee}(\"{key}\", ...) passes {max(supplied, 0)} argument(s), "
                                f"the template needs {needs[key]}")

    if failures:
        print("[lang-placeholders] FAILED:")
        for failure in failures:
            print(" -", failure)
        sys.exit(1)
    print(f"[lang-placeholders] OK: {len(needs)} placeholder keys, {checked} Java uses all forward enough "
          f"arguments; es_es/zh_cn placeholder counts match en_us")


if __name__ == "__main__":
    main()
