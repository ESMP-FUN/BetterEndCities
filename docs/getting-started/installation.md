# Installation

Install the jar and restart. There is no per-city setup afterwards.

## Requirements

* **Minecraft 26.1 or newer**, on Paper, Folia or Purpur
* **Java 25 or newer** (Minecraft 26 needs it)

{% hint style="warning" %}
**Minecraft 26 only.** There is no 1.21 version, and this one won't start on 1.21. If you need 1.21, ask on Discord.
{% endhint %}

## Pick the right download

Get it from [GitHub Releases](https://github.com/ESMP-FUN/BetterEndCities/releases).

| Your server | Download |
|---|---|
| Minecraft 26.1 or 26.2 | `BetterEndCities-<version>-mc26.jar` |
| Minecraft 26.3 or newer | `BetterEndCities-<version>-mc263.jar` |

The only difference: the 26.3 download also protects cushions, which 26.3 added.

* **`-mc263` on 26.1 or 26.2** won't load, and the console says why. Swap the jar.
* **`-mc26` on 26.3** works, without cushion protection. Swap it when convenient.

The built-in update check only ever offers the download your server needs.

## Install

1. Stop the server with `/stop`.
2. Put the jar in your server's `plugins/` folder.
3. Start the server and watch the console:

```
[BetterEndCities] Better End Cities starting on Paper...
[BetterEndCities] Database pool initialized (SQLITE)
[BetterEndCities] Loaded 0 End Cities into cache
[BetterEndCities] Better End Cities ready.
[BetterEndCities] FastStats Metrics: Enabled
```

`Loaded 0 End Cities` is normal on a first start. Cities register as players travel to them.

{% hint style="success" %}
**Errors instead?** See [Troubleshooting](../troubleshooting.md).
{% endhint %}

The plugin creates `plugins/BetterEndCities/`:

```
plugins/BetterEndCities/
├── config.yml       # settings (all changeable in game)
├── database.db      # cities, claims and loot
└── snapshots/       # a saved copy of each city
```

## Check it's working

Run `/betterend` in game. If the settings menu opens, it's installed.

From the console, `/betterend list` shows the cities found so far (none on a fresh install).

{% hint style="success" %}
**Next:** [Quick Start](quick-start.md) shows each feature working in game.
{% endhint %}

## Better Anti-Dupe

If [Better Anti-Dupe](https://github.com/ESMP-FUN/BetterAntiDupe) is installed, it's picked up automatically. Claimed elytras are marked as the claimer's own, so they never look like copies. The cost-item picker also warns you if you pick an item Better Anti-Dupe watches.

## Updating

1. Copy `plugins/BetterEndCities/` somewhere safe.
2. Stop the server.
3. Replace the old jar with the new one.
4. Start the server.

Your `config.yml` is left as it is. Settings added in a newer version aren't written into it; they run on their defaults until you change them with `/betterend`, or add them by hand from the [config.yml reference](../configuration/config.yml.md).

`/betterend update` can also check for a new version and download it for the next restart. See [Commands](../reference/commands.md#betterend-update).

## What's next?

{% content-ref url="quick-start.md" %}
[quick-start.md](quick-start.md)
{% endcontent-ref %}

***

## Tips

{% hint style="info" %}
**Folia** works with nothing to switch on. The console shows `starting on Folia...`.
{% endhint %}

{% hint style="info" %}
**MySQL** is only needed when several servers share one End. See [Storage](../configuration/storage.md).
{% endhint %}

{% hint style="info" %}
**Existing worlds are fine.** Cities that were already looted still register and get protection, saved copies and fresh per-player loot. Players who took an elytra before you installed this can claim one again.
{% endhint %}
