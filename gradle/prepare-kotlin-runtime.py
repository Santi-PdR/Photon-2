#!/usr/bin/env python3
"""Prepare Photon-private Kotlin runtimes without split packages or ABI drift.

KFF 4.11 supplies Forge's language provider and Kotlin reflection/coroutines.
LDLib2 embeds a newer Kotlin stdlib. Put those newer stdlib classes in KFF's
early-visible kotlin.stdlib module, then remove LDLib2's nested duplicate.
"""

from __future__ import annotations

import argparse
import json
from io import BytesIO
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    parser.add_argument("ldlib2_source", type=Path)
    parser.add_argument("ldlib2_destination", type=Path)
    args = parser.parse_args()

    with ZipFile(args.ldlib2_source) as ldlib2:
        ldlib2_metadata_name = "META-INF/jarjar/metadata.json"
        if ldlib2_metadata_name not in ldlib2.namelist():
            raise SystemExit("LDLib2 archive is missing JarJar metadata")
        ldlib2_metadata = json.loads(ldlib2.read(ldlib2_metadata_name))
        stdlib_dependencies = [item for item in ldlib2_metadata.get("jars", [])
                               if item.get("identifier", {}).get("artifact") == "kotlin-stdlib"]
        if len(stdlib_dependencies) != 1:
            raise SystemExit("Expected exactly one nested LDLib2 Kotlin stdlib")
        stdlib_dependency = stdlib_dependencies[0]
        stdlib_path = stdlib_dependency["path"]
        stdlib_version = stdlib_dependency.get("version", {}).get("artifactVersion")
        with ZipFile(BytesIO(ldlib2.read(stdlib_path))) as kotlin_stdlib:
            stdlib_manifest_name = "META-INF/MANIFEST.MF"
            if stdlib_manifest_name not in kotlin_stdlib.namelist():
                raise SystemExit("LDLib2 Kotlin stdlib is missing its manifest")
            stdlib_manifest = kotlin_stdlib.read(stdlib_manifest_name).decode("utf-8")
            if f"Implementation-Version: {stdlib_version}-" not in stdlib_manifest:
                raise SystemExit("LDLib2 Kotlin stdlib version does not match its JarJar metadata")
            stdlib_entries = {
                name: kotlin_stdlib.read(name)
                for name in kotlin_stdlib.namelist()
                if (name.startswith(("kotlin/", "_COROUTINE/", "META-INF/versions/9/kotlin/"))
                    or name.startswith("META-INF/services/kotlin.")
                    or (name.startswith("META-INF/") and name.endswith(".kotlin_module")))
            }

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
        manifest = manifest.replace(
            "Automatic-Module-Name: kotlin.stdlib",
            f"Automatic-Module-Name: kotlin.stdlib\nPhoton-Kotlin-Stdlib-Version: {stdlib_version}",
            1,
        )
        stdlib_names = set(stdlib_entries)

        args.destination.parent.mkdir(parents=True, exist_ok=True)
        with ZipFile(args.destination, "w", ZIP_DEFLATED) as output:
            for entry in source.infolist():
                name = entry.filename
                if name == module_info or name in stdlib_names:
                    continue
                data = manifest.encode("utf-8") if name == manifest_name else source.read(entry)
                output.writestr(name, data)
            for name, data in stdlib_entries.items():
                output.writestr(name, data)

    with ZipFile(args.ldlib2_source) as source:
        removed_paths = {stdlib_path}

        args.ldlib2_destination.parent.mkdir(parents=True, exist_ok=True)
        with ZipFile(args.ldlib2_destination, "w", ZIP_DEFLATED) as output:
            for entry in source.infolist():
                if entry.filename in removed_paths:
                    continue
                data = source.read(entry)
                if entry.filename == ldlib2_metadata_name:
                    ldlib2_metadata["jars"] = [item for item in ldlib2_metadata["jars"]
                                                if item["identifier"]["artifact"] != "kotlin-stdlib"]
                    data = (json.dumps(ldlib2_metadata, indent=2) + "\n").encode("utf-8")
                output.writestr(entry.filename, data)


if __name__ == "__main__":
    main()
