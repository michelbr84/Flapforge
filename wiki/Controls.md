_Language:_ **English** · [[Português (Brasil)|Controles-(pt-BR)]]

# Controls

Flapforge deliberately keeps its primary control to one button: difficulty comes from timing,
positioning, reading obstacles and build decisions, not from input combinations. A second input
fires the equipped active ability, and everything else is menu navigation. Keyboard, mouse and
touch all work, on every screen.

## Keys and mouse

| Input | Action |
| --- | --- |
| `Space`, `Up arrow`, left mouse button | Flap |
| `X`, `Shift`, right mouse button | Use the equipped active ability |
| `Esc` | Pause the run / go back a screen / on the home hub, twice to quit |
| `Enter` | Confirm the focused item |
| `M` | Mute / unmute audio |
| `F3` | Toggle the debug overlay (tick rate, frame time, seed) |
| `F11` | Toggle borderless fullscreen |

A flap is an edge: one press is one flap, however long you hold the key, unless **Hold to flap**
is on (see below). In the READY moment before a run, the first flap is what starts it.

## Menus

Every screen is usable with either input device:

- arrow keys or `Tab` move the focus ring, `Enter` or `Space` activate the focused control, and
  `Esc` goes back one screen;
- every control also responds to mouse hover and click, and to touch;
- on the home hub `Esc` shows "Press again to quit" and a second press closes the game. On the
  desktop, Settings › About has a Quit row too.

Lists and tabs (the Shop, the Goals tabs, the upgrade trees) take the same focus ring, and the
text-size and colour-blind settings apply to all of them — see [[Settings and Accessibility]].

## Rebinding

All seven keyboard bindings in the table above are rebindable in Settings › Controls: select the
action and the screen waits with "Press a key, or Esc to cancel". The arrow keys, `Esc`-to-go-back
and the mouse buttons are fixed. Bindings live in `settings.json` under `keyBindings`, which
carries exactly those seven actions; "Restore defaults" puts them back.

## In a run

| Situation | Input |
| --- | --- |
| Flying | flap; ability; `Esc` pauses |
| Paused | the **Resume** and **Menu** buttons; `Esc` resumes; a click or tap outside the buttons resumes too |
| Modifier draft | arrows compare the cards, `Enter` (or `Space`) takes the focused one, `Esc` or the **Skip this draft** button skips |
| Boss | nothing new: fly through the streamed patterns until the timer runs out |

While the draft cards are up, a flap, an ability press or a pause are not forwarded to the run,
so mashing `Space` over the cards cannot fly the bird into a pipe it cannot see; the flap you were
holding when the draft opened cannot take a card by itself either. See
[[Modifiers and Synergies]].

## The game-over strip and retrying

When the bird dies the game-over strip appears with three buttons: **Retry**, **Summary** and
**Menu**.

- `Space` (or a left click, or a tap outside the buttons) retries instantly with a fresh seed.
- `Enter` opens the run summary.
- `Esc` returns to the home hub.

Rewards are banked the moment the run ends, so a retry never loses them. A retry of the daily
keeps its seed and only counts the attempt; see [[Game Modes and Difficulty]]. The strip and the
summary are described on [[Playing a Run]].

## `M`, `F3` and `F11` are remembered

The three toggles work on every screen and are not throwaway switches: each one flips the
matching setting (mute, the debug overlay, fullscreen) in `settings.json`, applies it and persists
it, so the game starts up the way you left it and the Settings screen always shows what is in
force.

## Input timing

Input is sampled per simulation tick (60 Hz), not per frame: a tap shorter than a frame is never
lost, and key auto-repeat never produces an extra flap. A key still held when the window loses
focus is released cleanly, and a key held across the fullscreen switch (`F11` recreates the
window) does not turn into a second press.

## Touch (Android)

- A tap anywhere on the playfield flaps.
- The game-over strip's **Retry** / **Summary** / **Menu** and the pause panel's **Resume** /
  **Menu** are real buttons; a tap outside them still retries (game over) or resumes (pause).
- Every menu control responds to a touch the way it responds to a click.
- There is no Quit row on Android; leave the app the way the system does.
- The picture fills tall screens edge to edge; the **Fill screen** setting (on by default)
  restores the letterbox when switched off.

## Hold to flap

Settings › Game › **Hold to flap** makes holding the flap input flap continuously — one flap
every 24 ticks — instead of once per press. It is an accessibility option, persisted like every
other setting; see [[Settings and Accessibility]].

> **Tip:** the HUD and the `F3` overlay both show the seed of the run you are flying. Seeded mode
> replays the seed of the last run you finished, so the pipes that killed you can be flown again
> in exactly the same places — see [[Game Modes and Difficulty]].
