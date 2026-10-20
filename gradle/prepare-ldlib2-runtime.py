#!/usr/bin/env python3
"""Prepare a private, relocated Kotlin runtime inside Photon’s LDLib2 bundle."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile


PRIVATE_RUNTIME_PATH = "META-INF/jarjar/photon-kotlin-runtime-{version}.jar"
PRIVATE_PREFIX = "com/lowdragmc/photon/internal/embedded/"


def is_runtime_entry(name: str) -> bool:
    """Keep only Kotlin runtime code/resources from the KFF source jar."""
    package_path = name
    if name.startswith("META-INF/versions/"):
        parts = name.split("/", 3)
        package_path = parts[3] if len(parts) == 4 else ""
    return package_path.startswith(("kotlin/", "kotlinx/", "_COROUTINE/"))


def relocate(java: str, asm_classpath: str, relocator: Path, source: Path, output: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        [java, "--class-path", asm_classpath, str(relocator), str(source), str(output)],
        check=True,
    )


def main() -> None:
    if len(sys.argv) != 8:
        raise SystemExit(
            "usage: prepare-ldlib2-runtime.py LDLib2.jar KFF.jar OUTPUT.jar JAVA ASM_CP KOTLIN_VERSION RELOCATOR.java"
        )

    source_path, kff_path, destination_path = map(Path, sys.argv[1:4])
    java, asm_classpath = sys.argv[4:6]
    kotlin_version = sys.argv[6]
    relocator = Path(sys.argv[7])

    metadata_name = "META-INF/jarjar/metadata.json"
    with ZipFile(source_path) as source:
        if metadata_name not in source.namelist():
            raise SystemExit("LDLib2 archive is missing JarJar metadata")
        metadata = json.loads(source.read(metadata_name))
        stdlibs = [item for item in metadata.get("jars", [])
                   if item.get("identifier", {}).get("artifact") == "kotlin-stdlib"]
        if len(stdlibs) != 1:
            raise SystemExit("Expected exactly one nested LDLib2 Kotlin stdlib")
        dependency = stdlibs[0]
        old_stdlib_path = dependency.get("path")
        if not old_stdlib_path or old_stdlib_path not in source.namelist():
            raise SystemExit("LDLib2 Kotlin stdlib path is missing")
        min_version = dependency.get("version", {}).get("range", "")
        if min_version != "[2.1.20,)":
            raise SystemExit(f"Unexpected LDLib2 Kotlin requirement: {min_version}")

    destination_path.parent.mkdir(parents=True, exist_ok=True)
    runtime_source = destination_path.parent / "kotlin-runtime-source.jar"
    runtime_output = destination_path.parent / f"photon-kotlin-runtime-{kotlin_version}.jar"
    transformed_ldlib2 = destination_path.parent / "ldlib2-relocated.jar"
    try:
        with ZipFile(kff_path) as kff, ZipFile(runtime_source, "w", ZIP_DEFLATED) as runtime:
            copied = 0
            for entry in kff.infolist():
                if not is_runtime_entry(entry.filename):
                    continue
                if entry.filename.endswith("/module-info.class") or entry.filename == "module-info.class":
                    continue
                runtime.writestr(entry, kff.read(entry))
                copied += 1
            if copied == 0:
                raise SystemExit("The KFF source archive contains no Kotlin runtime classes")

        relocate(java, asm_classpath, relocator, runtime_source, runtime_output)
        relocate(java, asm_classpath, relocator, source_path, transformed_ldlib2)

        new_stdlib_path = PRIVATE_RUNTIME_PATH.format(version=kotlin_version)
        with ZipFile(transformed_ldlib2) as transformed, ZipFile(destination_path, "w", ZIP_DEFLATED) as output:
            for entry in transformed.infolist():
                if entry.filename == old_stdlib_path:
                    continue
                data = transformed.read(entry)
                if entry.filename == metadata_name:
                    dependency["path"] = new_stdlib_path
                    dependency["version"]["artifactVersion"] = kotlin_version
                    data = (json.dumps(metadata, indent=2) + "\n").encode("utf-8")
                output.writestr(entry, data)
            output.write(runtime_output, new_stdlib_path)
    finally:
        runtime_source.unlink(missing_ok=True)
        transformed_ldlib2.unlink(missing_ok=True)
        runtime_output.unlink(missing_ok=True)


if __name__ == "__main__":
    main()
