# GameDay — Upstream EPlusTV audit

Audited tree: `WorshipWarehouse/EPlusTV` @ `8fbc1fc` (master), version **4.16.6**.
Upstream project: <https://github.com/tonywagner/EPlusTV>. Licence: **MIT** (repository `LICENSE`, retained).

## 0. What this repository actually contained before this work

The mission brief describes an existing GameDay Android project with update
infrastructure, diagnostics, security checks, recovery, remote navigation, deployment
scripts and an automated test suite. **That code is not in this repository.** What exists
here is the EPlusTV server (TypeScript/Node) plus two GitHub workflows:

| Path | Contents | Treatment |
| --- | --- | --- |
| `index.tsx`, `services/**`, `views/**` | EPlusTV Node server | Read, untouched |
| `Dockerfile`, `entrypoint.sh` | Alpine image running `npm start` | Untouched |
| `.github/workflows/push-docker-hub.yml` | Docker image publication | Untouched |
| `.github/workflows/release.yml` | Creates a GitHub release on tag push | Untouched |
| tests, diagnostics, Android code | **absent** | Stated plainly rather than invented |

There are no other branches in this clone and no prior Android runtime experiment to
preserve. The new application therefore lives entirely in `android/` and adds tests,
diagnostics and update infrastructure itself; nothing upstream was deleted or rewritten.

## 1. Runtime and packaging

| Question | Finding |
| --- | --- |
| Required Node.js version | **18** (`.nvmrc`), `@types/node@16` in devDeps |
| Runtime entry | `ts-node -r tsconfig-paths/register index.tsx` — TypeScript/TSX executed directly |
| HTTP framework | Hono 4.6 via `@hono/node-server`, JSX views served as HTML |
| Container base | `alpine:latest`; `apk add nodejs npm su-exec shadow yt-dlp`; `npm ci` |
| Process model | Single process, one HTTP server, `setInterval` timers (guide every 4 h, tokens every 30 min, idle-playlist sweep every 1 min) |
| Shutdown | `SIGTERM`/`SIGINT` → `process.exit(0)` |

## 2. Dependency audit (portability view)

| Dependency | Kind | Android/ARMv7 implication |
| --- | --- | --- |
| `sharp` 0.33.5 | **native** (libvips/`libvips-cpp`) | No nodejs-mobile ABI prebuild; needs NDK cross-compile of libvips for armeabi-v7a |
| `jsdom` 26 | pure JS, heavy | `runScripts: "dangerously"` DOM per provider scrape; large heap footprint |
| `yt-dlp-wrap` 2.3.12 | **external executable** | Requires `child_process.spawn`; nodejs-mobile documents spawn/fork as unavailable on mobile |
| `@seald-io/nedb` | pure JS, file-backed | Needs a writable app-private directory |
| `hls-parser`, `fast-xml-parser`, `xml`, `cheerio`, `crypto-js`, `jwt-decode`, `lodash`, `moment`, `moment-timezone`, `axios`, `ws`, `sockette` | pure JS | Portable in principle |
| `htmx.org`, `@picocss/pico`, `htmx-toaster` | browser assets | Irrelevant to a native TV UI; server serves `/node_modules/*` statically |

External executables: **`yt-dlp`** only (installed by the Dockerfile, used for YouTube
channels/streams). Filesystem assumptions: `config/` relative to `process.cwd()`
(`services/config.ts`), holding `entries.db`, `schedule.db`, `providers.db`, `misc.db`
(NeDB JSON lines) and `config/debug/` capture files when `DEBUGGING=true`.

## 3. Network behaviour

- Listens on `0.0.0.0:${PORT}`, default **8000** (`services/port.ts`). Plain HTTP unless
  `SSL_CERTIFICATE_PATH`/`SSL_PRIVATEKEY_PATH` are set.
- Outbound HTTPS to provider APIs and CDNs; outbound requests carry spoofed
  browser/TV user agents from `services/user-agent.ts`.
- Segment proxy: `/channels/:id/*.ts|.key|.vtt|.m4i` and `/chunklist/:id/*.m3u8` re-serve
  provider HLS through the server with provider headers attached.
- Memory: segment cache capped at **128 MiB** (`services/caching.ts`), 3-minute promise
  TTL for segments (30 s for Fox One keys). No Node heap limit is configured; jsdom
  instances and `sharp` add unaccounted heap.

## 4. Existing API interfaces (reused as a contract)

| Route | Purpose |
| --- | --- |
| `GET /channels.m3u` | Numbered channel playlist (`tvg-id`, `tvg-chno`, `tvg-name`) |
| `GET /event-channels.m3u` | One channel per live/upcoming event |
| `GET /linear-channels.m3u` | Dedicated linear channels (optional) |
| `GET /xmltv.xml`, `/linear-xmltv.xml` | XMLTV EPG (`channel` + `programme`) |
| `GET /channels/:id.m3u8` | Rewritten HLS master playlist for channel `id` |
| `GET /chunklist/:id/:name.m3u8` | Rewritten media playlist |
| `GET /channels/:id/:part.ts/.key/.vtt/.m4i` | Proxied segment, AES key, subtitle fragment |
| `POST /rebuild-epg`, `/reset-channels`, `/start-channel`, `/num-of-channels`, `/linear-channels`, `/proxy-segments`, `/xmltv-padding`, `/hide-studio`, `/event-filters` | Web-UI settings |
| `GET /providers/...` | 22 provider login/config pages |

Configuration is a mix of environment variables (`PORT`, `START_CHANNEL`,
`NUM_OF_CHANNELS`, `LINEAR_CHANNELS`, `PROXY_SEGMENTS`, `MAX_RESOLUTION`, `XMLTV_PADDING`,
`BASE_URL`, `DEBUGGING`, `SSL_*`, one variable per provider/network) and NeDB rows written
by the web UI.

Program guide: XMLTV programmes are collected per provider, deduplicated, filtered, then
"linearised" into channel slots (`schedule.db`) starting at `START_CHANNEL`. XMLTV channel
IDs are `${channelNumber}.eplustv`; M3U `tvg-id` matches those IDs.

## 5. Provider authorisation audit

22 providers are wired in `services/providers/index.ts`. Mechanisms observed:

- **OAuth / token endpoints** with provider `client_id`s and, in several cases,
  `client_secret`s embedded in the TypeScript source (for example `services/nfl-handler.ts`
  splits `CLIENT_KEY`, `TV_CLIENT_SECRET`, `CLIENT_ID` into character arrays).
- **Generated device identity**: random UUIDs / visitor MIDs / hex codes are minted per
  session (`getRandomUUID`, `getRandomVisitorMid`, `getRandomHex`).
- **User-agent spoofing**: hard-coded Android TV / mobile app user agents per provider.
- **Cookie sessions** scraped or posted through `axios`; some providers are driven through
  `jsdom` with `runScripts: "dangerously"` to execute provider JavaScript.
- **YouTube-backed events** through the external `yt-dlp` binary.
- **Encryption, not DRM removal**: `PlaylistHandler` proxies AES keys and segments inside an
  authorised session, and inlines `EXT-X-KEY` payloads as `data:` URIs for players that
  cannot call back. There is no key-extraction or DRM-stripping code.

Assessment against the mission's own constraints:

1. Porting the token/device-identity layer to a new client would reproduce **device identity
   spoofing** and re-publish **embedded client secrets**, which Phase 3 explicitly forbids.
2. Provider terms and bot protection are tied to those identities and UAs; a native client
   cannot honestly claim to be another vendor's app.
3. The providers are the part of EPlusTV most likely to break on any given week (the git log
   is dominated by provider fixes), so copying them into an APK would create a permanent
   maintenance obligation with no authorised path.

**Decision: the provider authentication layer is not ported.** GameDay consumes streams the
user is already entitled to (their own M3U/HLS endpoints, or their own EPlusTV gateway on
the LAN). Section 6 of `ARCHITECTURE_DECISION.md` records the consequences.

## 6. Android / ARMv7 portability of the Node server

| Requirement | Verdict |
| --- | --- |
| Node 18 features used (`node:https`, `fs`, `crypto`, timers, `Buffer`) | Supported by NodeMobile 18.x |
| `child_process.spawn` (`yt-dlp`) | **Unsupported** on nodejs-mobile (documented permission limits) |
| `process.exit()` (signal handler) | **Unsupported** on mobile per nodejs-mobile FAQ |
| `sharp` native addon | Requires NDK cross-compilation against the NodeMobile ABI |
| `jsdom` heap | Fits only if the process is given a large share of 1.5 GB |
| armeabi-v7a | NodeMobile **v18.20.4** still ships `armeabi-v7a`, but its Android guide marks the ABI **"(discontinued)"** |
| Minimum platform | NodeMobile requires **API ≥ 24** — API 25 is acceptable |

So the server *could* start on an API 25 ARMV7 device, but not without dropping `yt-dlp`
providers, patching the shutdown path, cross-compiling `sharp`, and accepting a deprecated
ABI plus a second runtime and its heap inside a 1.5 GB device.

## 7. What was reused

- The M3U/XMLTV contract and channel-ID conventions (so an EPlusTV gateway drops in as a
  source without translation).
- The concept of a bounded guide snapshot and idle-safe refresh (re-implemented with a hard
  programme cap and staleness rules).
- MIT licence and attribution retained; see `NOTICE.md`.

## 8. What was deliberately not reused

Provider logins, token/device spoofing, the Node HTTP server, the HTML/HTMX UI, `sharp`
thumbnails, `jsdom` scraping, `yt-dlp` extraction, and the segment-proxy server (ExoPlayer
talks to the provider CDN directly, which avoids double buffering).
