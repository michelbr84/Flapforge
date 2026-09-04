# Third-Party Notices

Flapforge is licensed under the MIT licence (see [`LICENSE`](LICENSE)). This
file lists the third-party software and content that Flapforge is derived
from, depends on, or ships, together with the licence each one is used under.
It is kept up to date whenever a dependency or an asset is added; contributors
must add an entry before merging anything that is not their own work.

## 1. kingyuluk/FlappyBird (design foundation) — MIT

- Project: <https://github.com/kingyuluk/FlappyBird>
- Author: Kingyu Luk (`kingyuluk`)
- Licence: MIT (the upstream repository ships the licence as a file named
  `License`, detected by GitHub as SPDX `MIT`).

Flapforge started as a fork of this Java Flappy Bird implementation. The
rewrite replaced every upstream source file with new code under
`io.github.michelbr84.flapforge`, but the game's *feel* is a deliberate
reproduction of the upstream behaviour: its physics constants and update
order were studied and are converted to a fixed 60 Hz simulation pinned by
a literal transliteration of the upstream integer loop in the test
suite (`ClassicReference`). The inherited release notes are preserved in
`CHANGELOG.md` under "Inherited upstream history", and the original sources
remain available in this repository's git history (commit `b811782` and
earlier). Credit is also given in `README.md` ("Original Project").

The upstream `License` file contains the MIT text with GitHub's template
placeholders left in place (`Copyright (c) [year] [fullname]`). The notice
below therefore reconstructs the copyright line from the repository's author
and its 2020 release history; the permission text is reproduced verbatim.

```text
MIT License

Copyright (c) 2020 Kingyu Luk (kingyuluk)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

### Upstream image and audio assets are not shipped

The upstream project bundled sprite sheets, background images, number fonts,
title/game-over artwork, screenshots and three WAV sound effects
(`resources/img`, `resources/wav`, `resources/readme_img`). Its README states
that these resources were "obtained from the internet for learning purposes".
The MIT licence covers the upstream *source code*; it does not establish
redistribution rights for artwork and audio whose authors are unknown, and
some of the images reproduce the "Flappy Bird" wordmark and character, which
are the property of their respective owners.

For those reasons **Flapforge ships none of the inherited assets**. They were
deleted from the working tree in milestone M0 (they remain in git history
only, and are not part of any build, jar or release), and no file derived from
them exists in `src/`. Every image in the game is generated procedurally at
runtime (`render.ProceduralArt`), and every sound effect and music track is
synthesised at runtime by the software mixer, tone synthesiser and music
sequencer (`audio.SoftwareMixer`, `audio.ToneSynth`, `audio.MusicSequencer`) —
no audio files ship. The upstream
wordmarks are never drawn. Future original art or audio packs are added through
`src/main/resources/assets/manifest.json`, which records the licence and
provenance of every file, and get an entry in this document.

## 2. Runtime dependencies

### Gson 2.11.0 — Apache License 2.0

- Project: <https://github.com/google/gson>
- Copyright 2008 Google Inc.
- Licence: Apache License, Version 2.0 —
  <https://www.apache.org/licenses/LICENSE-2.0>

Gson is bundled inside the self-contained jar (`flapforge-<version>-all.jar`)
and the packaged application. It is used to read the JSON content files, the
settings and the save file. Gson is redistributed unmodified; the Apache
licence text is available at the link above and is included in the Gson jar.

## 3. Build and test tooling (not shipped)

These are used to build and test Flapforge; they are not part of the
distributed game.

| Component | Version | Licence |
| --- | --- | --- |
| Gradle and the Gradle wrapper | 9.7.1 | Apache License 2.0 |
| JUnit Jupiter / JUnit Platform (via `junit-bom`) | 6.1.3 | Eclipse Public License 2.0 |
| GitHub Actions (`actions/*`, `gradle/actions`, `mikepenz/action-junit-report`) | see `.github/workflows` | MIT / Apache License 2.0 (per action repository) |

## 4. Fonts

### Nunito (variable) — SIL Open Font License 1.1

Since M8 the UI text is drawn with the bundled Nunito variable font instead of
the JDK's logical `SansSerif` (which is resolved to a system font at runtime
and is not redistributed):

- Font name and version: **Nunito**, variable font (`Nunito-VariableFont_wght.ttf`),
  as published in the `google/fonts` repository (copyright 2014)
- Author / copyright holder: The Nunito Project Authors
  (https://github.com/googlefonts/nunito)
- Licence: SIL Open Font License 1.1
  (https://openfontlicense.org), shipped next to the font as
  `src/main/resources/assets/fonts/OFL.txt`
- Reserved Font Name: **Nunito** (per the licence above)
- Modified: no — the file is redistributed unmodified; the game only derives
  point sizes and styles from it at runtime

The file is declared in `src/main/resources/assets/manifest.json` under the id
`font/ui` and loaded lazily during the boot sequence. When the entry or the
file is missing, the game falls back to the logical `SansSerif`, which is not
redistributed.

## 5. Trademarks

"Flappy Bird" is a trademark of its respective owner. Flapforge is an
independent project that is not affiliated with, endorsed by, or derived from
the assets of the original Flappy Bird game; the name is used only to describe
the gameplay genre. See the Disclaimer section of `README.md`.
