# GameDay Android TV app

The native television application lives in [`android/`](android/README.md). The EPlusTV server
at the repository root is unchanged and can still be run exactly as before; GameDay can also
consume it as a LAN source (`http://<host>:8000`).

- Build and install: [`android/docs/INSTALL.md`](android/docs/INSTALL.md)
- Upstream audit of EPlusTV: [`android/docs/UPSTREAM_AUDIT.md`](android/docs/UPSTREAM_AUDIT.md)
- Why Kotlin instead of embedded Node.js: [`android/docs/ARCHITECTURE_DECISION.md`](android/docs/ARCHITECTURE_DECISION.md)
- Compatibility and what is/isn't validated: [`android/docs/COMPATIBILITY.md`](android/docs/COMPATIBILITY.md)
- Embedded-runtime proof-of-concept record: [`android/docs/PHASE2_RUNTIME_POC.md`](android/docs/PHASE2_RUNTIME_POC.md)

Status: compiles, 62 unit tests pass, Lint has 0 errors, debug and release APKs assemble and
the release APK signature verifies. It has **not** been run on Android hardware from this
build environment, and provider logins/DRM playback are intentionally out of scope.
