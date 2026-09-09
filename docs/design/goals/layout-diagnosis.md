# Forge / responsive layout architecture diagnosis

## Current state

This is **not a new Forge build**. The Forge already exists in `ui/screens/UpgradeTreeScreen.java:88-132`, `ForgeScene`, `ForgeArt`, `ForgeNodeCard`, `progression/UpgradeManager`, and the shipped three-tree/18-node content. The reported compression is a presentation/layout defect, not missing progression content.

The fixed logical design is `420x640`: `core/Playfield.java:11-14`. It is enforced by `render/Viewport.java:73-82` (`min(window/420,height/640)`) and by `ScreenManager` handing screens a post-transform logical `Graphics2D` (`ui/ScreenManager.java:563-578`). `Screen` receives no viewport or insets (`ui/Screen.java:27-35`).

Desktop path: `GameWindow.create` makes a `Frame` + `Canvas`, preferred size `420*scale x 640*scale` (`app/GameWindow.java:128-159`); default scale is the largest integer fitting usable height minus 48 px (`:102-117`). `GameApplication` constructs the viewport from the actual canvas (`app/GameApplication.java:581-595`). `BufferStrategyPresenter.paint` fills the physical canvas, publishes overscan, then applies translate/scale/clip (`app/BufferStrategyPresenter.java:86-92`; `render/Viewport.java:289-299`). Thus screen painting is logical coordinates after `(offsetX,offsetY)` and scale; no screen sees physical dimensions.

Android path: `MainActivity` installs a full-screen `GameSurfaceView` (`android/MainActivity.java:134-156`), and starts only after the first positive `surfaceChanged` size (`:214-221`). `GameSurfaceView` reports raw surface width/height (`android/GameSurfaceView.java:166-180`); `SurfacePresenter` uses the same viewport/presenter sequence (`android/SurfacePresenter.java:103-109`). Touch coordinates are surface pixels and are mapped by the same viewport in `ScreenManager` (`android/AndroidInputBridge.java:20-31`, `ScreenManager.java:558-560`). Android hides system bars (`MainActivity.java:261-270`) and opts into cutouts (`:141-150`), but registers no `WindowInsets` listener and computes no safe rectangle: **safe-area handling is absent**. The cutout comment's claim that the interactive field is clear is not an inset guarantee.

`Overscan` only extends cosmetic clipping/background rows (`render/Overscan.java:7-15,39-47`); it does not move controls. `BackgroundRenderer` uses fixed logical bands including ground at 598/42 (`:69-74` and `Playfield.java:16-18`), and `CloudLayer` spawns in logical y=20..213 (`render/CloudLayer.java:45-49`). `Fonts.setTextScale` is an independent global font-size multiplier (`render/Fonts.java:62-89`), not layout adaptation.

## Letterbox calculation

For portrait surfaces at 1080 px wide, scale is `1080/420 = 18/7 = 2.571428`; the fitted logical rectangle is `1080 x 1645.714`. Values below are physical pixels (rounded).

| aspect | surface | scale | fitted rectangle | waste |
|---|---:|---:|---:|---:|
| 16:9 | 1080x1920 | 2.5714 | 1080x1646 | 137 top/bottom |
| 18:9 | 1080x2160 | 2.5714 | 1080x1646 | 257 top/bottom |
| 19.5:9 | 1080x2340 | 2.5714 | 1080x1646 | 347 top/bottom |
| 20:9 | 1080x2400 | 2.5714 | 1080x1646 | 377 top/bottom |
| 21:9 | 1080x2520 | 2.5714 | 1080x1646 | 437 top/bottom |
| 4:3 tablet | 1080x1440 | 2.25 | 945x1440 | 0 vertical; 67.5 left/right |

Therefore the `582..640` nav (`ui/component/SectionNav.java:27-31`, also `MainMenuScreen.java:267-283`) is inside the fitted logical rectangle. On a 1080x2400 phone its physical bottom is about y=2023 (`377 + 640 × 2.5714`), leaving about 377 px below it; the top is similarly about 377 px from the physical top. There is no game status bar above it; Android system bars are hidden, and desktop decorations are outside the canvas.


## Seam and data flow

**Seam:** the first implementation seam is `app/GameApplication.java:592-595`, where the fixed `Viewport` is created and passed to `ScreenManager`; rendering then enters `app/BufferStrategyPresenter.java:86-92` / `android/SurfacePresenter.java:103-109`. The responsive UI seam should be a UI-owned `LayoutMetrics` snapshot computed from the viewport's actual/safe surface and exposed through `ScreenManager` (or a screen/layout context), not a new screen and not progression/content. `ui/Screen.java:27-35` currently has the minimal render signature and must gain a metrics access path without making core/gameplay aware of AWT.

```text
src/main/resources/data/*.json
        -> content.defs records (StrictBinder)
        -> content.GameContent
        -> progression services (UpgradeManager, etc.)
        -> progression.RunLoadout
        -> gameplay/stats/StatSheet
        -> gameplay/Simulation
        -> render/UI (only the final state is displayed)

responsive work: Viewport/presenter -> safe viewport -> ui.LayoutMetrics
                 -> ScreenManager -> SectionNav + Goals/Forge/MainMenu bands
content/progression/gameplay/stat/simulation links: UNCHANGED
```

The Forge screen reads bound tree definitions/profile through `GameContent` and `UpgradeManager`, but this layout change does not alter that chain, save data, or run loadout/stat resolution.

## Current hardcoded 640-space bands

## Current state: Goals uses tab 48..76, view 86..578, footer 584..626 (`GoalsScreen.java:75-110`); Forge uses header 0..100, tabs 100..140, tree 140..330, detail 330..582, nav 582..640 (`UpgradeTreeScreen.java:61-72,94-118`); Main Menu uses fixed controls at y 8..536 and nav 582..640 (`MainMenuScreen.java:98-106,241-283`). These all assume 640 logical pixels. This is a real Forge screen, not a dead implementation; `Overscan` is the dead-for-interaction part of the attempted tall-screen fix.

## Root cause

The viewport preserves aspect ratio and centers a fixed 420x640 surface, while all interactive screens—including SectionNav—remain fixed inside that surface; at 1080x2400 this produces a 1080x1646 game rectangle and ~377 px dead bands above and below. Overscan paints those bands but cannot make UI responsive.

## Recommended architecture

- Keep `Playfield` and simulation unchanged; add a UI-only `ui/layout/LayoutMetrics` value object owned by `ScreenManager`/`GameContext`.
- Compute `actual viewport -> safe viewport -> logical layout metrics`: physical safe insets supplied by Android `WindowInsets` and zero/known insets on desktop; never put AWT/Android types in core or progression.
- Preserve logical artwork units, but choose a bounded uniform UI scale and recomposition policy; do not stretch icons, type, or cards. Expose `contentTop()`, `contentBottom()`, `contentHeight()`, and `navBounds()` to screens.
- Make `SectionNav` reserve its measured 58-logical-unit band at the physical safe bottom, with usable touch bounds; it must no longer own literal `TOP=582`.
- Give `Screen`/screen context the immutable metrics snapshot; `GoalsScreen`, `UpgradeTreeScreen`, and `MainMenuScreen` recompute bounds on resize/language/metrics changes. Flexible content is between header and reserved nav.
- Android host owns insets and touch conversion; viewport maps physical safe coordinates consistently. Desktop presenter remains `Frame`/`Canvas`/`BufferStrategy`.
- Keep headless rendering deterministic: a `LayoutMetrics.forSurface(420,640,Insets.ZERO)` fixture preserves existing geometry; add pure geometry tests for every table row and BufferedImage render tests at Playfield size.

## Migration and ownership

1. **ff-architect (design only):** this file; lead approves metrics API. Then in sequence, **ff-nav** owns `ui/layout/LayoutMetrics.java`, `ui/component/SectionNav.java`, and `ui/Screen.java` API change; no parallel edits to those files.
2. **ff-android** (UNVERIFIED agent label; assign explicitly) owns `android/MainActivity.java`, `GameSurfaceView.java`, `AndroidInputBridge.java` for insets/safe-coordinate events, after metrics API.
3. **ff-ui** exclusively owns `GoalsScreen.java`, `UpgradeTreeScreen.java`, `ForgeNodeCard.java`, `ForgeArt.java`, `ForgeScene.java`, and `MainMenuScreen.java`; migrate one screen at a time after nav. Existing Forge/Goals ownership remains ff-ui.
4. **ff-qa** owns only `src/test/...` layout/viewport/render tests and must verify `build`, `contentCheck`, both languages, Android emulator, and hash `eaaa01685261a433`.
5. **ff-tree/ff-gameplay/ff-economy/ff-persistence/ff-l10n** touch no files for this UI-only change. No JSON, save shape, progression, `RunLoadout`, `StatSheet`, or `Simulation` link changes.

## Risks / non-negotiables

Do not alter simulation geometry, seeded streams, save v1, prices, or logical Playfield constants. Avoid double scaling (`Viewport` plus `Fonts`), clipping touch targets under cutouts, and Android-only assumptions. `CloudLayer`'s production unseeded `new Random()` (`render/CloudLayer.java:71-74`) is cosmetic but **UNVERIFIED** against the project determinism policy; fix only in a separate render-risk change. Android emulator verification is mandatory because Robolectric will not expose device inset/record issues.
