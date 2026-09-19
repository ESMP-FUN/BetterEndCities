# Commands

Everything lives under `/betterend`. There are no short forms.

{% hint style="info" %}
**Press Tab** and the game completes things for you, including city numbers, which are suggested from the cities you've actually found.
{% endhint %}

***

## Quick reference

| Command | What it does | Permission |
|---|---|---|
| `/betterend` | Open the settings menu | `betterend.admin` |
| `/betterend menu` | The same thing, spelled out | `betterend.admin` |
| `/betterend setup` | Guided setup tour | `betterend.admin` |
| `/betterend help` | List the commands | `betterend.admin` |
| `/betterend list` | Every city found so far | `betterend.admin` |
| `/betterend info <id>` | Details about one city | `betterend.admin` |
| `/betterend tp <id>` | Teleport to a city | `betterend.admin` |
| `/betterend snapshot <id>` | Save a copy of the city | `betterend.admin` |
| `/betterend reset <id>` | Put the blocks back, fresh loot for everyone | `betterend.admin` |
| `/betterend resetloot <id> <player>` | Let one player loot the city again | `betterend.admin` |
| `/betterend clearclaims <id>` | Let everyone claim the ship's elytra again | `betterend.admin` |
| `/betterend delete <id>` | Stop managing a city | `betterend.admin` |
| `/betterend reload` | Re-read `config.yml` | `betterend.admin` |
| `/betterend update <check\|download\|status\|restore\|ignore\|unignore>` | Check for and download new versions | `betterend.admin` |

Every command needs `betterend.admin`, which ops have by default. There are no commands for ordinary players. They just punch frames and open chests.

***

## Command details

### `/betterend`

Opens the [settings menu](../getting-started/config-menu.md). `/betterend menu` does exactly the same.

**In-game only**, because the menu is drawn by the player's game. From the console use `list`, `info`, `reset` and the rest.

### `/betterend setup`

Starts the [guided setup tour](../getting-started/config-menu.md#the-setup-tour): five screens, about two minutes, saving as you go.

**In-game only.**

### `/betterend list`

Every city you've found, with its number, world and coordinates. Works from the console.

### `/betterend info <id>`

Details for one city:

* Its size, and how many towers it has
* Whether it has a ship, and so an elytra frame
* Whether a copy has been saved
* Where it is in its loot countdown

This is the first thing to run when something isn't behaving as you expect.

### `/betterend tp <id>`

Teleports you there. **In-game only.**

### `/betterend snapshot <id>`

Saves a copy of that city's blocks right now, replacing any existing one.

This normally happens automatically when a city is found, so you'd only run it by hand after repairing a city yourself, or if automatic saving was switched off at the time.

### `/betterend reset <id>`

A full reset. Puts the blocks back from the saved copy **and** clears everyone's loot copies.

There has to be a saved copy. Check with `info`.

{% hint style="warning" %}
A reset rewrites blocks. Players inside can be shoved out of the way or suffocated, and anything built inside the city is erased. Check nobody's in there first.
{% endhint %}

### `/betterend resetloot <id> <player>`

Clears one player's loot copies for one city, so they can loot it again. Blocks and everyone else's copies are untouched.

The precise option. Good for making it up to a player, or for an event.

### `/betterend clearclaims <id>`

Clears who has claimed the elytra at one city, so everyone can claim from its ship again. Doesn't touch loot or blocks.

Use it after changing the claim setting, or to run an event.

### `/betterend delete <id>`

Stops the plugin managing a city. No more protection, no more per-player loot, no more elytra claims.

{% hint style="warning" %}
**This doesn't demolish anything.** Blocks stay exactly as they are. The city will also be **found again next time it loads in**, unless you turn finding cities off or exclude its world. `delete` on its own isn't permanent.
{% endhint %}

### `/betterend reload`

Re-reads `config.yml`. Use it after editing the file by hand. The in-game menu applies changes straight away and needs no reload.

### `/betterend update`

Checks for new versions against [GitHub Releases](https://github.com/ESMP-FUN/BetterEndCities/releases).

| Subcommand | What it does |
|---|---|
| `/betterend update` | The same as `check`, which is what you get with no subcommand |
| `/betterend update check` | Look for a newer release |
| `/betterend update download` | Download it, check it arrived intact, and **put it in place** for the next restart |
| `/betterend update install` | Another name for `download` |
| `/betterend update status` | Show where things stand |
| `/betterend update restore` | Put the previous version back, to undo a bad update |
| `/betterend update ignore <version>` | Stop being told about one specific version |
| `/betterend update unignore <version>` | Undo that |

Nothing is ever swapped out underneath a running server. Updates are put in place ready and applied when you restart. Downloads are checked for damage, and your current jar is backed up first.

{% hint style="warning" %}
**`/betterend update apply` won't do anything here.** It exists to apply a downloaded update without restarting, but that needs an optional extra this plugin doesn't include. It reports:

```
Better End Cities: hot reload is not available - restart the server to apply.
```

That's deliberate. This plugin includes storage code that can't safely be swapped out while the server is running. Restart to apply.

Note too that `/betterend update reload` and `/betterend reload` are **different commands**. The plain `/betterend reload` is the one you want for settings.
{% endhint %}

### How much it does on its own

By default it only **tells you** about a new version, and downloads nothing unless you ask. You can change that in `config.yml`:

```yaml
update:
  mode: notify              # off, check-only, notify, download, or auto-stage
  check-interval-hours: 6
```

| Mode | What happens |
|---|---|
| `off` | No checking at all |
| `check-only` | Check quietly. You only see it in `/betterend update status` |
| `notify` | **Default.** Check and tell admins. Downloads nothing |
| `download` | Check, download, verify, and put it in place for the next restart |
| `auto-stage` | Put it in place automatically as soon as an update appears |

***

## From the console

These work from the console: `list`, `info`, `snapshot`, `reset`, `resetloot`, `clearclaims`, `delete`, `reload`, `update`, `help`.

These are in-game only, because they open a menu or move you: `menu`, `setup`, `tp`.

***

## While the server is starting

Anything other than `help` replies with:

```
Better End Cities is still starting up, try again in a moment.
```

Setting up storage and loading your cities happens in the background, so there's a brief moment after startup where the plugin isn't ready yet. It sorts itself out in a second or two.
