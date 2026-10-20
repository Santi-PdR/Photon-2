#!/usr/bin/env python3
"""Prepare Photon’s private LDLib2 copy to use the profile’s KotlinForForge runtime."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("usage: prepare-ldlib2-runtime.py LDLib2.jar OUTPUT.jar")

    source_path, destination_path = map(Path, sys.argv[1:])
    with ZipFile(source_path) as source:
        metadata_name = "META-INF/jarjar/metadata.json"
        if metadata_name not in source.namelist():
            raise SystemExit("LDLib2 archive is missing JarJar metadata")
        metadata = json.loads(source.read(metadata_name))
        stdlib = [item for item in metadata.get("jars", [])
                  if item.get("identifier", {}).get("artifact") == "kotlin-stdlib"]
        if len(stdlib) != 1:
            raise SystemExit("Expected exactly one nested LDLib2 Kotlin stdlib")
        dependency = stdlib[0]
        stdlib_path = dependency.get("path")
        if not stdlib_path or stdlib_path not in source.namelist():
            raise SystemExit("LDLib2 Kotlin stdlib path is missing")
        version = dependency.get("version", {}).get("artifactVersion", "")
        if tuple(map(int, version.split("."))) < (2, 1, 20):
            raise SystemExit(f"LDLib2 Kotlin stdlib {version} is below Photon’s required 2.1.20")

        destination_path.parent.mkdir(parents=True, exist_ok=True)
        with ZipFile(destination_path, "w", ZIP_DEFLATED) as destination:
            for entry in source.infolist():
                if entry.filename == stdlib_path:
                    continue
                data = source.read(entry)
                if entry.filename == metadata_name:
                    metadata["jars"] = [item for item in metadata["jars"]
                                        if item is not dependency]
                    data = (json.dumps(metadata, indent=2) + "\n").encode("utf-8")
                destination.writestr(entry, data)


if __name__ == "__main__":
    main()
