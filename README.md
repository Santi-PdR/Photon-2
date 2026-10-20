# Photon-2 Forge 1.20.1 Port

An in-progress Forge port of Photon-2, targeting Minecraft 1.20.1, Forge 47.x, and Java 17. The implementation is based on the community Forge 1.20.1 source baseline and is being reconciled against the Photon 26.2 reference. See [PORT_AUDIT.md](PORT_AUDIT.md) for coverage, validation, and outstanding work.

## Build

With Java 17 installed, run:

```bash
./gradlew build
```

The distributable JAR is `build/libs/photon-forge-1.20.1-26.2.2.3-all.jar`. Unit tests run as part of `build`; `./gradlew test` runs only the tests. The project compiles against LDLib 1.0.52.a from the target profile, matching the version used by `ghouls`. Photon bundles LDLib2 and the Forge 47.4.10-compatible KilaGraph build. KotlinForForge is a separate required profile dependency, version 4.12.0 or newer; Photon deliberately does not bundle Kotlin or KFF, avoiding duplicate Kotlin JPMS packages. The current updated Ghouls profile still has KFF 4.11.0, so it does not meet this dependency requirement yet.

GitHub Actions builds this branch with Java 17 and publishes the JAR as a downloadable workflow artifact after a successful build. The workflow artifact is a test build, not a final release.

## License

See [LICENSE](LICENSE) for the upstream license and port distribution conditions. Photon is developed by Low-Drag-MC.
