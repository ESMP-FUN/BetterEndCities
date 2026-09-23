# Commands

Every command starts with `/betterend` and needs `betterend.admin` (ops have it). Players need no commands: they punch frames and open chests.

{% hint style="info" %}
Press Tab to complete commands and city numbers.
{% endhint %}

***

## Quick reference

| Command | What it does |
|---|---|
| `/betterend` | Open the settings menu (same as `/betterend menu`) |
| `/betterend setup` | Start the guided setup tour |
| `/betterend help` | List the commands |
| `/betterend list` | List every city found so far |
| `/betterend info <id>` | Show details about one city |
| `/betterend tp <id>` | Teleport to a city |
| `/betterend snapshot <id>` | Save a copy of the city's blocks |
| `/betterend reset <id>` | Put the blocks back and give everyone fresh loot |
| `/betterend resetloot <id> <player>` | Let one player loot the city again |
| `/betterend clearclaims <id>` | Let everyone claim from the city's ship again |
| `/betterend delete <id>` | Stop managing a city |
| `/betterend reload` | Re-read `config.yml` |
| `/betterend update [check\|download\|status\|restore\|ignore\|unignore]` | Check for and download new versions |

Leave out `<id>` while standing in a city to use that city.

***

## Command details

### `/betterend`

Opens the [settings menu](../getting-started/config-menu.md). In game only.

### `/betterend setup`

Starts the [setup tour](../getting-started/config-menu.md#the-setup-tour): five screens, saving as you go. In game only.

### `/betterend list`

Every registered city with its number, world and position. Click the position to teleport.

### `/betterend info <id>`

Shows the city's world, bounds, number of pieces, whether it has a ship, whether a copy is saved, and how long ago its loot countdown started. Run this first when something seems wrong.

### `/betterend tp <id>`

Teleports you on top of the city's base tower. In game only.

### `/betterend snapshot <id>`

Replaces the city's saved copy with its blocks as they are now. Normally done automatically when a city is found; run it after repairing a city by hand.

### `/betterend reset <id>`

Puts the blocks back from the saved copy and clears everyone's loot copies. Without a saved copy it only clears the loot.

{% hint style="warning" %}
A reset can suffocate players inside and erases anything built there. Check the city is empty first.
{% endhint %}

### `/betterend resetloot <id> <player>`

Clears one player's loot copies in one city. Blocks and other players are untouched. The player must have joined the server before.

### `/betterend clearclaims <id>`

Lets everyone claim from that city's ship again. Loot and blocks are untouched.

### `/betterend delete <id>`

Stops managing the city (protection, loot, claims) and deletes its saved copy.

{% hint style="warning" %}
**Nothing is demolished.** The city is found again the next time it loads, unless you exclude its world or turn off `discovery.enabled`.
{% endhint %}

### `/betterend reload`

Re-reads `config.yml` after you edit it by hand. Changes made in the menu apply without it. `database`, `metrics` and `update` changes need a restart.

### `/betterend update`

Checks [GitHub Releases](https://github.com/ESMP-FUN/BetterEndCities/releases) for a new version.

| Subcommand | What it does |
|---|---|
| `/betterend update` or `update check` | Look for a newer version |
| `/betterend update download` | Download it for the next restart (also `update install`) |
| `/betterend update status` | Show where things stand |
| `/betterend update restore` | Put the previous version back |
| `/betterend update ignore <version>` | Stop being told about one version |
| `/betterend update unignore <version>` | Undo that |

Downloads are checked for damage, and your current jar is backed up first. The update is installed when you restart.

{% hint style="info" %}
`/betterend update apply` can't install without a restart for this plugin, and says so. Don't confuse `/betterend update reload` with `/betterend reload`: the second one reloads settings.
{% endhint %}

### How much it does on its own

Set on the **Updates & Stats** page of `/betterend`, or in `config.yml`:

```yaml
update:
  mode: notify
  check-interval-hours: 6
```

| Mode | What happens |
|---|---|
| `off` | No checking |
| `check-only` | Check quietly; see `/betterend update status` |
| `notify` | **Default.** Tell staff, who can run `/betterend update download` |
| `download` | Same as `notify` |
| `auto-stage` | Download it for the next restart by itself |

***

## From the console

Works from the console: `list`, `info`, `snapshot`, `reset`, `resetloot`, `clearclaims`, `delete`, `reload`, `update`, `help`. From the console, always give the city number.

In game only: `menu`, `setup`, `tp`.

***

## Right after startup

For a second or two after the server starts, commands other than `help` reply:

```
Better End Cities is still starting up, try again in a moment.
```
