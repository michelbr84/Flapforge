# Goals — visual QA final (re-sign against the shipped bytes)

Supersedes the sign-off in [goals-visual-qa-pass2.md](goals-visual-qa-pass2.md), which
judged the `build/goals-qa/` set rendered at 12:01 — before `GoalsScreen.java` was last
written (13:22) and `SectionNav.java` (12:28). This pass re-renders from the current
source and re-signs.

Render set: `build/goals-qa/goals-{en,pt_BR}-{640,720,840,933,980}-tab{0..3}.png` — 40
frames, regenerated 14:31 from the working tree. References:
`docs/design/goals/{challenges,achievements,milestones,collections}.png`; canonical
spec: [goals-visual-spec.md](goals-visual-spec.md).

## Harness (throwaway, and how it was validated)

`build/goals-qa/GoalsQaRender.java` — a plain `main` class, compiled with `javac`
against the main tree compiled into `build/vision-classes` (gson from the Gradle cache
on the classpath). No Gradle invocation, no source file touched, no profile directory
written (the rig builds `PlayerProfile.fresh` in memory; `~/.flapforge` mtimes verified
unchanged before and after). It renders `ScreenManager.render(Graphics2D, double)` with
`Viewport.setExtendVertical(true)` + `publishOverscan()`, the `ResponsiveMetaTest`
idiom, at 420xH windows so the frames are 1:1 with the logical surface, and selects tabs
through `screen.tabBar().select(...)`.

The pass-1/pass-2 harness is gone, so the state it rendered had to be reverse-engineered
from the old frames. The old set's profile was: fresh profile + three achievements in
`profile.achievements` (`first_flight` at day 0, `gates_10` at day 1, `centurion` at
day 2 — the card date lines read 2023-11-14/15/16), `xp = 425` (level 4 with 94 of the
133-XP span — the milestone card's "94 / 133 XP"), `level = 4` (the reward-card
counters read the field, e.g. "Level 5 reward: 150 coins" at "4 / 5"). With that state
replicated the harness reproduces the old pipeline **byte-for-byte** outside the nav
band — which is itself the proof that fonts, antialiasing, transition settling and
profile state all match. The harness is self-contained and throwaway; if it should
become a permanent QA tool, route it to ff-ui to adopt.

## What actually changed in the shipped bytes

Differencing the 40 new frames against the backed-up 12:01 set
(`build/goals-qa-pass2-backup/`), with the profile state replicated:

- **Every frame's content above the nav band is pixel-identical** (threshold >8/255,
  zero rows) — all five heights, both languages, all four tabs.
- **The only change is the bottom navigation row**, and it is identical everywhere: two
  diff bands per frame at (navTop..navTop+31) and (navTop+40..navBottom) — e.g. at 933:
  y=879..910 and y=919..932.

Measured nav geometry, old → new (all heights; shown for 640 and 933):

| metric | old | new | mock (goals spec 1.6, logical scale) |
| --- | --- | --- | --- |
| nav band | 582..639 / 875..932 | unchanged | band 11.0%H, UNCERTAIN translucency |
| active gold plate | 584..639 / 877..932 (h=56) | 584..633 / 877..926 (h=50) | h≈58 |
| margin below the active plate | **0 — flush with the screen's last row** | **6 logical px** | **14.3 logical px** (32 mock px at 420/941) |
| inactive buttons' bottom strokes | 895..899 | 892..896 (~3px higher) | buttons sit clear of the edge |

Five buttons, order, glyphs, colours and the pale rim on the active plate are unchanged.

## Verdict

**The shipped Goals screen matches the spec at every height (640/720/840/933/980) and in
both languages, with zero CRITICAL, zero HIGH, zero MEDIUM defects.**

- All eight fixes pass 2 verified (3.1 caption, 1.1 boundary crop, 1.4 date line, 1.5
  coin group, 2.1 track colours, 2.2 pt reward title, 1.2 thumb width, 3.3 flag/star
  glyphs) **carry over unchanged** — the bytes they were verified on are the bytes now
  shipped, everywhere above the nav band, at every height and in both languages. The
  Challenges tab (the do-not-touch tab) is pixel-identical outside the nav band.
- The 13:22/12:28 edits' only visible effect is the **X.2 fix**: the active nav plate no
  longer touches the screen's last row on any surface — the exact defect pass 2 recorded
  ("active gold button's last row = screen's last row"). Verdict: **X.2 resolved as an
  improvement, downgraded MEDIUM → LOW**: the delivered margin is 6 logical px against
  the mock's 14.3. Reasons it is a LOW and not a reopen: the defect's essence (flush
  plate) is gone at every height; the nav band is a game-wide shared `SectionNav`
  component whose internal metrics are not Goals-owned (the icon-only divergence X.1 was
  already accepted on the same grounds); and the mock's band is proportionally taller
  than the shipped one (≈78 vs 58 logical), so its 14.3px interior margin does not map
  1:1.
- Intentional divergence **X.1** (icon-only nav) stands, unchanged.
- The five pass-1 LOWs carry unchanged (0.1b reward separator/copy; 0.2 Challenges
  reveal slack ~107px = 11.5%H at 933; 0.3/X.4 flat backdrop; X.3 active nav glyph
  legibility at 420px; X.5 640 Collections Everything card below the fold), plus the
  recorded 640 Challenges panel share (63.3%H).

**Remaining count: CRITICAL 0, HIGH 0, MEDIUM 0, LOW 6** (the five carried LOWs + the
downgraded X.2 margin shortfall). No new discrepancy was found at any height or in
either language.

## Deltas table for the record

| region | old (12:01 bytes) | shipped bytes | reference | verdict |
| --- | --- | --- | --- | --- |
| nav band, all 40 frames | plate flush with the bottom edge | plate 6px clear; inactive strokes ~3px higher | margin ≈14.3 logical px | improved; X.2 downgraded to LOW (6 vs ~14.3) |
| everything above the nav band | — | pixel-identical to the signed-off set | spec + mocks | carries over |
