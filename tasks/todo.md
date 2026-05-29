# Todo: ECF Remote Services showcase

Design spec: `docs/superpowers/specs/2026-05-29-ecf-remote-services-design.md`

## Phase 0 — De-risk (do first)
- [x] Resolve exact ECF 3.14.x bundle versions on Maven Central (Java 8 line)
- [x] Add ECF `<location type="Maven">` block to target platform
- [x] Confirm target platform resolves: `mvn clean verify -pl com.kk.pde.ds.target` (or a probe build)

## Phase 1 — Bundles
- [x] `com.kk.pde.ds.ecf.api` — IRemoteGreet interface bundle
- [x] `com.kk.pde.ds.ecf.host` — RemoteGreetImpl @Component with RSA export properties
- [x] `com.kk.pde.ds.ecf.consumer` — RemoteGreetConsumer @Component + EDEF file + Remote-Service header (+ optional Gogo cmd)

## Phase 2 — Build wiring
- [x] Add 3 modules to root pom `<modules>`
- [x] Add 3 demo bundles + ECF stack to feature.xml
- [x] Add `com.kk.pde.ds.ecf.host.product` + `com.kk.pde.ds.ecf.consumer.product`
- [x] Add run scripts: run-ecf-host.sh/.bat, run-ecf-consumer.sh/.bat
- [x] Wire script/logback copies in distribution antrun

## Phase 3 — Verify
- [x] `mvn clean verify` clean
- [x] Run host + consumer in two JVMs; confirm remote greet returns over socket
- [x] Confirm existing com.kk.pde.ds.product still builds/runs (no regression)
- [x] Correct EDEF properties by inspecting live exported endpoint if import fails

## Phase 4 — Docs
- [x] CLAUDE.md module list + ECF section
- [x] README.md ECF section
- [x] FOR_Kerem.md ECF section

## Review

All phases complete. ECF Remote Services showcase added and verified end-to-end.

**Pinned ECF 3.14.x set (Java-8 BREE, from Maven Central, groupId `org.eclipse.ecf`):**
ecf 3.10.0 · identity 3.9.402 · remoteservice 8.14.0 · remoteservice.asyncproxy 2.1.200 ·
discovery 5.1.1 · sharedobject 2.6.200 · provider 4.9.1 · provider.remoteservice 4.6.1 ·
osgi.services.remoteserviceadmin 4.9.1 · ...remoteserviceadmin.proxy 1.0.101 ·
osgi.services.distribution 2.1.600. Plus Equinox common/registry/concurrent + core.jobs,
and org.osgi:org.osgi.service.remoteserviceadmin 1.1.0.

**What was built:**
- 3 bundles: `com.kk.pde.ds.ecf.api` / `.host` / `.consumer` (DS annotations + checked-in OSGI-INF XML).
- Host exports `IRemoteGreet` via `service.exported.*` properties (ECF Generic server on :3288).
- Consumer imports via static EDEF (`OSGI-INF/remote-service/`) + `Remote-Service` header; `@Reference` binds the proxy; Gogo `ecf:greet`.
- Target platform: new ECF Maven location block.
- 2 products + 4 run scripts (host/consumer × sh/bat); antrun copies logback + scripts; dropped `archive-products` (multi-product attachId clash).
- Feature + root pom modules updated.

**Verification (clean build, two fresh JVMs):**
- `mvn clean verify` → BUILD SUCCESS; main product archives intact (no regression).
- Host exported on :3288; consumer logged `Remote response: Hello, ECF! (served remotely by host)`; host's `Remote Request Handler` thread received the call (~11ms round trip). 0 EventAdmin warnings.

**Bugs hit & fixed:**
1. Missing `asyncproxy` package — surfaced only at product resolution, not compilation.
2. `archive-products` attachId clash with 3 products — removed the goal (antrun handles main archives).
3. EDEF `ecf.endpoint.id.ns` was wrong (`ecf.namespace.generic`) → corrected to `org.eclipse.ecf.core.identity.StringID` by reading the host's live exported endpoint.
4. `pkill -f "...product"` matched nothing (path is cwd, not argv) → matched on `osgi.noShutdown`.

**Docs:** CLAUDE.md (module list + ECF section + tech stack), README.md (ECF section), FOR_Kerem.md (engaging writeup), design spec at `docs/superpowers/specs/2026-05-29-ecf-remote-services-design.md`.
