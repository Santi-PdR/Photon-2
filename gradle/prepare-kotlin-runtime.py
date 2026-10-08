#!/usr/bin/env python3
"""Build a KFF runtime carrier that uses LDLib2's Kotlin standard library.

The local KFF 4.11 archive duplicates Kotlin stdlib classes also supplied by
LDLib2. Keeping both providers made Forge select the newer stdlib while KFF's
language-provider scanner could not load ``kotlin.jvm.internal.Intrinsics``.
Retain KFF's kotlinx runtime and nested kfflang/kfflib/kffmod modules, remove the
duplicate Kotlin stdlib packages, and give the carrier a unique module name.
"""

from __future__ import annotations

import argparse
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    args = parser.parse_args()

    with ZipFile(args.source) as source:
        names = set(source.namelist())
        manifest_name = "META-INF/MANIFEST.MF"
        module_info = "META-INF/versions/9/module-info.class"
        if manifest_name not in names or "kotlinx/coroutines/CoroutineScope.class" not in names:
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
        manifest = manifest.replace(
            "Automatic-Module-Name: thedarkcolour.kotlinforforge",
            "Automatic-Module-Name: com.lowdragmc.photon.kotlinruntime",
            1,
        )

        args.destination.parent.mkdir(parents=True, exist_ok=True)
        with ZipFile(args.destination, "w", ZIP_DEFLATED) as output:
            for entry in source.infolist():
                name = entry.filename
                if (name == module_info
                        or name.startswith("kotlin/")
                        or name.startswith("_COROUTINE/")
                        or name.startswith("META-INF/versions/9/kotlin/")
                        or name.startswith("META-INF/services/kotlin.")
                        or name.rsplit("/", 1)[-1].startswith("kotlin-stdlib")):
                    continue
                data = manifest.encode("utf-8") if name == manifest_name else source.read(entry)
                output.writestr(name, data)


if __name__ == "__main__":
    main()
