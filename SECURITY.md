# Security Policy

## Scope

Flapforge is a single-player desktop game. It has no network features, no
accounts, no telemetry and no remote services: the only data it writes is the
local save file and settings under the per-user data directory
(`~/.flapforge` on Linux, `%APPDATA%\Flapforge` on Windows,
`~/Library/Application Support/Flapforge` on macOS), plus optional log or
screenshot output under the project's `build/` directory when developing.

The attack surface is therefore small, but not empty. Reports are welcome for:

- **Save and settings files.** The game parses JSON from the data directory.
  A crafted save must never crash the JVM in a way that loses the player's
  data, execute code, or read/write files outside the data directory.
  Corrupt files are expected to be quarantined and a backup restored.
- **Command-line and content parsing.** Launch flags (`--home`, `--seed`,
  `--headless-run`, ...) and the bundled JSON content go through strict
  parsing; anything that turns them into path traversal, unbounded memory use
  or a hang is in scope.
- **Build and supply chain.** The Gradle wrapper, pinned dependencies
  (Gson, JUnit) and the GitHub Actions workflows. A report that the wrapper
  jar or a workflow could be abused is in scope.
- **Packaged builds.** Anything in the fat jar or the `jpackage` image that
  behaves differently from source.

Out of scope: cheating in a local single-player game (editing your own save
file is a feature, not a vulnerability), and issues in the JDK or in
third-party libraries that are not caused by how Flapforge uses them (please
report those upstream).

## Supported versions

Only the latest release tag and the current `main`/`rewrite/flapforge`
branch receive fixes. Older tags are not patched.

## Reporting a vulnerability

Please do **not** open a public issue for anything you believe could put
players' data at risk.

1. Use GitHub's private vulnerability reporting on the repository
   ("Security" tab, "Report a vulnerability") if it is enabled.
2. Otherwise contact the maintainer privately through the e-mail address on
   the maintainer's GitHub profile (`michelbr84`), with the subject
   `Flapforge security report`.

Include the version or commit, your OS and JDK, a description of the impact,
and steps or a file that reproduces the problem. Please give us a reasonable
time to respond and fix the issue before disclosing it publicly.

## What to expect

- An acknowledgement within 7 days.
- A fix or a mitigation in a new release, with credit in `CHANGELOG.md` if
  you would like it.
- Coordinated disclosure once the fix is available.

Thank you for helping keep Flapforge safe for its players.
