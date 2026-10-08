# Photon-2 Forge 1.20.1 Port

An in-progress Forge port of Photon-2, targeting Minecraft 1.20.1, Forge 47.x, and Java 17. The implementation is based on the community Forge 1.20.1 source baseline and is being reconciled against the Photon 26.2 reference. See [PORT_AUDIT.md](PORT_AUDIT.md) for coverage, validation, and outstanding work.

## Build

With Java 17 installed, run:

```bash
./gradlew build
```

The distributable all-in-one JAR is `build/libs/photon-forge-1.20.1-26.2.2.3-all.jar`. Unit tests run as part of `build`; `./gradlew test` runs only the tests. The project compiles against LDLib 1.0.52.a from the target profile, matching the version used by `ghouls`. Photon embeds LDLib2, the Forge 47.4.10-compatible KilaGraph build, the KotlinForForge language/mod modules, and its Kotlin runtime under the `kotlin.stdlib` module name to coexist with profiles that already contain Kotlin packages. Those dependencies do not need separate JARs in the profile.

GitHub Actions builds this branch with Java 17 and publishes the JAR as a downloadable workflow artifact after a successful build. The workflow artifact is a test build, not a final release.

## License

See [LICENSE](LICENSE) for the upstream license and port distribution conditions. Photon is developed by Low-Drag-MC.
