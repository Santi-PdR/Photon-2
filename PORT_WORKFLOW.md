# Photon 2 Forge Port Workflow

This document defines the evidence and validation workflow for the Photon 2 Forge 1.20.1 port. It uses the repository's original Photon 26.2 source and artifacts as the behavior reference, and the refreshed `test-2` modpack as a read-only compatibility reference.

## Scope and boundaries

- Change Photon source, Photon resources, Photon-specific build configuration, tests, and Photon documentation only.
- Treat `test-2` and its installed mods as read-only references. Do not copy, remove, upgrade, or edit other mods.
- Do not launch Minecraft unless the user explicitly changes the current instruction not to open it.
- Preserve unrelated user changes in the working tree. Never reset, force-push, or discard them.
- Keep source behavior, Forge compatibility adaptations, and unresolved differences distinguishable in the audit.

## Investigation sequence

1. **Set the target.** Confirm repository remote, branch, Minecraft/Forge/Java versions, runtime dependencies, and relevant client/server side.
2. **Reproduce narrowly.** Identify the exact failing project, log error, resource, shader, or user-visible behavior. Filter logs to the first meaningful error and surrounding call path.
3. **Map against the original.** Record the original artifact/source revision and hash when relevant. Compare the corresponding code, serialized format, shader stages, resources, and lifecycle. Mark each conclusion as observed, strongly inferred, or unverified.
4. **Trace data and resources end to end.** Follow project data from file parsing through LDLib accessors and Photon runtime objects; follow assets from ResourceLocation and registration through shader/resource loading to the packaged JAR. Check namespace, case, metadata, sampler/uniform bindings, and dependency APIs.
5. **Make the smallest Photon-only fix.** Preserve identifiers and observable behavior. Keep version/API adaptations separate from behavior changes and document necessary deviations.
6. **Validate in layers.** Run focused Java 17 tests or static probes first, then the relevant full build. For resource or runtime failures, inspect the built JAR and its SHA-256. Check GitHub Actions for the pushed commit. A successful compile, test suite, or client startup does not by itself prove editor, visual, multiplayer, or dedicated-server behavior.
7. **Report the boundary of evidence.** State exactly what passed, what artifact was produced, what CI reported, and what still requires a user-provided log or an explicitly authorized runtime check.

## Tool and skill routing

- **Port behavior:** `minecraft-port-fidelity` establishes the original-to-Forge comparison and deviation record.
- **Binary, format, and API questions:** `reverse-engineering` guides provenance, targeted bytecode/format inspection, confidence, and safe probes.
- **Missing or incorrect resources:** `santi-asset-forensics` traces the exact path from source and registration to the final JAR.
- **Crashes and regressions:** `santi-debug-efficient` narrows the failure, hypothesis, fix, and validator.
- **Build and release files:** `verification-build` and `santi-artifact-verification` check the smallest useful build, packaged contents, and artifact identity.
- **Remote repository state:** `santi-github-engineering` uses targeted GitHub queries and verifies CI before reporting it.
- **Long-running work:** `santi-handoff` keeps a compact state record at meaningful milestones; do not duplicate full logs or source into it.

Use shell search and targeted reads for repository work, Gradle with Java 17 for this Forge target, and `gh` for remote branch/CI state. Use browser lookup only when the original source, official artifact, or current external documentation is missing locally. Image/video inspection can support a visual comparison when supplied, but cannot replace a runtime check. The currently recommended external plugins are not needed for this workflow; do not install them unless a concrete task requires one.

## Current verification boundary

The current audit records static comparison with the official Photon source/artifact, Java 17 tests and artifact inspection. The latest Photon JAR is installed in `test-1`, but the available client log predates it. Therefore editor open/save/reopen, custom-particle rendering, the refreshed modpack launch, multiplayer, and dedicated-server behavior remain unverified. Do not raise the completion estimate based on source or build checks alone.
