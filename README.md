# MrCuisineVibeConnect

🇫🇷 **[Version française](README.fr.md)**

> **macOS version.** This is a copy of [Wuki60/MrCuisineVibeConnect](https://github.com/Wuki60/MrCuisineVibeConnect)
> whose install tools run on a **Mac**: shell scripts instead of PowerShell, and
> [mtkclient](https://github.com/bkerler/mtkclient) instead of SP Flash Tool and its Windows driver.
> The Toque app is unchanged and still updates from the original repository's releases. The macOS
> install was done end to end on a real robot in November 2026; see [AGENTS.md](AGENTS.md).

**Toque** is a fast, modern replacement interface for the Lidl **Monsieur Cuisine Connect**
kitchen robot (SKMC 1200). It runs on the robot's own tablet instead of the stock app and still
uses **the official Lidl recipes**.

> Independent project, not affiliated with Lidl, SilverCrest or Hoyer. Motor control is based on
> a public analysis of the serial protocol. Since October 2026 Toque has been running on SKMC 1200
> robots (revisions A1, B2 and C3) in France and Spain, and on an SKMC 1100 A1 (with an adapted
> recovery, see [issue #10](https://github.com/Wuki60/MrCuisineVibeConnect/issues/10) of the
> original repository). Use at your own risk and follow the first-run procedure.
>
> The interface is available in **English**, **French**, **German** and **Spanish** (*Settings › Langue · Language*), and
> other languages can be added with a single file (see below).

## Install

**Start here: [docs/INSTALLATION.md](docs/INSTALLATION.md)**, a step-by-step guide that starts from
zero, for a Mac (and, [tried once](docs/INSTALLATION.md#8-from-a-linux-pc), a
Linux PC). On Windows, follow the
[original repository's guide](https://github.com/Wuki60/MrCuisineVibeConnect/blob/main/docs/INSTALLATION.md).

In short: you need a Mac, a **USB-A male ↔ USB-A male** cable, a small Torx screwdriver and 1 to 2
hours. You prepare the Mac (Homebrew, then a copy of this repository), then either let
[Claude Code](https://code.claude.com/docs/en/quickstart) do the install by following
[AGENTS.md](AGENTS.md), or run [`tools/setup/toque-setup.sh`](tools/setup/toque-setup.sh) yourself,
one step at a time. It backs up the robot first, roots it with Magisk, installs Toque and sets up
ADB over Wi-Fi. You only handle the cover, the cable, the power switch and a tap on the robot's
screen.

## Features

- **Dark night-blue interface** designed for the robot's 7" screen and rotary knob, and smooth on its
  original tablet (1 GB RAM, Android 6): no heavy libraries, downsampled images, recycled views.
- **Lidl recipes, straight from monsieur-cuisine.com.** Toque downloads the official Monsieur
  Cuisine connect recipes (about 1,700 in French, with each step's machine settings) from the
  site's public API, nightly or on demand: no account, no server to run, no need for the stock app.
  Requests are spaced out and only new or changed recipes are downloaded again. The recipes already
  in the stock app can still be imported instead.
- **Author, ratings and reviews**: each recipe shows who published it and its rating on the site,
  and the recipe page loads the users' comments (with Monsieur Cuisine's replies).
- **Search that works**: ignores accents, plurals and typos ("bechamle", "risoto").
  Search by ingredients (e.g. "chicken, curry"), filters, favorites. **Recent** (last cooked first)
  and **Frequent** (most cooked first) lists, also one tap away under the home search bar.
- **Guided cooking**: each step starts with one tap, the next step shows up by itself, built-in
  weighing, resume after a power cut. Settings that follow each other with nothing to do in
  between (rinsing, blending at several speeds…) start together with a single tap: the blade goes
  straight from one speed and temperature to the next without stopping. Recipe steps that use the
  site's **progressive speed ramp** become one machine step per stage. A recipe in progress shows
  on the home screen and can be **abandoned** there (✕), except while a program is running.
- **Your own notes and quantities**: a free note on each recipe, and tapping an ingredient's
  quantity changes it (1000 g of water → 800 g); the weighings and mentions in the steps that use
  it follow in proportion (numeric keypad, fractions included). Favourites, notes and adjusted quantities follow the recipe when a sync
  replaces it with the same recipe from the other source (site or stock app).
- **For more or fewer people**: − / + above the ingredients, from half to double the recipe. Every
  weighed or measured quantity follows (list, weighings, "500 g of water" in the steps), rounded the
  way you would write it by hand, with the same rounding everywhere; eggs and spoons are rounded to
  the half. Times, speeds and temperatures stay those of the tested recipe, with a reminder to keep
  an eye on the cooking, and a warning when the bowl may be too full (2.5 L of liquid at most).
- **My recipes**: write your own (title, people, time, one line per ingredient, steps with their
  robot setting and weighing, chosen with the same safety limits as manual cooking), or make **your
  version** of a Lidl recipe with the pencil. Syncs never touch them. *Settings › My recipes* exports
  them to, or imports them from, `Download/toque-mes-recettes.json` (to back them up or pass them to
  another robot).
- **Recipes from other languages** (option in *Settings › Lidl recipes*): the recipes that don't
  exist in your language are added from English, French, German, Spanish…, each one only once, with
  its language on the thumbnail.
- **Sounds**: a bell rings when the weight to add is reached (sound to choose in *Settings*), and a
  loud tune plays when the recipe's last program ends. A setting you confirmed ("I checked this
  setting") is remembered for the next time you cook the recipe.
- **Manual cooking**, **Knead / Steam / Sauté / Cleaning** programs, **Turbo**, **scale**. Cleaning:
  1 L of hot water and a drop of washing-up liquid, speed rising from 4 to 10, no heat.
- **Offline**: everything works without a network (no more "server unreachable" at startup).
- **Always the right time**: the time zone comes from the Internet connection and the time from
  the network (the robot has no clock battery). Right after power-on, the clock is set from the
  first server that answers, so the time is right within seconds instead of staying on 01:00. Can
  be set by hand in *Settings › Date and time*.
- **Standby**, like the stock app: after a few minutes without a touch or a turn of the knob
  (2 to 30 min, or never, in *Settings › Screen*), a dimmed black screen shows the time, and the
  bowl temperature when it is hot. Never during a program or on the scale. The gesture that wakes
  the screen does nothing else. During standby, the knob ring stays normal, dims to a faint white
  night-light, or turns off (*Settings › Screen*).
- **Bowl sensor that reads the wrong way round**: on some robots (seen on an SKMC 1200 C3) the
  base reports the bowl as present when it is removed; *Settings › Safety › Invert the bowl
  sensor* fixes it.
- **Made for a robot without Android buttons**: Toque opens at startup, and a **Back to Toque**
  button stays on top of the stock app when you open it.
- **Updates from GitHub**: *Settings › Updates* checks this repository's
  [Releases](https://github.com/Wuki60/MrCuisineVibeConnect/releases) and installs the new version
  (silently on a rooted robot, then Toque restarts). Refused while a program is running. Recent
  root certificates missing from Android 6 (ISRG Root X1/X2 for GitHub's download server, Sectigo,
  Google) are bundled, so downloads over HTTPS work on the robot. When a new version is out, Toque
  says so when it starts or wakes from standby (at most every few hours, never during a program);
  "Later" waits until the next day.

## Safety: what changes compared to the stock app

All original limits are kept (speed 3 max when heating and in reverse, maximum durations,
Turbo conditions…). Several flaws found while analysing the protocol are fixed:

| Stock app flaw | Toque |
|---|---|
| Start was requested before checking the lid | Lid, errors and data freshness are checked **before** any start, and before any new setting while running |
| No detection of a silent motor board | No reply for 1.5 s: everything stops and an alert is shown |
| Serial write errors ignored | Stop and alert |
| A STOP followed by a START could never send the stop | Any restart or direction change waits for a sample confirming **motor stopped** |
| Serial reading without resynchronisation | Fragmented, merged or corrupted frames are handled |
| The blade sometimes turned in steam mode | Steam at speed 0 |
| Temperature only watched by the base | Everything stops if the bowl reaches 150 °C, keeps rising 15 °C above the setting, or heats during a step without heating (thresholds not yet measured on a real base) |

Also added: confirmation (and a gradual ramp-up) before blending contents at 60 °C or more,
confirmation before steaming if the scale reads under 300 g or gives no reading, a
speed limit with the whisk, alerts that stay latched until acknowledged, nothing restarts on its
own after a pause or an alert. When cold, the blade goes straight to the requested speed. Motor control runs in **its own process**: if the interface crashes, a cooking run is not
cut off and finishes normally.

## Project layout

```
core/   Pure Kotlin logic, tested without Android
        protocol/  serial frames (15 bytes sent, 27 received, 4800 baud)
        machine/   safety rules, controller, 100 ms loop, links (root serial, TCP, demo)
        recipes/   model, stock database import, search
app/    Android app (minSdk 23), no AndroidX
        machine/   motor control service (":machine" process) and IPC
        recipes/   local catalogue, Lidl sync
        ui/        screens
tools/  setup/toque-setup.sh (full install from a Mac, can be driven by an AI agent), robot/
        (scripts run on the robot: root, flash from TWRP, restore), install, restore and release
        scripts (.sh for macOS, the original .ps1 for Windows), twin.py (digital twin for testing)
docs/   INSTALLATION.md, INSTALLATION.fr.md
AGENTS.md  install procedure for an AI agent
```

## Build

Open the folder in **Android Studio**, wait for the Gradle sync, then
**Build › Build App Bundle(s) / APK(s) › Build APK(s)**. The APK is in
`app/build/outputs/apk/debug/`.

From the command line: `./gradlew assembleDebug` (and `./gradlew :core:test` for the tests). Gradle
needs JDK 17 or 21; with a newer default JDK the build stops with just a version number as error.
Android Studio's JDK works:
`export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.

## Try it without the robot

1. **Demo mode** (default): install the APK on any Android device (or the Android Studio
   emulator, in landscape). The motor board is simulated; *Settings › Tools › Motor base
   diagnostics* lets you open the lid or trigger an error to see the safety
   features. *Settings › Lidl recipes › Sync* downloads the recipes on any device too.
2. **Digital twin**: the twin from the [Monsieur Cuisine Developer SDK](https://github.com/antrea/monsieur-cuisine-developer-sdk)
   reproduces the real protocol independently of Toque.
   Run `python3 tools/twin.py --sdk <SDK folder>`, then in Toque choose *Settings › Machine ›
   Twin (PC)* (emulator: host `10.0.2.2`; real device: `adb reverse tcp:8765 tcp:8765` and host
   `127.0.0.1`). `python3 tools/twin.py --send "lid 1"` opens the lid, etc.

## Add a language

The app is written in French; each other language is one file in
[`app/src/main/assets/lang/`](app/src/main/assets/lang) (`en.json` for English).

1. Copy `en.json` to `<code>.json` (for example `de.json`), set `"_name"` (for example `"Deutsch"`)
   and translate the values. Keys are the French texts; keep the `%s` / `%d` placeholders.
   Missing texts stay in French.
2. Rebuild the app, or, without rebuilding, copy the file to `Android/data/app.toque/files/lang/`
   on the device and restart Toque.

The language then appears in *Settings › Langue · Language*. `./gradlew :core:test` checks
every language file.

## Publish a new version

1. Increase `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Commit and push to `main`.

The [release workflow](.github/workflows/release.yml) runs the tests, builds the APK and publishes
the `vX.Y.Z` release with it. Robots then find it in *Settings › Updates*.

All builds are signed with the project key in `app/signature/` (public on purpose), so an APK built
on any PC or by GitHub installs over the previous version. Only install APKs from this repository's
releases.

Without GitHub Actions: `./gradlew assembleRelease`, then
`bash tools/publish-release.sh --apk app/build/outputs/apk/release/app-release.apk [--repo owner/name]`
creates the `vX.Y.Z` release and attaches the APK (needs `gh`; GitHub token: `GH_TOKEN`, `gh auth login`,
or the credentials already stored for git; `--dry-run` only shows what it would do).

## Install on the robot

**[docs/INSTALLATION.md](docs/INSTALLATION.md)** (see [Install](#install)): preparing the Mac, root,
install with Claude Code or by hand, first run with an empty bowl, recipe sync, going back to the
stock app. From Windows, use the original repository's guide.

## Credits

- Protocol: [Monsieur Cuisine Developer SDK](https://github.com/antrea/monsieur-cuisine-developer-sdk) (MIT).
- Robot access: [Monsieur-Cuisine-Connect-Hack](https://github.com/EliasKotlyar/Monsieur-Cuisine-Connect-Hack).
- MediaTek download mode on macOS: [mtkclient](https://github.com/bkerler/mtkclient) (GPLv3, downloaded by `prepare`, not included; `prepare` applies the macOS serial fixes in `tools/setup/mtkclient-macos-serial.patch`).
- Contributions: @pierok13 (standby, notes and quantities, speed stages, sounds, recent recipes,
  Android 6 roots, bowl sensor), @lucasbraud, @OUTSIDE2k8 (Spanish), @Locodice67 (German, install
  script), @baptiste-gislot, @florentdestremau (rotary knob), and everyone who tested their robot
  and reported bugs.

MIT License. This repository contains no Lidl recipes, images or code: each robot downloads the
recipes itself from monsieur-cuisine.com, for its owner's personal use.
