_Language:_ **English** · [[Português (Brasil)|Perguntas-Frequentes-(pt-BR)]]

# FAQ

Short answers to the questions that come up most, each with a link to the page that has the
details.

## Where is my save, and how do I reset it?

Your profile — `save.json`, its backup `save.json.bak`, a `backups/` folder and `settings.json` —
lives in `~/.flapforge` on Linux, `%APPDATA%\Flapforge` on Windows and
`~/Library/Application Support/Flapforge` on macOS (or wherever `--home DIR` points). To start
over, launch once with `--reset-save`: the old save and its backup are moved aside as
`save.reset-<time>.json` and `save.bak.reset-<time>.json`, never deleted, so you can put them
back by hand. See [[Getting Started]] and [[Settings and Accessibility]].

## Is there online play, leaderboards or accounts?

No. Flapforge is a single-player game with no network features, accounts, telemetry or remote
services; records, seeds and dailies live in the local save. Leaderboards were deliberately left
out of the first release (there is no online infrastructure); your run history is kept locally
instead. The daily challenge needs no server either: its seed is nothing but the UTC date, so
everyone gets the same run on the same day. See [[Game Modes and Difficulty]].

## I launched with `--world wind_valley` and the game ignored it. Why?

`--world` selects a world, it does not unlock one. When your profile owns the world the flag
writes the selection, exactly as the World Select would, and the hub's plaque and START RUN play
it. When it does not, the profile is left alone and a line on stdout says so
(`--world <id>: not unlocked in this profile …`); START RUN keeps playing your current
selection. Unlock the world first — see the world question below and [[Worlds and Bosses]].

## I unlocked something at lunchtime, but the daily is still the same. Why?

By design. The daily pick — world, tier and two forced modifiers, all drawn from what you own —
is written to your profile the first time the Daily mode is *viewed* or played on a given UTC
date, and every later question about that date is answered from the record. So a world unlocked
at noon cannot move the run you practised in the morning; only the attempt counter and the best
gate count change, and a retry keeps the seed. Tomorrow's daily will draw from the new content.
See [[Game Modes and Difficulty]].

## Does an instant retry lose the rewards of the run I just finished?

No. Coins, XP, achievements and unlocks are banked the moment the run ends, before the game-over
strip appears, so pressing `Space` (or clicking) to retry never loses them. A standard retry
starts a fresh seed; a daily retry keeps its seed and only counts the attempt. See
[[Playing a Run]].

## How do I play in Portuguese?

Open Settings (the gear on the hub) and set the first row, **Language**, to **Portuguese
(Brazil)**; the switch is immediate. **Auto** follows your system locale. For one launch only,
pass `--lang pt_BR` (or `--lang en`) on the command line. See [[Settings and Accessibility]].

## There is no sound. What can I do?

First check the Sound section of Settings (**Mute all sound**, the three volumes) and the `M`
key, which mutes on every screen and is remembered. If the game printed `Audio: no output
device` at start-up, no usable output line was found (a container, a busy PulseAudio, no sound
card) and it is running silently on purpose; `--no-audio` selects that path deliberately, which
is useful on machines whose audio stack is slow to open. The game plays exactly the same either
way. See [[Settings and Accessibility]].

## The window is too big or too small.

The playfield is 420×640 logical pixels and the window starts at the largest whole-number scale
that fits your screen height (2× on a 1440-class display, 1× on 1080p). Pass `--scale N` to pick
a scale, or press `F11` for borderless fullscreen. In Settings › Display, **Integer scaling**
keeps the picture at whole-pixel scales, **Fill screen** paints sky and earth over the letterbox
bars of tall windows, and **Smoothing** controls the filtering at fractional scales. See
[[Settings and Accessibility]].

## How do I quit?

Press `Esc` on the home hub: the toast "Press again to quit" appears, and a second press within
three seconds closes the game. Settings › About also has a **Quit** row on the desktop. On
Android neither exists; leave the app with the system's Back gesture. See [[Home Hub]].

## What does Forge on the hub open?

The bottom navigation's **Forge** item opens the whole upgrade workshop — the screen titled
**Upgrades**, with the flight, economy and forge trees where coins buy permanent stat levels. It
is not the Iron Forge world, the Cinder bird or any single tree. See [[Shop and Upgrades]].

## How do I unlock Hard and Nightmare?

Each tier opens by either of two branches. **Hard** (scroll ×1.10, gap ×0.92, rewards ×1.5):
pass 40 gates in one run *or* 400 gates across the profile; the `hard_tier_1` upgrade node
(400 coins) is a paid shortcut. **Nightmare** (scroll ×1.20, gap ×0.85, every obstacle moves,
lethal ceiling, rewards ×2.5): complete the Corridor Boss challenge *or* reach level 20. Tiers
are chosen on the World Select's difficulty row. See [[Game Modes and Difficulty]] and
[[Challenges and Goals]].

## How do I unlock the other worlds?

Every world after Green Fields opens either by clearing the previous world's boss or by buying
it in the Shop: Wind Valley 350 coins, Iron Forge 700, Storm Sky 1200, The Void 2000. Green Fields'
boss waits at gate 30. Challenges ignore all this: a challenge plays in its world whether you own
it or not. See [[Worlds and Bosses]] and [[Shop and Upgrades]].

## Can I play on Android?

Yes. Every release on the
[Releases page](https://github.com/michelbr84/Flapforge/releases) carries a
`Flapforge-<version>-android.apk` alongside the desktop bundles. It is a sideloadable,
debug-signed APK for Android 13 and newer — it is not on a store, so allow installing from the
source you download it with. Touch works everywhere: tap to flap, the buttons on the game-over
and pause panels, and portrait screens are filled edge to edge. See [[Getting Started]] and
[[Controls]].

## My settings came back as defaults after an update. Why?

The `settings.json` file carries a version. When a build finds a file of a different version it
does not load it: the defaults are restored, a warning toast names the old file, and the file is
kept beside the new one as `settings.v<N>.json` so nothing is lost. See
[[Settings and Accessibility]].

## How do I report a bug or propose a feature?

Open an issue at <https://github.com/michelbr84/Flapforge/issues> and use the templates: *Bug
report* asks for a summary, the exact launch command, the steps, and the expected and actual
behaviour; *Feature request* asks for the problem and the proposed change. Include your OS, the
JDK (`java -version`), how you launched the game and, for a run, the seed shown on the `F3`
overlay. Security-sensitive reports follow
[`SECURITY.md`](https://github.com/michelbr84/Flapforge/blob/main/SECURITY.md). To contribute a
fix yourself, see [[Building and Contributing]].
