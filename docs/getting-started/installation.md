# Installation

Drop a jar in a folder, restart. That's the whole thing. There's no per-city setup to do afterwards.

## Before you start

* **Minecraft 26.1 or newer**, on Paper, Folia, or Purpur
* **Java 25 or newer**

{% hint style="warning" %}
**Minecraft 26 only.** This plugin uses pop-up menus and world information that don't exist in 1.21, so there is no 1.21 build and it will not start on one. If you need 1.21 support, say so on Discord. It's a rewrite of two large parts of the plugin rather than a quick version bump, so it depends on how many people ask.
{% endhint %}

{% hint style="info" %}
**Why Java 25?** Minecraft 26 runs on it. Paper 26.1 is built for Java 25, so an older Java can't read it at all.
{% endhint %}

## Download

* [GitHub Releases](https://github.com/ESMP-FUN/BetterEndCities/releases) - every version, with changelogs

**There are two downloads. Pick the one for your Minecraft version:**

| Your server | Download |
|---|---|
| Minecraft 26.1 or 26.2 | `BetterEndCities-<version>-mc26.jar` |
| Minecraft 26.3 or newer | `BetterEndCities-<version>-mc263.jar` |

The 26.3 download adds protection for cushions, which 26.3 introduced and which the older download can't know about. Apart from that the two are the same.

Get it wrong and nothing breaks either way:

* **`-mc263` on a 26.1 or 26.2 server** won't load at all, and the console says why. Swap the jar and restart.
* **`-mc26` on a 26.3 server** loads and works normally, you just don't get cushion protection. Swap it when convenient.

The built-in update check knows which one your server needs and only ever offers you that one.

## Installation steps

### 1. Stop your server

Properly, with `/stop`.

### 2. Drop the jar in

Move the jar you downloaded into your server's `plugins/` folder.

```
your-server/
├── plugins/
│   ├── BetterEndCities-<version>-mc26.jar   <- here
│   └── ... other plugins
└── ...
```

### 3. Start your server

Watch the console:

```
[BetterEndCities] Better End Cities starting on Paper...
[BetterEndCities] Database pool initialized (SQLITE)
[BetterEndCities] Loaded 0 End Cities into cache
[BetterEndCities] Better End Cities ready.
[BetterEndCities] FastStats Metrics: Enabled
```

`Better End Cities ready.` comes before the metrics line on purpose. The plugin is fully working at that point, and the optional extras start afterwards.

`Loaded 0 End Cities` is correct on a first start. Cities register themselves as players travel, not up front.

{% hint style="success" %}
**Seeing errors instead?** Check [Troubleshooting](../troubleshooting.md).
{% endhint %}

### 4. Check the folder it made

Better End Cities creates `plugins/BetterEndCities/`:

```
plugins/BetterEndCities/
├── config.yml       # Settings (also changeable in-game)
├── database.db      # Where cities and loot are remembered
└── snapshots/       # A saved copy of each city
```

Two more files appear only when they're needed:

* `materials.yml` - written when [Better Anti-Dupe](https://github.com/ESMP-FUN/BetterAntiDupe) is installed, listing the items it watches
* `ownership-key` - a marker used by Better Anti-Dupe

You don't need to open any of them. Everything in `config.yml` can be changed in-game.

## Check it's working

```
/betterend
```

The settings menu opens as a pop-up. If it does, you're installed.

Prefer to check from the console? `/betterend list` works there too, and prints the list of cities found so far (empty on a fresh install).

{% hint style="success" %}
**Next:** [Quick Start](quick-start.md), what's already running and how to see it happen in-game.
{% endhint %}

## Optional: Better Anti-Dupe

If you run [Better Anti-Dupe](https://github.com/ESMP-FUN/BetterAntiDupe), Better End Cities spots it automatically. Claimed elytras are marked as belonging to the player who claimed them, so a renewable elytra never looks like a copy.

Nothing to set up. Install both and it works. The cost-item picker also warns you if you choose an item Better Anti-Dupe watches.

## Updating

1. Stop the server
2. Replace the old jar with the new one
3. Start the server

Anything missing in the database is created on start, and your existing `config.yml` is left exactly as it is.

{% hint style="info" %}
**New settings don't appear in an existing `config.yml`.** The file is only written when it's missing, so settings added in a later version won't show up in yours. They simply run on their built-in defaults, which are always the sensible ones.

To see and change a newly added setting, either add the line by hand (the [config.yml reference](../configuration/config.yml.md) lists them all) or use `/betterend`. The menu always shows every current setting, whatever is in the file.
{% endhint %}

{% hint style="warning" %}
**Back up first.** Copy `plugins/BetterEndCities/` somewhere safe before updating. It holds which cities you've found, who has looted what, and the saved copies of each city.
{% endhint %}

Better End Cities can also check for updates itself. `/betterend update` looks for a new release and can download it ready for your next restart. See [Commands](../reference/commands.md#betterend-update).

## What's next?

{% content-ref url="quick-start.md" %}
[quick-start.md](quick-start.md)
{% endcontent-ref %}

***

## Quick tips

{% hint style="info" %}
**Folia:** supported out of the box, with nothing to switch on. The console line tells you which server type was found (`starting on Folia...`).
{% endhint %}

{% hint style="info" %}
**MySQL:** not needed. The default storage needs no setup at all. If you run several servers that should share their cities, switch to MySQL in [config.yml](../configuration/storage.md).
{% endhint %}

{% hint style="info" %}
**Existing worlds are fine.** Cities that were already looted still register, and still get protection, saved copies and per-player loot. Players who already took an elytra before you installed this can claim one again, because the plugin starts with no history of who has claimed what.
{% endhint %}
