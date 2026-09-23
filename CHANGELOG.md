# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog, and this project adheres to Semantic Versioning.

## [Unreleased]
### Changed
- **Every setting is now in the `/betterend` menu**, so there is no reason left to open `config.yml`. The old "End Cities" screen is split into **Per-player Loot**, **Protection** and **Finding Cities & Resets**, and everything that was file-only now has a place: the XP level price, price doubling and the frame shimmer (on **Elytra Frames**), ship-only protection, the takeable dragon head and how far protection reaches past each tower (on **Protection**), and worlds to leave alone, the startup check and the saved-copy size limit (on **Finding Cities & Resets**). Two new pages, **Storage** and **Updates & Stats**, cover SQLite or MySQL, update checks, anonymous stats, error reports and extra logging; those take effect after a restart, and the menu says so. The setup tour also asks about the XP price and ship-only protection.

### Fixed
- **MySQL storage never started.** Setting up the tables used a command MySQL doesn't have, so with `database.type: mysql` the plugin stopped during startup and did nothing at all. SQLite servers were not affected. Loot copies stored in MySQL can also now hold more than 64 KB, so a chest full of written books or packed shulker boxes saves properly.
- **Loot could be taken twice.** A player who had a city chest open when the server stopped or reloaded kept what they took, while the chest came back full on the next start. The same happened if a chest was reopened before the previous close had finished saving. Open chests are now saved and closed before shutdown, and a reopen always sees the latest contents.
- **A player's own chest inside a city was copied to everyone.** On worlds that were played before the plugin was installed, a chest a player had placed and filled inside a city tower was treated as city loot, handing every player a copy of what was in it. A chest that holds items but has no loot of its own now stays an ordinary shared chest.
- **Cities that were looted before the plugin was installed gave out empty chests.** Those chests now fill with fresh End City loot for each player, as the documentation always said.
- **City bounds were one block too small** along their east, top and south edges, so a chest or block right on that edge could be missed by per-player loot and protection. Existing cities are corrected automatically on the first start.
- **Finding a ship's elytra frame could load up to 25 chunks at once**, briefly freezing the server, and on Folia could error. It now only looks at the frame's own chunk.
- **Saving a city's snapshot no longer freezes the server** while the rest of the city's chunks load or generate.
- **Protection gaps:** water and lava buckets, pistons pushing or pulling city blocks, withers, and falling sand or anvils could all change a protected city. They can't anymore.
- **Folia:** `/betterend tp` failed, and saving settings from the menu could throw an error while updating the floating price notes. Both are fixed.
- `/betterend tp` now lands you on top of the city's base tower, instead of the middle of the city's area, which can be open void.
- `/betterend delete` now also forgets that city's elytra claims straight away, and lets the city be found again without a restart, as the documentation describes.
- `/betterend resetloot` no longer stalls the server looking up a player name that has never joined.

## [0.3.0] - 2026-09-19
### Added
- **Minecraft 26.3 support, as its own download.** This is the `-mc263` jar; keep using `-mc26` on 26.1 and 26.2. Each download follows its own updates, so a 26.3 server is never offered a jar built for an older Minecraft. The `-mc26` jar still loads and works on 26.3, it just can't protect cushions.
- **Cushions in a city are protected like anything else in it** (`-mc263` only). 26.3 added cushions, and a cushion is an entity rather than a block, so the protection that covers every block in a city didn't see them at all. A cushion can no longer be placed inside a protected city, or taken, blown up or knocked out of one, and `betterend.bypass.protection` still lets staff move one.
- **Automatic error reporting, on by default** (`metrics.error-reporting`, set it to `false` to turn off). When something in the plugin goes wrong it's now reported on its own, so bugs get fixed without a server owner having to notice one and write it up. That matters most for the quiet failures that never become a ticket: a saved copy that wouldn't load, a loot copy that wouldn't save, an elytra claim that didn't persist. Only this plugin's own errors are captured, never another plugin's. IP addresses, file paths containing the OS username, database credentials and player UUIDs are stripped out before anything leaves the server. Each report carries the plugin and Minecraft version, storage type, whether the server runs Folia, a rough city-count band, and which operation was running. `metrics.enabled: false` continues to disable everything at once, and `submitErrors=false` in `plugins/faststats/config.properties` is the server-wide off switch.

### Changed
- **The plugin is now called Better End Cities.** A data pack of the same name has been on Modrinth far longer, and its author asked for the name back. The new name also says what the plugin actually does.
  - The jar is now `BetterEndCities-<version>-mc26.jar` (or `-mc263` on Minecraft 26.3), and the plugin folder is `plugins/BetterEndCities/`.
  - **Updating from 0.2.3 or earlier: rename your `plugins/BetterEnd/` folder to `plugins/BetterEndCities/` before starting the server.** It holds your city registrations, per-player loot state and snapshots. If you skip this, the plugin starts with an empty database and rediscovers cities from scratch, and your saved copies of looted cities are gone.
  - Commands and permissions are unchanged. `/betterend` and `betterend.*` still work exactly as before.
- **The documentation, store pages and `config.yml` comments are rewritten in plain English.** Same information, written for a server owner rather than a developer, with the jargon taken out.

## [0.2.3] - 2026-07-20
### Fixed
- **Every "close" button in the dialogs did nothing.** These screens stay open while you navigate between them, which means a button carries no implicit close — it has to close the dialog itself, and five of them had no action at all: **Close** on the main menu and on each settings screen, and **Close**, **Finish later** and **Done** in the setup tour. All five now close as labelled. "Finish later" still resumes on the step you left.

### Changed
- Dialogs now declare escape-to-close explicitly instead of inheriting the server default, so a screen can always be dismissed with Escape even if one of its buttons fails.

## [0.2.2] - 2026-07-20
### Fixed
- **`/betterend` and `/betterend setup` threw instead of opening.** Both build dialogs that stay open while you navigate between screens, but left the dialog's `pause` flag at its default of `true`. The server rejects a pausing dialog whose after-action leaves it paused, so every screen failed with `Dialogs that pause the game must use after_action values that unpause it after user action`. All five dialogs now declare `pause(false)` — a dedicated server never pauses regardless.

## [0.2.1] - 2026-07-20
### Fixed
- **Better End Cities did nothing on 0.1.0 and 0.2.0.** `/betterend` was registered from a scheduled task rather than during plugin enable. Paper only accepts command registration while a plugin is enabling, so it always threw `Cannot register lifecycle event handlers` — which aborted the rest of startup, leaving the ready flag unset. Every listener checks that flag, so elytra claims, per-player loot, protection and city discovery were all silently inactive, and update checking and metrics never started. The command is now registered inside `onEnable` where Paper expects it. **Updating from 0.1.0 or 0.2.0 requires no config changes — the plugin simply starts working.**
- Startup no longer depends on optional integrations: the ready flag is set before update checking and metrics initialise, and a failure in either is logged instead of disabling the plugin.

## [0.2.0] - 2026-07-19
### Changed
- **Usage metrics moved from bStats to FastStats.** No player data is collected, and the opt-out in `config.yml` (`metrics.enabled`) is unchanged. The server-wide opt-out file is now `plugins/faststats/config.properties` (`enabled=false`) instead of `plugins/bStats/config.yml`; nothing is submitted until the restart after that file is first written, so admins can always opt out before any data leaves the server.

## [0.1.0] - 2026-07-17
### Added
- **Renewable elytra item frames.** The elytra frame in an End Ship stays an item frame; punching it (vanilla pick-up) gives the player a fresh elytra while the frame stays for the next player. Frames are protected from breaking and non-player damage; player-placed frames are never touched.
- **Claim modes** — once per ship (default), re-claimable after each loot refresh, or once per player total.
- **Optional claim cost** — any item (custom/NBT items included), picked from your inventory in-game; the amount slider clamps to the item's max stack size. Free by default.
- **Floating hint** above the ship frame showing the cost (or "Punch to claim").
- **End City auto-discovery** via the server's structure data — exact piece bounds, ships included, active immediately (no approval step).
- **Per-player container loot** (Lootr-style) with a lazy per-city refresh window, shared op-editable loot templates, hopper protection, and player-placed-container detection.
- **Griefing protection**, bounds-based per structure piece — the void between towers stays buildable.
- **Structure snapshots** — gzip block snapshots captured automatically on discovery, restorable on demand (`/betterend reset`) or automatically on each loot refresh (opt-in).
- **Dialog config menu** (`/betterend`) — native MC26 dialogs with sliders, toggles and choice buttons; every change applies live.
- **Guided setup tour** (`/betterend setup`) — every setting explained in plain words, one screen at a time; ops get a one-time reminder on join until it's completed or skipped.
- **BetterAntiDupe compatibility** — claimed elytras are pre-stamped with the claimer's ownership tag when ADP is installed, and the cost picker warns when a tracked material is chosen.
- SQLite (default) or MySQL storage, Folia support, PluginPulse update checking.
