#!/usr/bin/env python3
"""P0 prototype of the build-time source transform (M10, D8).

Rules (forward, applied in this order):
  T1  "javax.sound.sampled."  -> "jssound."
  T2  "javax.imageio."        -> "jimageio."
  T3  "java.awt."             -> "awt."   only when the tail is
       - an uppercase letter (a class: java.awt.Graphics2D -> awt.Graphics2D), or
       - one of the guarded subpackages: geom. / image. / event.
      (this leaves strings like the "java.awt.headless" property name untouched)
  T4  StrictBinder's record reflection -> the jrecord shim (literal tokens that
      occur only in content/StrictBinder.java; no forward image pre-exists):
  T4a "import java.lang.reflect.RecordComponent;" -> "import jrecord.RecordComponent;"
  T4b "raw.isRecord()"                            -> "jrecord.Records.isRecord(raw)"
  T4c "raw.getRecordComponents()"                 -> "jrecord.Records.components(raw)"
      (D8 desugars every record below API 34, which strips the platform's record
      reflection; the shim answers from the build-time table instead)

The integrity gate reverses the same rules on the transformed output and requires
byte-identical results, then asserts no java.awt/javax.sound/javax.imageio/
java.lang.reflect.RecordComponent references survive in the transformed tree.

The six desktop-only files are excluded entirely (they get Android replacements):
  app/GameWindow, app/BufferStrategyPresenter, app/AwtInputBridge,
  app/KeyRepeatFilter, app/AwtHost, Flapforge
"""
import re
import shutil
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
SRC = REPO / "src/main/java"
OUT = Path("/tmp/flapforge-transform/java")

EXCLUDED = {
    "io/github/michelbr84/flapforge/app/GameWindow.java",
    "io/github/michelbr84/flapforge/app/BufferStrategyPresenter.java",
    "io/github/michelbr84/flapforge/app/AwtInputBridge.java",
    "io/github/michelbr84/flapforge/app/KeyRepeatFilter.java",
    "io/github/michelbr84/flapforge/app/AwtHost.java",
    "io/github/michelbr84/flapforge/Flapforge.java",
}

AWT_TAIL = re.compile(r"(?=[A-Z]|geom\.|image\.|event\.)")

# T4a-c, mirrored from TransformSourcesTask in android/build.gradle (and
# GoldenRenderTest.forward()): change all three together.
T4 = (
    ("import java.lang.reflect.RecordComponent;", "import jrecord.RecordComponent;"),
    ("raw.isRecord()", "jrecord.Records.isRecord(raw)"),
    ("raw.getRecordComponents()", "jrecord.Records.components(raw)"),
)

def forward(text: str) -> str:
    text = text.replace("javax.sound.sampled.", "jssound.")
    text = text.replace("javax.imageio.", "jimageio.")
    # regex split keeps the guard simple: replace "java.awt." only where the
    # look-ahead sees a class or a guarded subpackage
    text = re.sub(r"java\.awt\." + AWT_TAIL.pattern, "awt.", text)
    for src, dst in T4:
        text = text.replace(src, dst)
    return text

def reverse(text: str) -> str:
    text = re.sub(r"jssound\.", "javax.sound.sampled.", text)
    text = re.sub(r"jimageio\.", "javax.imageio.", text)
    text = re.sub(r"awt\." + AWT_TAIL.pattern, "java.awt.", text)
    for src, dst in T4:
        text = text.replace(dst, src)
    return text

def main() -> int:
    if OUT.exists():
        shutil.rmtree(OUT)
    files = sorted(p for p in SRC.rglob("*.java") if not EXCLUDED.intersection(
        p.relative_to(SRC).as_posix().split("/")) and
        p.relative_to(SRC).as_posix() not in EXCLUDED)
    skipped = sorted(p.relative_to(SRC).as_posix() for p in SRC.rglob("*.java")
                     if p.relative_to(SRC).as_posix() in EXCLUDED)
    out_files = []
    for p in files:
        rel = p.relative_to(SRC)
        dst = OUT / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        src_bytes = p.read_bytes()
        dst.write_bytes(forward(src_bytes.decode("utf-8")).encode("utf-8"))
        out_files.append((rel, p, dst))

    # gate 1: reverse rewrite must reproduce the originals byte for byte
    bad = 0
    for rel, p, dst in out_files:
        if reverse(dst.read_bytes().decode("utf-8")).encode("utf-8") != p.read_bytes():
            print(f"INTEGRITY FAIL (round-trip): {rel}")
            bad += 1

    # gate 2: no AWT/javax/platform-record refs may survive the transform
    survivor = re.compile(r"javax\.sound\.sampled\.|javax\.imageio\.|java\.lang\.reflect\.RecordComponent|"
                          r"java\.awt\.(?=[A-Z]|geom\.|image\.|event\.)")
    survivors = []
    for rel, p, dst in out_files:
        text = dst.read_bytes().decode("utf-8")
        for i, line in enumerate(text.splitlines(), 1):
            if survivor.search(line):
                survivors.append(f"{rel}:{i}: {line.strip()[:90]}")

    print(f"transformed: {len(out_files)} files (excluded {len(skipped)} desktop-only)")
    print(f"round-trip integrity: {'OK' if bad == 0 else f'{bad} FILES FAILED'}")
    print(f"surviving refs: {len(survivors)}")
    for s in survivors[:15]:
        print("  " + s)
    return 0 if bad == 0 and not survivors else 1

if __name__ == "__main__":
    sys.exit(main())
