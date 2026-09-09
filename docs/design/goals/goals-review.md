# Adversarial review: M11 responsive viewport and Goals

## Scope

The review covered the following shipped implementation, resource, and test files in the working tree.

### Modified implementation and resource files

- `/home/michel/Documents/Jogos/Flapforge/android/src/main/java/io/github/michelbr84/flapforge/android/AndroidHost.java`
- `/home/michel/Documents/Jogos/Flapforge/android/src/main/java/io/github/michelbr84/flapforge/android/MainActivity.java`
- `/home/michel/Documents/Jogos/Flapforge/src/main/java/io/github/michelbr84/flapforge/content/StringKey.java`
- `/home/michel/Documents/Jogos/Flapforge/src/main/java/io/github/michelbr84/flapforge/render/Viewport.java`
- `/home/michel/Documents/Jogos/Flapforge/src/main/java/io/github/michelbr84/flapforge/ui/ScreenManager.java`
- `/home/michel/Documents/Jogos/Flapforge/src/main/java/io/github/michelbr84/flapforge/ui/component/HubHeader.java`
- `/home/michel/Documents/Jogos/Flapforge/src/main/java/io/github/michelbr84/flapforge/ui/component/SectionNav.java`
- `/home/michel/Documents/Jogos/Flapforge/src/main/java/io/github/michelbr84/flapforge/ui/screens/BirdSelectionScreen.java`
- `/home/michel/Documents/Jogos/Flapforge/src/main/java/io/github/michelbr84/flapforge/ui/screens/GoalsScreen.java`
- `/home/michel/Documents/Jogos/Flapforge/src/main/java/io/github/michelbr84/flapforge/ui/screens/MainMenuScreen.java`
- `/home/michel/Documents/Jogos/Flapforge/src/main/java/io/github/michelbr84/flapforge/ui/screens/RunSummaryScreen.java`
- `/home/michel/Documents/Jogos/Flapforge/src/main/java/io/github/michelbr84/flapforge/ui/screens/SettingsScreen.java`
- `/home/michel/Documents/Jogos/Flapforge/src/main/java/io/github/michelbr84/flapforge/ui/screens/ShopScreen.java`
- `/home/michel/Documents/Jogos/Flapforge/src/main/java/io/github/michelbr84/flapforge/ui/screens/StatisticsScreen.java`
- `/home/michel/Documents/Jogos/Flapforge/src/main/java/io/github/michelbr84/flapforge/ui/screens/UpgradeTreeScreen.java`
- `/home/michel/Documents/Jogos/Flapforge/src/main/java/io/github/michelbr84/flapforge/ui/screens/WorldSelectScreen.java`
- `/home/michel/Documents/Jogos/Flapforge/src/main/resources/data/strings/en.json`
- `/home/michel/Documents/Jogos/Flapforge/src/main/resources/data/strings/pt_BR.json`

### New implementation and test files

- `/home/michel/Documents/Jogos/Flapforge/src/main/java/io/github/michelbr84/flapforge/ui/layout/LayoutMetrics.java`
- `/home/michel/Documents/Jogos/Flapforge/src/main/java/io/github/michelbr84/flapforge/ui/layout/SafeInsets.java`
- `/home/michel/Documents/Jogos/Flapforge/src/main/java/io/github/michelbr84/flapforge/ui/screens/goals/GoalsArt.java`
- `/home/michel/Documents/Jogos/Flapforge/src/main/java/io/github/michelbr84/flapforge/ui/screens/goals/GoalsChallengeCarousel.java`
- `/home/michel/Documents/Jogos/Flapforge/src/main/java/io/github/michelbr84/flapforge/ui/screens/goals/GoalsPlayButton.java`
- `/home/michel/Documents/Jogos/Flapforge/src/main/java/io/github/michelbr84/flapforge/ui/screens/goals/GoalsSurface.java`
- `/home/michel/Documents/Jogos/Flapforge/src/main/java/io/github/michelbr84/flapforge/ui/screens/goals/GoalsTabBar.java`
- `/home/michel/Documents/Jogos/Flapforge/android/src/test/java/io/github/michelbr84/flapforge/android/SafeInsetsTest.java`
- `/home/michel/Documents/Jogos/Flapforge/src/test/java/io/github/michelbr84/flapforge/ui/ScreenManagerMetricsTest.java`
- `/home/michel/Documents/Jogos/Flapforge/src/test/java/io/github/michelbr84/flapforge/ui/layout/AspectRatioTest.java`
- `/home/michel/Documents/Jogos/Flapforge/src/test/java/io/github/michelbr84/flapforge/ui/layout/LayoutMetricsTest.java`
- `/home/michel/Documents/Jogos/Flapforge/src/test/java/io/github/michelbr84/flapforge/ui/screens/ResponsiveHubTest.java`
- `/home/michel/Documents/Jogos/Flapforge/src/test/java/io/github/michelbr84/flapforge/ui/screens/ResponsiveMetaTest.java`
- `/home/michel/Documents/Jogos/Flapforge/src/test/java/io/github/michelbr84/flapforge/ui/screens/ResponsiveSecondaryTest.java`

### Design and content-audit material

- `/home/michel/Documents/Jogos/Flapforge/docs/design/goals/goals-content-spec.json`
- `/home/michel/Documents/Jogos/Flapforge/docs/design/goals/goals-economy.md`
- `/home/michel/Documents/Jogos/Flapforge/docs/design/goals/goals-event-wiring.md`
- `/home/michel/Documents/Jogos/Flapforge/docs/design/goals/goals-nav-integration.md`
- `/home/michel/Documents/Jogos/Flapforge/docs/design/goals/goals-persistence.md`
- `/home/michel/Documents/Jogos/Flapforge/docs/design/goals/goals-strings.md`
- `/home/michel/Documents/Jogos/Flapforge/docs/design/goals/goals-tree-audit.md`
- `/home/michel/Documents/Jogos/Flapforge/docs/design/goals/goals-visual-qa.md`
- `/home/michel/Documents/Jogos/Flapforge/docs/design/goals/goals-visual-qa-pass2.md`
- `/home/michel/Documents/Jogos/Flapforge/docs/design/goals/goals-visual-spec.md`
- `/home/michel/Documents/Jogos/Flapforge/docs/design/goals/layout-diagnosis.md`

The pre-existing modified tests `/home/michel/Documents/Jogos/Flapforge/src/test/java/io/github/michelbr84/flapforge/NavBarTest.java` and `/home/michel/Documents/Jogos/Flapforge/src/test/java/io/github/michelbr84/flapforge/ViewportTest.java` were also checked for weakened assertions and disabled execution.

## Verdict

**APPROVE-WITH-FIXES**

The implementation was not acceptable as first inspected: three screens tracked only two derived coordinates and could retain stale geometry when another metric field changed. Those defects were fixed in this review. After the fixes, the required build, content, responsive tests, and pinned deterministic run passed.

## Findings

### CRITICAL

None found.

### HIGH

None remaining.

### MEDIUM

- **MEDIUM — `/home/michel/Documents/Jogos/Flapforge/src/main/java/io/github/michelbr84/flapforge/ui/screens/BirdSelectionScreen.java:406` (fixed).** The resize guard compared only `contentTop()` and `navTop()`, although the requirement is to re-layout whenever the complete `LayoutMetrics` changes. A change to height, width, or any safe-area field that did not alter those two coordinates could leave the bird screen's cached bounds stale, producing misplaced controls or hit targets after an inset/viewport update. The guard now compares `!metrics.equals(laidOut)`, and `relayout()` stores the complete metrics at `/home/michel/Documents/Jogos/Flapforge/src/main/java/io/github/michelbr84/flapforge/ui/screens/BirdSelectionScreen.java:454`.

- **MEDIUM — `/home/michel/Documents/Jogos/Flapforge/src/main/java/io/github/michelbr84/flapforge/ui/screens/UpgradeTreeScreen.java:300` (fixed).** The Forge used the same partial comparison and could keep stale cached layout for a metrics change not represented by its two coordinate checks. This is especially relevant to safe-inset and viewport changes arriving asynchronously on Android. It now compares the full immutable metrics value and records it at `/home/michel/Documents/Jogos/Flapforge/src/main/java/io/github/michelbr84/flapforge/ui/screens/UpgradeTreeScreen.java:328`.

- **MEDIUM — `/home/michel/Documents/Jogos/Flapforge/src/main/java/io/github/michelbr84/flapforge/ui/screens/GoalsScreen.java:1069` (fixed).** Goals had the requested earlier `navTop()` invalidation fix, but the guard still did not express the full-metrics invariant. It now compares `LayoutMetrics` directly, while retaining the `navTop()` portion of the effective behavior. The layout snapshot is kept at `/home/michel/Documents/Jogos/Flapforge/src/main/java/io/github/michelbr84/flapforge/ui/screens/GoalsScreen.java:981`.

### LOW

None found that justified leaving additional churn in the shipped change.

## Fixes applied

- Replaced the partial coordinate snapshot in `/home/michel/Documents/Jogos/Flapforge/src/main/java/io/github/michelbr84/flapforge/ui/screens/BirdSelectionScreen.java:237` with a `LayoutMetrics` snapshot; changed the resize guard at line 406 and snapshot assignment at line 454.
- Replaced the partial coordinate snapshot in `/home/michel/Documents/Jogos/Flapforge/src/main/java/io/github/michelbr84/flapforge/ui/screens/UpgradeTreeScreen.java:217` with a `LayoutMetrics` snapshot; changed the resize guard at line 300 and snapshot assignment at line 328.
- Added the `LayoutMetrics` snapshot to `/home/michel/Documents/Jogos/Flapforge/src/main/java/io/github/michelbr84/flapforge/ui/screens/GoalsScreen.java:203`; changed `ensureLayout()` at line 1069 to compare the whole metrics value, preserving the previously applied `navTop()` invalidation fix.
- Added the missing `LayoutMetrics` import in `/home/michel/Documents/Jogos/Flapforge/src/main/java/io/github/michelbr84/flapforge/ui/screens/GoalsScreen.java:38`.

## Accepted risks

- GUI smoke testing was not run in this pass because it requires an idle real display and is documented as environmentally flaky. Headless responsive tests and all required non-GUI gates were run instead.
- The Android build/emulator path was not run concurrently with the desktop Gradle battery. The Android-specific safe-inset test file was inspected; Android compilation remains a CI/device concern rather than a reason to alter the pure Java implementation without a reproducible failure.
- The existing `java.awt` usage in UI and Android-host packages was left alone. The purity rule applies to the listed simulation/content/persistence packages, not UI rendering; the architecture scan found no new forbidden dependency in those protected packages.
- The existing user profile directory was not used by any command in this review. The pinned run explicitly used `/tmp/ff-hash-home`. A profile directory already present in the environment was not modified by this review.

## Regression evidence

- `git diff --check` — passed with no whitespace errors.
- `./gradlew --offline test --tests 'io.github.michelbr84.flapforge.ui.layout.*' --tests 'io.github.michelbr84.flapforge.ui.screens.Responsive*' --tests 'io.github.michelbr84.flapforge.ui.ScreenManagerMetricsTest'` — **BUILD SUCCESSFUL**.
- `./gradlew --offline build contentCheck` — **BUILD SUCCESSFUL**; `Content check: OK — 0 error(s), 0 warning(s)`.
- `./gradlew --offline fatJar && rm -rf /tmp/ff-hash-home && mkdir -p /tmp/ff-hash-home && java -jar build/libs/flapforge-*-all.jar --headless-run 3000 --seed 42 --home /tmp/ff-hash-home` — **BUILD SUCCESSFUL** and printed exactly:

  `hash=eaaa01685261a433 ticks=3000 gates=36 points=36`

- The first concurrent verification attempt produced a Gradle `NoSuchFileException` for an in-progress binary test result while another Gradle process was active; it was treated as a build-tool concurrency failure, not a code result. The required gate was rerun without that concurrency and passed.
- No save-schema or persistence code was changed. The resource string key sets and Portuguese translations were checked, and no new player-facing literal was found in the Goals UI.
