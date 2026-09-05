# Flapforge — Roadmap

## What shipped in 0.1.0

M0–M9, one commit each on `rewrite/flapforge` (tagged `v0.1.0`):

| milestone | commit | shipped |
| --- | --- | --- |
| M0 | `7610c2e` | Gradle skeleton, scaled window, 60 Hz loop, per-tick input, menu shell, legacy assets removed |
| M1 | `c51333a` | classic core — physics parity (0 px), gates, content pipeline, Green Fields, determinism fixtures |
| M2 | `d14b161` | boot, settings, synthesised audio, i18n (EN/pt-BR), presentation services |
| M3 | `5b25429` | coins, clean-gate streaks, run rewards, crash-safe save with migrations |
| M4 | `224e589` | seven birds, three upgrade trees, unlock graph, purchases and shops |
| M5 | `44da4de` | eight abilities, stat-driven shield and revive, loadout and HUD |
| M6 | `12a47b6` | mid-run modifier drafts, rarity pool, tags and synergies |
| M7 | `cf5b34a` | five worlds, four hazard families, patterns, ambience and rule cycles |
| M8 | `e2ad93f` | challenges, world bosses, achievements, procedural music, OFL font and accessibility |
| M9 | *(this release)* | hard/nightmare tiers, daily challenge, prestige, seeded runs, attract mode, MetaSim balance, release packaging |

Also in 0.1.0: `speed_run_1` retuned 40 → 30 gates (BALANCING.md §11.1) and the `glide_1`
retune (§12) — both measured, both recorded.

## M10 — Android port (done; attached to the 0.1.0 release)

M10 shipped after the `v0.1.0` tag as commits on `rewrite/flapforge` (`be5cb13` P0 spike,
`a1b824b` P1 shims, `0e9bff2` P2 host seam and transform, `41fd006` P3 gestures and lifecycle,
then the P4 CI/docs pass) and joins the same release: the APK is attached to `v0.1.0` as
`Flapforge-0.1.0-android.apk` — no new tag and no version bump, because desktop behaviour did
not change (the host seam is a refactor behind the same hash) and the published hash
(`eaaa01685261a433`) is untouched. The port is
libGDX-free: a build-time source transform (`android/build.gradle`) rewrites `java.awt.*`,
`javax.sound.sampled.*` and `javax.imageio.*` into three census-bound shim packages over
`android.graphics` / `android.media`, guarded by a double integrity gate with a self-test; a
host seam (`GameHost` / `AppWindow` / `InputBridge`) lets the unchanged game run on a
`SurfaceView` presenter with touch gestures (tap = flap, drag = wheel, second finger or the
HUD badge = ability, back = `Esc`), the activity lifecycle mapped onto the game's own
focus/iconify/close events, hold-to-flap on by default, a launcher icon generated from the
procedural desktop icon, and 201 tests (JUnit 4, most under Robolectric). `minSdk 33`,
`compileSdk 36`, debug-signed for sideloading. Details: `docs/ARCHITECTURE.md` ("Android
port") and `docs/DEVELOPMENT.md` ("Android build").

## Deferred, with next-step anchors

* **Leaderboards** — needs online infrastructure 1.0 does not have. `runHistory` (capped
  100, in the save) already records the local data a future board would upload.
* **Challenge sharing / community packs** — `ChallengeDef` is already strict-bound JSON;
  an export/import format (one file per challenge, validated by the existing
  `ContentValidator` rules) is a small follow-up. Moderation is the open question.
* **Mod packs** — a `~/.flapforge/mods/*.json` overlay follows naturally from the
  data-driven loader (`ContentLoader` reads each file independently); not built in 1.0 to
  keep the validator's guarantees closed. The `assets/manifest.json` override path is the
  safe half of this and already ships.
* **New Game+** — superseded by tiers (`hard`/`nightmare`) plus prestige; revisit only if
  the prestige loop feels stale.
* **"Upgrade materials" as a second currency** — the plan mapped them to coins to keep the
  economy provable; `Wallet` is map-keyed by `economy.json.currencies`, so a second
  currency is a data change plus sink design.
* **Licensed music tracks** — the procedural `MusicSequencer` ships; the mixer already has
  a WAV streaming voice for future tracks (no OGG/MP3 by design).
* **Endless difficulty tiers** — the `tierGenerator` key is reserved in `difficulty.json`;
  needs a per-generated-tier balancing pass (`MetaSim`) before it can ship.
* **Original art and SFX packs** — the procedural default ships; original assets land
  through `assets/manifest.json` entries (id → path, licence, provenance) with zero code
  changes. `IconExport`/`AssetValidator` already validate the manifest.
* **Android: stop the loop while the activity is stopped** — `onStop` only queues
  `Iconified(true)`; the loop thread keeps ticking in the background while the presenter
  skips. `MainActivity`'s javadoc marks stopping it as the next step; the seams are the queue
  (`CloseRequested`) and a restart on `onStart`.
* **Android: store distribution** — the APK is debug-signed and sideloaded by decision. A
  store listing needs a release keystore and `minifyEnabled` with keep rules for the Gson
  record reflection.
* **Android: keyboard and gamepad** — the host reads no key events; a Bluetooth keyboard does
  nothing. `AndroidInputBridge.key(int)` already turns a key code into the queue's press/release
  pair, so a `View.OnKeyListener` mapping Android key codes onto `input.Keys` is the whole job.

## Beyond 1.0 (candidate order)

1. **Quality-of-life pack** — run-seed sharing UI, replay of `runHistory` entries, more
   statistics graphs (data already captured).
2. **Challenge export** — smallest new-content lever: JSON out, JSON in, validator in the
   middle.
3. **Second currency** — only with a sink design that `MetaSim` can measure end to end.
4. **Endless tiers** — after a `MetaSim` extension that treats generated tiers as content.
5. **Online anything** — leaderboards first, only if the save/policy story is solved.
