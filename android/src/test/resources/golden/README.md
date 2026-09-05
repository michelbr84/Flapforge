# Golden render references (M10 shim fidelity)

The PNGs in this directory are the desktop Java2D rendering of seven scenes drawn by the game's
own code (`ProceduralArt`, `TextPainter`, `Fonts`, `BackgroundRenderer`): the app icon at 128
and 64 px, a UI composition (panel, the four button states, bird poses, shop portraits, coin
spins, anvil), a four-line text scene in the bundled Nunito font, the Green Fields backdrop with
clouds, every `drawImage` form the game uses, and the styled world backdrops.
`android/src/test/java/awt/GoldenRenderTest.java` draws the same scenes through
the transformed game classes over the `awt.*` shims (Robolectric, native graphics) and compares
the pixels against these files, so a shim regression in transforms, paths, arcs, gradients,
strokes, clips, text or images shows up as a scene drifting from the desktop reference.

| file | scene | size |
|---|---|---|
| `icon128.png` | `ProceduralArt.icon(128)` | 128x128 |
| `icon64.png` | `ProceduralArt.icon(64)` | 64x64 |
| `composition.png` | panel, buttons, bird poses, shop portraits, coins, anvil | 256x256 |
| `text.png` | `TextPainter` at 28/16/20/12 px, every alignment, outlined | 256x128 |
| `background.png` | `fillBackground` at 1/5 scale, sky paint, four clouds | 256x128 |
| `images.png` | natural / scaled / source-region / subimage / nearest-neighbour blits | 256x128 |
| `worlds.png` | `BackgroundRenderer`: Storm Sky, Iron Forge, The Void at 1/5 scale; girders and mesas at 1:1; storm cloud banks and Green Fields hills at 1:1 through one shared context | 256x384 |

## Regenerating

The scene code lives once, between the `GOLDEN SCENES` markers of
`android/tools/GoldenRender.java` (against `java.awt`), and is copied byte for byte into
`GoldenRenderTest.java` with `java.awt.` rewritten to `awt.` (rule T3 of the source transform).
`GoldenRenderTest.sceneSourceIsSharedWithTheDesktopGenerator` reads both files and fails when
the copies differ, so a scene change means editing both and regenerating the PNGs.

The generator needs `java.desktop`, which the Android project cannot see, so it is run by hand
from the repository root with the desktop classes (no Gradle task):

```
./gradlew --offline classes
javac -d build/goldenrender -cp build/classes/java/main android/tools/GoldenRender.java
java -Djava.awt.headless=true \
    -cp build/goldenrender:build/classes/java/main:build/resources/main \
    GoldenRender android/src/test/resources/golden
```

`build/resources/main` supplies the bundled font (`/assets/fonts/Nunito-VariableFont_wght.ttf`),
which the text scene installs exactly as `AssetManager.loadFont` does. The output is
deterministic: the checked-in set was rendered with OpenJDK 17.0.20 and is byte-identical when
rendered again with OpenJDK 21.0.12. Then run the Android test:

```
./gradlew -p android testDebugUnitTest --tests awt.GoldenRenderTest
```

The test writes what the shims drew to `android/build/golden-actual/<scene>.png` (or the
directory named by the `FLAPFORGE_GOLDEN_OUT` environment variable) next to a
`<scene>.diff.png` heat map — grey for small differences, red where a pixel exceeds the
per-channel tolerance — and prints the measured metrics per scene, so a failure can be looked
at rather than guessed at. The worlds scene draws its first five backdrops through a context
each, as the host opens one per frame, and its bottom row through one shared context, as the
screen stack draws every screen and overlay of a frame: the storm's last translucent puddle
fill is what the hills' sky gradient starts from, which pins `Graphics2D` drawing a
`GradientPaint` at the ramp's own alpha. The storm window under its high-contrast palette pins
the `Ellipse2D` outline winding the same way as `Rectangle2D` (the cloud banks append both
into one non-zero path; an ellipse wound the other way punches holes where the strip crosses a
single ellipse).

## What is compared

Shape scenes: the fraction of pixels whose largest premultiplied channel difference exceeds
40/255 (anti-aliasing and rasteriser differences sit far below that) and the mean absolute
channel difference. The text scene: Java2D and Skia rasterise glyphs differently (Skia's grey
antialiasing is lighter), so each line box is compared by glyph footprint — pixels that differ
from the background by more than 10/255, which both rasterisers agree on — and by the
footprint's bounding box, not pixel by pixel; the derived bold title accepts a footprint ratio
from a floor under the regular weight (Robolectric's native runtime draws it, measured 0.68,
because Java2D emboldens the derived face and `Typeface.create(BOLD)` does not there) up to a
ceiling just over the emboldened weight a device produces, so a thinner or missing title fails
on every runtime and an emboldening runtime does not turn the test red. The thresholds in
`GoldenRenderTest` are at least twice the values measured on a green run and were checked to
fail under deliberate shim mutations (arc sweep sign, fill rule, gradient end points, stroke
width, text baseline, antialiasing, subimage offset, the ellipse outline wound against the
rectangle, a gradient inheriting the previous fill's alpha).
