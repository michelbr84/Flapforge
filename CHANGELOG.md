# [](https://github.com/michelbr84/Flapforge/compare/v1.2.2...v) (2020-08-06)

## [1.2.2](https://github.com/michelbr84/Flapforge/compare/v1.2.1...v1.2.2) (2020-07-14)

### Features

* Improved the scoring system ([33ad51a](https://github.com/michelbr84/Flapforge/commit/33ad51a97bcb6c2adce3fc944fa5aea00d210198))

### BREAKING CHANGES

* Removed the timer and updated the scoring system, resulting in more accurate score tracking.

---

## [1.2.1](https://github.com/michelbr84/Flapforge/compare/v1.2.0...v1.2.1) (2020-07-12)

### Features

* Changed the audio playback method ([9429be6](https://github.com/michelbr84/Flapforge/commit/9429be613a21752d2c61e38ca7df87fb4a0b51b9))

### BREAKING CHANGES

* Repeatedly playing short audio clips using the `AudioClip` class could cause thread conflicts and make the game freeze. Audio playback was changed to use the `AudioPlayer` implementation from `sun.audio`.

---

# [1.2.0](https://github.com/michelbr84/Flapforge/compare/v1.1.0...v1.2.0) (2020-07-11)

### Features

* Added randomly generated pipes that can move vertically ([ab33686](https://github.com/michelbr84/Flapforge/commit/ab33686c8c2ace54da3ddffe220b40a33100989f))

### BREAKING CHANGES

* The probability of spawning moving pipes now increases as the player's current score increases.

---

# [1.1.0](https://github.com/michelbr84/Flapforge/compare/v1.0.0...v1.1.0) (2020-07-11)

### Features

* Added floating pipes ([074595b](https://github.com/michelbr84/Flapforge/commit/074595b3408a1323b41226d4b4259c6aff696888))

---

# [1.0.0](https://github.com/michelbr84/Flapforge/compare/d158fa5ca5927e1febcd460e8d61b5a16756c761...v1.0.0) (2020-07-09)

### Features

* Implemented the core gameplay features of the original Flappy Bird ([d158fa5](https://github.com/michelbr84/Flapforge/commit/d158fa5ca5927e1febcd460e8d61b5a16756c761)
