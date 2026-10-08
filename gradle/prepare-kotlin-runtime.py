#!/usr/bin/env python3
"""Repackage the local KFF 4.11 runtime under Kotlin's module name.

KFF's local 4.11 artifact contains Kotlin standard-library packages but names the
automatic Java module ``thedarkcolour.kotlinforforge``. A profile can already
provide a ``kotlin.stdlib`` module (for example, through another mod), causing a
split-package module-layer failure. Keep the exact local runtime classes and KFF's
nested kfflang/kfflib/kffmod dependencies, but expose those packages as
``kotlin.stdlib`` so module resolution selects one provider by module name.
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
        if manifest_name not in names or "kotlin/Unit.class" not in names:
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
            "Automatic-Module-Name: kotlin.stdlib",
            1,
        )

        args.destination.parent.mkdir(parents=True, exist_ok=True)
        with ZipFile(args.destination, "w", ZIP_DEFLATED) as output:
            for entry in source.infolist():
                if entry.filename == module_info:
                    continue
                data = manifest.encode("utf-8") if entry.filename == manifest_name else source.read(entry)
                output.writestr(entry.filename, data)


if __name__ == "__main__":
    main()
