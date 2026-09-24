# Better End Cities

**Every player gets the elytra, and the item frame stays an item frame.**

The first player to reach an End Ship takes *the* elytra. Everyone after them finds an empty frame and gutted chests.

Other elytra plugins swap the ship's item frame for a vault block, then make you install a datapack and hand out a key item. This one leaves the frame alone. Players punch it exactly like they always have, and every player gets their own.

## What it does

* **Renewable elytras** - punch the ship's frame for your own elytra; the frame keeps its elytra for the next player. Once per ship, once per player, or again after every loot refresh
* **Optional price** - any item (picked from your inventory in game) and XP levels, which can double with each elytra bought
* **Per-player chest loot** - each player gets their own copy of a city's chests, refreshed on a timer each city runs on its own. Player-placed chests are left alone
* **Protection** - towers, bridges and the ship survive creepers, TNT, pistons and griefers, or protect only the ship. The space between towers stays buildable
* **Putting cities back** - a copy is saved when a city is found. `/betterend reset` restores the blocks and gives everyone fresh loot, or let it happen on every refresh
* **Automatic** - cities register themselves as players travel near them. No per-city setup
* **Every setting in game** - `/betterend` opens a menu, `/betterend setup` walks you through it
* **Better Anti-Dupe support** - with [Better Anti-Dupe](https://github.com/ESMP-FUN/BetterAntiDupe) installed, claimed elytras are marked as the claimer's own

## Requirements

* **Minecraft 26.1+** on Paper, Folia, or Purpur
* **Java 25+**
* No datapack, resource pack or other plugin

> **Minecraft 26 only.** There is no 1.21 version, and this one won't start on 1.21.

## Quick start

1. Put the jar in `plugins/`: `-mc26` for Minecraft 26.1 or 26.2, `-mc263` for 26.3 or newer.
2. Restart. Everything is on by default.
3. Optional: run `/betterend setup` for the guided tour, or `/betterend` for the menu.

## Commands

| Command | What it does |
| --- | --- |
| `/betterend` | Settings menu |
| `/betterend setup` | Walks you through the settings |
| `/betterend list` | Every city found so far |
| `/betterend info <id>` | Details about one city |
| `/betterend tp <id>` | Teleport there |
| `/betterend snapshot <id>` | Save a copy so the city can be put back |
| `/betterend reset <id>` | Restore the blocks, fresh loot for everyone |
| `/betterend resetloot <id> <player>` | Let one player loot it again |
| `/betterend clearclaims <id>` | Let everyone claim the elytra again |
| `/betterend delete <id>` | Stop managing this city |
| `/betterend reload` | Re-read `config.yml` and `messages.yml` |

## Permissions

| Permission | Default | What it grants |
| --- | --- | --- |
| `betterend.admin` | op | Everything, including the settings menu |
| `betterend.bypass.protection` | op | Build inside protected cities |
| `betterend.discovery.notify` | op | Notice when a city is found |

## Links

**[Docs](docs/README.md)** | **[Discord](https://discord.gg/aWMU2JNXex)** | **[Issues](https://github.com/ESMP-FUN/BetterEndCities/issues)**

Source-available, non-commercial, see [LICENSE](LICENSE). Contributions welcome, see [CONTRIBUTING.md](CONTRIBUTING.md).
