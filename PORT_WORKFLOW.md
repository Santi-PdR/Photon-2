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
- **Forge lifecycle and editor UI:** use `minecraft-forge-engineering` for registries, sided code, Forge APIs, networking, and version-specific runtime behavior. Use `santi-ui-engineering` when a Photon editor screen, panel lifecycle, or interaction is the subject of the bug. These are targeted references under `santi-minecraft-master`, not reasons to load every Minecraft skill at once.
- **Missing or incorrect assets:** `santi-asset-forensics` traces the exact resource from project/source through registration and packaging into the final JAR.
- **Crashes and regressions:** `santi-debug-efficient` narrows the first meaningful failure, a concrete root-cause hypothesis, the smallest fix, and a fitting validator.
- **Build and deliverables:** `verification-build` chooses focused checks before the full relevant build; `santi-artifact-verification` checks packaged contents, metadata, dependencies, and SHA-256.
- **GitHub state:** `santi-github-engineering` checks the actual remote branch, Actions runs, PRs, and published artifacts. Use targeted `gh` queries first; when CLI access is unavailable, use the connected GitHub tools for repository, branch, commit, combined-status, PR, and supported workflow/job/artifact reads. The connector also exposes write operations (files, refs, issues, PRs, reviews, and workflow reruns): treat those as mutations and use them only when the task explicitly calls for that specific GitHub action. For Photon source changes, edit the checkout and publish through the normal Git workflow so the diff and local validation remain reviewable. Check each endpoint's scope and pagination before interpreting an empty result; never infer CI state from local success.
- **Large investigations:** `santi-large-task` may divide only independent, bounded workstreams when the active collaboration policy permits delegation. Keep integration, final validation, and user-facing status in the primary workflow. Otherwise proceed sequentially.
- **Long-running work:** use `santi-handoff` at meaningful milestones so a resumed task can continue from verified state without rereading whole logs or audits. `santi-token-efficient` supports narrow searches and compact evidence, without skipping required validation.

Use `rg` and focused shell reads for source, Java 17 and Gradle for the Forge 1.20.1 target, and `javap`/archive inspection for binaries. The current task also has `mcp__cua_repl` for computer use, a connected GitHub app with read and write operations, and Blockbench MCP tools. Apply them selectively:

- Use GitHub app reads when `gh` cannot provide the needed remote state; keep code edits in the checkout and use Git for publishing. Do not use the connector's write endpoints unless the task asks for that external mutation.
- Use CUA only to inspect/control a UI the user has already opened and only when requested. The standing instruction is **do not open Minecraft**, so CUA must not launch or navigate into a Minecraft instance. A provided log or targeted source-level probe is the current runtime-evidence substitute.
- Use Blockbench MCP only when the issue concerns an actual Blockbench model, animation, UV, or texture; Photon `.fxproj` meshes and shader/material paths should first be traced in their own formats and loaders.
- `open_in_codex` can show a changed source or audit file when review in the app helps. Image generation, unrelated app connectors, automation, and plugin installation do not help this port unless a concrete task changes that.

Supplied screenshots and videos can establish visual expectations and observed UI state; they do not prove the port's runtime behavior. Use web lookup only when the local original/reference or authoritative external documentation is missing. Do not install recommended external plugins just because they appear in the catalog; none is needed for the current source, build, artifact, or GitHub checks.

## Current verification boundary

The current audit records static comparison with the official Photon source/artifact, Java 17 tests and artifact inspection. The latest Photon JAR is installed in `test-1`, but the available client log predates it. Therefore editor open/save/reopen, custom-particle rendering, the refreshed modpack launch, multiplayer, and dedicated-server behavior remain unverified. Do not raise the completion estimate based on source or build checks alone.
