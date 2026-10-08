#!/usr/bin/env python3
"""Prepare Photon-private Kotlin runtimes without split packages.

KFF 4.11 supplies the Kotlin classes needed while Forge scans language
providers. LDLib2 embeds another Kotlin stdlib; remove that nested copy from
Photon's private LDLib2 archive so KFF remains the sole provider.
"""

from __future__ import annotations

import argparse
from pathlib import Path
import json
from zipfile import ZIP_DEFLATED, ZipFile


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    parser.add_argument("ldlib2_source", type=Path)
    parser.add_argument("ldlib2_destination", type=Path)
    args = parser.parse_args()

    with ZipFile(args.source) as source:
        names = set(source.namelist())
        manifest_name = "META-INF/MANIFEST.MF"
        module_info = "META-INF/versions/9/module-info.class"
        if manifest_name not in names or "kotlin/jvm/internal/Intrinsics.class" not in names:
            raise SystemExit("Input is not the expected KotlinForForge runtime archive")
        if module_info not in names:
            raise SystemExit("Input has no Kotlin reflect module descriptor to remove")
        metadata_name = "META-INF/jarjar/metadata.json"
        if metadata_name not in names:
            raise SystemExit("Input is missing the nested KFF module metadata")
        nested = [
            "META-INF/jarjar/kfflang-4.11.0.jar",
            "META-INF/jarjar/kfflib-4.11.0.jar",
            "META-INF/jarjar/kffmod-4.11.0.jar",
        ]
        if any(name not in names for name in nested):
            raise SystemExit("Input is missing one or more KFF runtime modules")

        manifest = source.read(manifest_name).decode("utf-8")
        if "Automatic-Module-Name: thedarkcolour.kotlinforforge" not in manifest:
            raise SystemExit("Input has an unexpected KFF automatic module name")
        manifest = manifest.replace("Automatic-Module-Name: thedarkcolour.kotlinforforge",
                                    "Automatic-Module-Name: kotlin.stdlib", 1)

        args.destination.parent.mkdir(parents=True, exist_ok=True)
        with ZipFile(args.destination, "w", ZIP_DEFLATED) as output:
            for entry in source.infolist():
                name = entry.filename
                if name == module_info:
                    continue
                data = manifest.encode("utf-8") if name == manifest_name else source.read(entry)
                output.writestr(name, data)

    with ZipFile(args.ldlib2_source) as source:
        metadata_name = "META-INF/jarjar/metadata.json"
        if metadata_name not in source.namelist():
            raise SystemExit("LDLib2 archive is missing JarJar metadata")
        metadata = json.loads(source.read(metadata_name))
        removed = [item for item in metadata.get("jars", [])
                   if item.get("identifier", {}).get("artifact") == "kotlin-stdlib"]
        if len(removed) != 1:
            raise SystemExit("Expected exactly one nested LDLib2 Kotlin stdlib")
        removed_paths = {item["path"] for item in removed}

        args.ldlib2_destination.parent.mkdir(parents=True, exist_ok=True)
        with ZipFile(args.ldlib2_destination, "w", ZIP_DEFLATED) as output:
            for entry in source.infolist():
                if entry.filename in removed_paths:
                    continue
                data = source.read(entry)
                if entry.filename == metadata_name:
                    metadata["jars"] = [item for item in metadata["jars"]
                                        if item["identifier"]["artifact"] != "kotlin-stdlib"]
                    data = (json.dumps(metadata, indent=2) + "\n").encode("utf-8")
                output.writestr(entry.filename, data)


if __name__ == "__main__":
    main()
