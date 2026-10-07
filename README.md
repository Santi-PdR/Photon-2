# Photon-2 Forge 1.20.1 Port

An in-progress Forge port of Photon-2, targeting Minecraft 1.20.1, Forge 47.x, and Java 17. The implementation is based on the community Forge 1.20.1 source baseline and is being reconciled against the Photon 26.2 reference. See [PORT_AUDIT.md](PORT_AUDIT.md) for coverage, validation, and outstanding work.

## Build

With Java 17 installed, run:

```bash
./gradlew build
```

The build output is written to `build/libs/`. Unit tests run as part of `build`; `./gradlew test` runs only the tests. The project requires the bundled LDLib2 and KilaGraph Forge libraries in `libs/`. Install Kotlin for Forge 4.10.0 or newer in the Forge 1.20.1 profile; LDLib2 uses its Kotlin runtime libraries.

GitHub Actions builds this branch with Java 17 and publishes the JAR as a downloadable workflow artifact after a successful build. The workflow artifact is a test build, not a final release.

## License

See [LICENSE](LICENSE) for the upstream license and port distribution conditions. Photon is developed by Low-Drag-MC.
