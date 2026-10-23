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

Start broad or resumed work with `santi-task-router`, then identify the project using `santi-project-router`. When Photon-specific history or architecture matters, load only the Photon project summary and this repository's current handoff through `santi-project-intelligence` / `santi-handoff`; verify their claims against the checkout. Use `santi-task-contract` to keep large investigations bounded by an objective, scope, validator, repair policy, and completion condition. `santi-minecraft-master` is the umbrella workflow; select only the relevant specialized skills below.

- **Port behavior and upstream parity:** `minecraft-port-fidelity` defines the original-to-Forge comparison and deviation record. Use `reverse-engineering` for original JAR, bytecode, serialized-format, or dependency-API questions.
- **Missing or incorrect assets:** `santi-asset-forensics` traces the exact resource from project/source through registration and packaging into the final JAR.
- **Crashes and regressions:** `santi-debug-efficient` narrows the first meaningful failure, a concrete root-cause hypothesis, the smallest fix, and a fitting validator.
- **Build and deliverables:** `verification-build` chooses focused checks before the full relevant build; `santi-artifact-verification` checks packaged contents, metadata, dependencies, and SHA-256.
- **GitHub state:** `santi-github-engineering` checks the actual remote branch, Actions runs, and published artifacts with targeted `gh` queries. Never infer CI state from local success.
- **Large investigations:** `santi-large-task` may divide only independent, bounded workstreams when the active collaboration policy permits delegation. Keep integration, final validation, and user-facing status in the primary workflow. Otherwise proceed sequentially.
- **Long-running work:** use `santi-handoff` at meaningful milestones so a resumed task can continue from verified state without rereading whole logs or audits. `santi-token-efficient` supports narrow searches and compact evidence, without skipping required validation.

Use `rg` and focused shell reads for source, Java 17 and Gradle for the Forge 1.20.1 target, `javap`/archive inspection for binaries, and `gh` for GitHub state. Use web lookup only when the local original/reference or authoritative external documentation is missing. Supplied screenshots and videos can establish visual expectations and observed UI state; they do not prove the port's runtime behavior. UI automation is not a substitute for permission to launch Minecraft. The active instruction for this project is **do not open Minecraft**, so runtime/editor/render checks must wait for a user-provided log or an explicit later change to that instruction. Do not install recommended external plugins just because they appear in the catalog; none is needed for the current source, build, artifact, or GitHub checks.

## Current verification boundary

The current audit records static comparison with the official Photon source/artifact, Java 17 tests and artifact inspection. The latest Photon JAR is installed in `test-1`, but the available client log predates it. Therefore editor open/save/reopen, custom-particle rendering, the refreshed modpack launch, multiplayer, and dedicated-server behavior remain unverified. Do not raise the completion estimate based on source or build checks alone.
