# Upstream artifacts (not on the build path)

`flatDir { dirs 'libs' }` in `build.gradle` only scans `libs/` itself, so nothing in this folder is
compiled against or embedded. It keeps the pristine third-party builds that the jars in `libs/`
were derived from, for provenance and for redoing the derivation after an upgrade.

| File | What it is | Derived jar in `libs/` |
| --- | --- | --- |
| `ldlib2-2.2.39.a+forge.1.20.1.jar` | LDLib2 2.2.39.a for Forge 1.20.1 as published upstream. Jar-in-Jar: `kotlin-stdlib-2.1.20.jar` (identifier `org.jetbrains.kotlin:kotlin-stdlib`, JPMS module `kotlin.stdlib`) and `mixinextras-forge-0.3.5.jar`. | `ldlib2-2.2.39.a.jar`: byte-identical except that the 172 Kotlin classes have every `kotlin.*`/`kotlinx.*` reference relocated to `com.lowdragmc.photon.internal.embedded.*` and the loose standard library is replaced by `photon-kotlin-runtime-2.2.21.jar` (stdlib, reflect, coroutines, serialization, all under that package; Jar-in-Jar identifier `com.lowdragmc.photon:photon-kotlin-runtime`). |

Why the upstream jar must not be shipped: Photon embeds LDLib2 by Jar-in-Jar, and a nested
`kotlin-stdlib` becomes its own JPMS module `kotlin.stdlib` that exports `kotlin.*`. Kotlin for Forge
ships the same packages inside its module `thedarkcolour.kotlinforforge`; two modules exporting one
package is a split package and the game refuses to start for every Kotlin mod on the instance:

```
java.lang.module.ResolutionException: Modules thedarkcolour.kotlinforforge and kotlin.stdlib
export package kotlin.contracts to module create_pillagers_arise
```

Jar-in-Jar version selection cannot reconcile that (the two carry different identifiers), so the
only distribution that coexists with Kotlin for Forge — and with any other mod embedding a Kotlin
runtime — is the relocated one. The CI step "Verify the distributable embeds the relocated Kotlin
runtime" fails the build if a loose `kotlin/` package root ever comes back.
