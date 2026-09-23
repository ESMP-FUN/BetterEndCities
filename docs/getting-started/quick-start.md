# Quick Start

Everything is on as soon as the server starts. Use this page to see each feature working.

***

## What's on by default

| Feature | Default | What it does |
|---|---|---|
| **Finding cities** | on | Cities register as players travel near them |
| **Renewable elytras** | on, free | Each player can take one elytra from each ship |
| **Per-player loot** | on, 12 hours | Each player gets their own copy of a city's chests |
| **Protection** | on, whole city | City blocks can't be broken, built on or blown up |
| **Saved copies** | on | A copy of each city is saved when it's found |
| **Putting blocks back** | **off** | A refresh gives fresh loot only |

Putting blocks back is off because rewriting blocks can hurt players standing inside. [Turn it on](../guides/snapshots-and-resets.md#putting-blocks-back-on-every-refresh) if cities are only for farming.

***

## See it working

1. **Go to an End City.** When it loads, the console logs it:

   ```
   [BetterEndCities] Discovered End City #1 in world_the_end (1264,0,-368)..(1329,100,-303), 14 pieces
   ```

   Staff with `betterend.discovery.notify` also get a chat message they can click to teleport there.

2. **Punch the ship's elytra frame.** You get an elytra and the frame keeps its own. A second player can do the same.

3. **Open a chest and take the loot.** Another player opening the same chest still finds it full.

4. **Try breaking a tower block** on a non-op account. It's stopped, and a message above the hotbar says why.

5. **Check the city** with `/betterend list` and `/betterend info 1`.

{% hint style="info" %}
**Nothing in the console?** The city has to load in. Flying past fast can outrun that, so stop for a moment. See [Troubleshooting](../troubleshooting.md#cities-arent-being-found).
{% endhint %}

{% hint style="warning" %}
**Testing protection as an op?** Ops are let through by default. Use a normal account, or remove `betterend.bypass.protection` from yourself.
{% endhint %}

***

## Change a setting

```
/betterend          # the settings menu
/betterend setup    # a guided tour, five screens
```

Ops get a one-time reminder to run the tour when they join, until it's finished or skipped.

{% content-ref url="config-menu.md" %}
[config-menu.md](config-menu.md)
{% endcontent-ref %}

***

## Three settings worth deciding

### How often can a player get an elytra?

`elytra.claim-mode`: `per-ship` (default, one per ship), `per-refresh` (again after each loot refresh) or `global` (one in total). [More](../guides/elytra-claims.md#claim-modes)

### Should an elytra cost something?

Free by default. Charge an item, XP levels, or both, in `/betterend` on **Elytra Frames** and **Choose Cost Item**. [More](../guides/elytra-claims.md#claim-cost)

### How often does loot come back?

`loot.refresh-hours`, 12 by default. `0` means never, so each player loots each chest once. [More](../guides/per-player-loot.md)

***

## What's next?

{% content-ref url="basic-configuration.md" %}
[basic-configuration.md](basic-configuration.md)
{% endcontent-ref %}

{% content-ref url="../guides/elytra-claims.md" %}
[elytra-claims.md](../guides/elytra-claims.md)
{% endcontent-ref %}
