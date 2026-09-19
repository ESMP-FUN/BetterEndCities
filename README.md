# Better End Cities

**Every player gets the elytra, and the item frame stays an item frame.**

The first player to reach an End Ship takes *the* elytra. Everyone after them finds an empty frame and gutted chests.

Other elytra plugins swap the ship's item frame for a vault block, then make you install a datapack and hand out a key item. This one leaves the frame alone. Players punch it exactly like they always have, and every player gets their own.

## What it does

* **Renewable elytras** - punch the ship's item frame, get your own elytra. The frame and its elytra stay for the next player. Once per ship, once per player, or claimable again after every loot refresh. Free, or charge any item, picked from your own inventory in-game.
* **Per-player chest loot** - every player gets their own copy of a city's chest contents, on a refresh timer each city runs for itself. Player-placed chests are left alone.
* **Protection** - towers, bridges and the ship survive creepers, TNT and griefers. The empty space between them stays buildable.
* **Putting cities back** - a copy is saved the moment a city is found. `/betterend reset` restores the blocks and gives everyone fresh loot, or let it happen on every refresh.
* **Automatic** - cities register themselves from the server's own structure data as their chunks load. No per-city setup.
* **No config file needed** - `/betterend` opens a menu, `/betterend setup` asks one question at a time.
* **Anti-Dupe friendly** - with [Better Anti-Dupe](https://github.com/ESMP-FUN/BetterAntiDupe) installed, claimed elytras are marked genuine so they never look like copies.

## Requirements

* **Minecraft 26.1+** on Paper, Folia, or Purpur
* **Java 25+**
* Nothing else. No datapack, no resource pack, no other plugins.

> **Minecraft 26 only.** Uses pop-up menus and world information that don't exist in 1.21. There is no 1.21 build and it will not start on one.

## Quick start

1. Drop the jar into `plugins/` and restart. Use `-mc26` on Minecraft 26.1 or 26.2, `-mc263` on 26.3 or newer.
2. That's it. Cities register as players find them, elytras are renewable, loot is per-player.
3. Optionally run `/betterend setup` for the guided tour, or `/betterend` for the menu.

## Commands

| Command | What it does |
| --- | --- |
| `/betterend` | Settings menu |
| `/betterend setup` | Walks you through the settings |
| `/betterend list` | Every city found so far |
| `/betterend info <id>` | Details about one city |
| `/betterend tp <id>` | Go there |
| `/betterend snapshot <id>` | Save a copy so the city can be put back |
| `/betterend reset <id>` | Restore the blocks, fresh loot for everyone |
| `/betterend resetloot <id> <player>` | Let one player loot it again |
| `/betterend clearclaims <id>` | Let everyone claim the elytra again |
| `/betterend delete <id>` | Stop managing this city |
| `/betterend reload` | Re-read `config.yml` |

## Permissions

| Permission | Default | What it grants |
| --- | --- | --- |
| `betterend.admin` | op | Everything, including the settings menu |
| `betterend.bypass.protection` | op | Build inside protected cities |
| `betterend.discovery.notify` | op | Notice when a city is found |

## Links

**[Docs](docs/README.md)** · **[Discord](https://discord.gg/qwYcTpHsNC)** · **[Issues](https://github.com/ESMP-FUN/BetterEndCities/issues)**

Source-available, non-commercial, see [LICENSE](LICENSE). Contributions welcome, see [CONTRIBUTING.md](CONTRIBUTING.md).
