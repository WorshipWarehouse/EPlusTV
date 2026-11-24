# Third-party notices

## EPlusTV

GameDay implements the M3U/XMLTV interchange conventions and the conceptual guide/segment
model of **EPlusTV**, which is MIT licensed:

- Upstream: <https://github.com/tonywagner/EPlusTV>
- This repository: <https://github.com/WorshipWarehouse/EPlusTV>
- Licence text: `../LICENSE` (MIT), retained unchanged.

No EPlusTV source file is copied into `android/`. The guide parser, cache, TV interface and
player are original Kotlin implementations; the audit and the shared concepts are documented
in `docs/UPSTREAM_AUDIT.md`.

## AndroidX / Media3

- AndroidX Core, AppCompat, RecyclerView, Lifecycle, Media3 — Apache License 2.0.
- Kotlin, kotlinx.coroutines, kotlinx.serialization — Apache License 2.0.

Dependency versions are pinned in `app/build.gradle.kts`; run
`./scripts/verify-apk.sh` after a build to confirm the packaged artifact.
