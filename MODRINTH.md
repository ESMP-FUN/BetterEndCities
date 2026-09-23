<center>

# Better End Cities
**Every player gets the elytra, and the item frame stays an item frame.**

In vanilla, the first player to reach an End Ship <br>
takes the elytra and leaves the ship gutted.

[![Discord](https://img.shields.io/badge/join_-_Discord-gray?style=flat&logo=discord&logoSize=amd)](https://discord.gg/aWMU2JNXex)
[![Ko-Fi](https://img.shields.io/badge/support_-_KoFi-gray?style=flat&logo=kofi&logoSize=amd)](https://ko-fi.com/darkstarworks)


Other elytra plugins and datapacks swap the ship's item frame for a vault block, <br>
with no say in how the loot is handed out.

This plugin leaves the item frame a frame, <br>
and you decide exactly how the elytra can be claimed.

| **Vanilla** | **With this plugin** |
|---|---|
| First player takes the only elytra | Every player gets their own |
| Chests stay empty forever | Every player gets their own loot |
| Griefers strip the towers and ship | Protected |
| Cities never come back | Put them back whenever you like |
| Set up each city by hand | Found automatically |

</center>

---

<details>
<summary><b>Setup</b></summary>

1. Drop the jar into `plugins/`
2. Restart
3. There is no step 3

Everything is switched on by default. <br>
Cities add themselves as players travel near them.

Want to change something? `/betterend` opens a menu, <br>
or `/betterend setup` asks you one question at a time.

You never have to open a settings (`.yml`) file.
</details>

---

## What it does

**Elytras**
- Punch the ship's item frame and you get your own elytra. The frame, and its elytra, stay there for the next player
- Nobody can break the frame or knock the elytra out of it
- Choose: once per ship, once per player, or claimable again after every loot refresh
- Free, or charge any item (picked from your own inventory in game), XP levels, or both
- Optionally, each elytra a player buys costs double the one before

**Loot**
- Every player gets their own copy of what's in a city's chests
- Refreshes on a timer you set, and each city runs its own timer
- Chests players placed themselves are left alone

**Protection**
- Towers, bridges and the ship survive creepers, TNT, pistons and griefers
- Or protect only the ship, and let the towers be rebuilt on each refresh
- The empty space between towers is still free to build in

**Putting cities back**
- A copy of every city is saved the moment it's found
- `/betterend reset` puts the blocks back and gives everyone fresh loot
- Or let that happen by itself on every loot refresh

---

## Will it work on my server?

| | |
|---|---|
| **Server software** | Paper, Folia, or Purpur |
| **Minecraft** | 26.1 and up |
| **Java** | 25+ |
| **Anything else** | Nothing. No datapack, no resource pack, no dependencies |

> **Minecraft 26 only.** There is no 1.21 version.

Optional: with [Better Anti-Dupe](https://github.com/ESMP-FUN/BetterAntiDupe) installed, claimed elytras are marked as the claimer's own, so they never look like copies.

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
| `/betterend tp <id>` | Teleport there |
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
    amount: 0            # how many of the cost item, 0 = none
    levels: 0            # XP levels, 0 = none

loot:
  refresh-hours: 12      # 0 = never refresh

protection:
  scope: whole-city      # or ship-only

snapshot:
  auto-reset-on-refresh: false   # also put the blocks back every refresh

discovery:
  excluded-worlds: []    # leave a second End world completely vanilla
```

</details>

---

## Help

- **[Discord](https://discord.gg/aWMU2JNXex)** - ask me directly. Tell me "if it did X, I'd use it" and there's a good chance it ships
- **[Bug reports](https://github.com/ESMP-FUN/BetterEndCities/issues)** | **[Source](https://github.com/ESMP-FUN/BetterEndCities)**

<details>
<summary><b>Automatic bug reports and anonymous statistics</b></summary>

Both are on by default, and either can be switched off.

**Bug reports.** Only this plugin's own errors are sent, with IP addresses, file paths, passwords and player ids removed first. Each report says the plugin and Minecraft version, whether you run Folia, roughly how many cities you have, and what the plugin was doing. Nothing about your players or your world.

**Statistics.** Your storage type, claim setting, whether per-player loot is on, and a rough city count. No coordinates.

Set `metrics.enabled: false` to send nothing at all, or `metrics.error-reporting: false` for bug reports only.

</details>

<center>

**Paper | Folia | Purpur** <br>
**Minecraft 26.x** | **Java 25+** <br>
**No dependencies**

Free and actively maintained. <br>
Please consider donating: [Ko-Fi](https://ko-fi.com/darkstarworks) or [Patreon](https://patreon.com/cw/darkstarworks)

[![Servers](https://img.shields.io/endpoint?url=https%3A%2F%2Ffaststats.dev%2Fapi%2Fshields%2Fbetter-end-cities%3Fmetric%3Dservers%26color%3Dblueviolet%26icon%3D1&style=flat)](https://faststats.dev/project/better-end-cities)

</center>