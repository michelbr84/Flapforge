# BalancingSim

Batch simulation of the game over many seeds (plan D21, milestone M1). It builds runs from the
shipped `data/*.json` through `content.RunFactory`, drives them with `gameplay.harness.BotPilot`
at a chosen skill preset and prints survival percentiles, scores and death causes. Nothing about
it touches the window: it is the same pure simulation the game runs, so a number printed here is
the number a player gets.

## Running

```bash
./gradlew balancing -PtoolArgs="--seeds 200 --skill average --ticks 20000 --csv build/balancing.csv"
```

or through the wrappers in this directory (they forward every argument):

```bash
tools/balancing/run.sh --seeds 200 --skill average --csv build/balancing.csv
tools\balancing\run.ps1 --seeds 200 --skill average --csv build\balancing.csv
```

## Options

| Option | Default | Meaning |
| --- | --- | --- |
| `--seeds N` | 100 | runs per (bird × skill) cell |
| `--seed0 N` | 1 | first seed; run *i* uses `seed0 + i` |
| `--ticks N` | 20000 | tick budget of one run (60 ticks = 1 s) |
| `--bird ID` | `classic` | bird id, or `all` to iterate the bird registry |
| `--world ID` | `green_fields` | world id |
| `--tier ID` | `normal` | difficulty tier id |
| `--skill NAME` | `average` | `novice`, `average`, `expert`, `perfect` or `all` |
| `--csv PATH` | — | write one row per run |
| `--help` | — | print the options |

## Output

Per cell: the `p10 / p50 / p90` percentiles, minimum, maximum and mean of gates passed and of
ticks survived, the mean points, how many runs reached the tick budget alive, and the death-cause
histogram (`OBSTACLE`, `GROUND`, `ALIVE` for a run that ran out of budget).

The CSV has one row per run: `bird,skill,seed,gates,points,ticksAlive,finished,deathCause`.

## Determinism

Every run is seeded (`RandomProvider`, plan D12) and the pilot draws its reaction noise from its
own stream, so the same command prints the same numbers on every machine and every JDK. Balance
is therefore changed by editing `src/main/resources/data/*.json` and re-running this tool — never
by weakening the bot (E25).
