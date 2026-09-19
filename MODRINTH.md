<center>

# Better End Cities

**Every player gets the elytra, and the item frame stays an item frame.**

Free forever. Source available.

[![Discord](https://img.shields.io/badge/join_-_Discord-gray?style=flat&logo=discord&logoSize=amd)](https://discord.gg/qwYcTpHsNC)
[![Ko-Fi](https://img.shields.io/badge/support_-_KoFi-gray?style=flat&logo=kofi&logoSize=amd)](https://ko-fi.com/darkstarworks)

</center>

The first player to reach an End Ship takes *the* elytra. Everyone after them finds an empty frame and gutted chests.

| **Vanilla** | **With this plugin** |
|---|---|
| First player takes the only elytra | Every player gets their own |
| Chests stay empty forever | Every player gets their own loot |
| Griefers strip the towers and ship | Protected |
| Cities never come back | Put them back whenever you like |
| Set up each city by hand | Found automatically |

Other elytra plugins swap the ship's item frame for a vault block, then make you install a datapack and hand out a key item. This one leaves the frame alone. Players punch it exactly like they always have.

---

## Setup

1. Drop the jar into `plugins/` (`-mc26` for Minecraft 26.1 to 26.2, `-mc263` for 26.3 and up)
2. Restart
3. There is no step 3

Everything is switched on already. Cities add themselves as players travel near them.

Want to change something? `/betterend` opens a menu, or `/betterend setup` asks you one question at a time. You never have to open a settings file.

---

## What it does

**Elytras**
- Punch the ship's item frame and you get your own elytra. The frame, and its elytra, stay there for the next player
- Nobody can break the frame or knock the elytra out of it
- Choose: once per ship, once per player, or claimable again after every loot refresh
- Free, or charge any item you like. Pick it out of your own inventory in-game

**Loot**
- Every player gets their own copy of what's in a city's chests
- Refreshes on a timer you set, and each city runs its own timer
- Chests players placed themselves are left alone

**Protection**
- Towers, bridges and the ship survive creepers, TNT and griefers
- The empty space between them is still yours to build in

**Putting cities back**
- A copy of every city is saved the moment it's found
- `/betterend reset` puts the blocks back and gives everyone fresh loot
- Or let that happen by itself on every loot refresh

---

## Will it work on my server?

| | |
|---|---|
| **Server software** | Paper, Folia, or Purpur |
| **Minecraft** | 26.1 and up. Use the `-mc26` download on 26.1 or 26.2, and `-mc263` on 26.3 or newer |
| **Java** | 25+ |
| **Anything else** | Nothing. No datapack, no resource pack, no other plugins |

> **Minecraft 26 only.** This plugin uses pop-up menus and world information that don't exist in 1.21. There is no 1.21 version, and it will not start on one.

Optional: with [Better Anti-Dupe](https://github.com/ESMP-FUN/BetterAntiDupe) installed, claimed elytras are marked as genuine, so renewable elytras never look like copies.

---

## Reference

<details>
<summary><b>Commands</b></summary>

| Command | What it does |
|---|---|
| `/betterend` | The settings menu |
| `/betterend setup` | Walks you through the settings |
| `/betterend list` | Every city found so far |
| `/betterend info <id>` | Details about one city |
| `/betterend tp <id>` | Go there |
| `/betterend snapshot <id>` | Save a copy of the city so it can be put back |
| `/betterend reset <id>` | Put the blocks back, fresh loot for everyone |
| `/betterend resetloot <id> <player>` | Let one player loot it again |
| `/betterend clearclaims <id>` | Let everyone claim the elytra again |
| `/betterend delete <id>` | Stop managing this city |
| `/betterend reload` | Re-read the settings file |

</details>

<details>
<summary><b>Permissions</b></summary>

| Permission | What it does | Default |
|---|---|---|
| `betterend.admin` | Everything | OP |
| `betterend.bypass.protection` | Build inside cities | OP |
| `betterend.discovery.notify` | Get told when a city is found | OP |

</details>

<details>
<summary><b>Settings people change</b></summary>

```yaml
elytra:
  claim-mode: per-ship   # per-ship, per-refresh, or global
  cost:
    amount: 0            # 0 = free

loot:
  refresh-hours: 12      # 0 = never refresh

protection:
  piece-padding: 3       # how far protection reaches past each tower

snapshot:
  auto-reset-on-refresh: false   # also put the blocks back every refresh

discovery:
  excluded-worlds: []    # leave a second End world completely vanilla
```

</details>

---

## Help

- **[Discord](https://discord.gg/qwYcTpHsNC)** - ask me directly. Tell me "if it did X, I'd use it" and there's a good chance it ships
- **[Bug reports](https://github.com/ESMP-FUN/BetterEndCities/issues)** · **[Source](https://github.com/ESMP-FUN/BetterEndCities)**

<details>
<summary><b>Automatic bug reports and anonymous statistics</b></summary>

Both are on by default, and either can be switched off.

**Bug reports.** When something in the plugin goes wrong, it's reported automatically, so it gets fixed without you having to notice and write it up. Only this plugin's errors are ever sent. Before anything leaves your server, addresses, file paths containing your username, database passwords and player ids are stripped out and replaced. Each report says which plugin version, which Minecraft version, whether you run Folia, roughly how many cities you have, and what the plugin was doing. Player names, chat, inventories and anything about your world are never included.

**Statistics.** Which storage you use, your claim setting, whether per-player loot is on, and roughly how many cities you have. That's it. City coordinates are never sent, and the city count is a range, not a list.

Set `metrics.enabled: false` to send nothing at all, or `metrics.error-reporting: false` for bug reports only.

</details>

<div align="center">

**Paper · Folia · Purpur** · **Minecraft 26.1+** · **Java 25+** · **No dependencies**

Made with Kotlin by [darkstarworks](https://github.com/darkstarworks)

Free and actively maintained. (Anonymous) donations are very welcome: [Ko-Fi](https://ko-fi.com/darkstarworks)

[![Servers](https://img.shields.io/endpoint?url=https%3A%2F%2Ffaststats.dev%2Fapi%2Fshields%2Fbetter-end-cities%3Fmetric%3Dservers%26color%3Dblueviolet%26icon%3D1&style=flat)](https://faststats.dev/project/better-end-cities)

</div>
