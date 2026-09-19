# Quick Start

Most plugin quick-starts give you a checklist. This one doesn't, because there's no setup step. Everything is on the moment the server starts.

This page explains what's already running, and how to watch it work.

***

## What's already on

Straight out of the box, with nothing to configure:

| Feature | Default | What it means |
|---|---|---|
| **Finding cities** | on | Cities register themselves as players travel near them |
| **Renewable elytras** | on, free | Every player can punch the ship's frame for their own elytra, once per ship |
| **Per-player loot** | on, 12h | Every player gets their own copy of a city's chests |
| **Protection** | on | City blocks can't be broken, built on, or blown up |
| **Saved copies** | on | A copy of each city is kept as soon as it's found |
| **Putting blocks back** | **off** | A reset gives fresh loot only, unless you turn this on |

The one thing deliberately left off is [putting the blocks back on every refresh](../guides/snapshots-and-resets.md#putting-blocks-back-on-every-refresh). Rewriting blocks while players might be standing there is disruptive, so you choose when to switch it on.

***

## Watch it happen

### 1. Go to the End

Travel to an End City, or teleport to one. As soon as you're close enough for it to load in, the console says:

```
[BetterEndCities] Discovered End City #1 in world_the_end (1264,0,-368)..(1329,100,-303), 14 pieces
```

Ops with `betterend.discovery.notify` also get a message in chat they can click to teleport there.

{% hint style="info" %}
**Nothing logged?** The city has to actually load in. Flying past fast on an elytra sometimes outruns that. Stop and wait a moment. See [Troubleshooting](../troubleshooting.md#cities-arent-being-found).
{% endhint %}

### 2. Punch the elytra frame

Find the End Ship and punch its elytra item frame, exactly like vanilla.

An elytra goes into your inventory. **The frame keeps its elytra.** Ask a second player to punch it and they get one too.

By default a small floating note above the frame reads "Punch to claim", or shows the cost if you've set one.

### 3. Open a chest

Open any chest in the city and loot it. Now have another player open the same chest. They see it full and untouched.

### 4. Try to grief it

Break a purpur block in a tower. You can't, unless you're an op (ops are allowed to build inside cities by default). A message appears just above your hotbar explaining why.

{% hint style="warning" %}
**Testing protection as an op?** It'll look broken, because ops are allowed through it by default. Test on a normal account, or take `betterend.bypass.protection` away from yourself.
{% endhint %}

### 5. Look at the city

```
/betterend list
/betterend info 1
```

`info` shows the city's size, how many pieces it has, whether a ship was found, whether a copy is saved, and where it is in its loot cycle.

***

## Changing anything

Two ways, neither of which involves opening a file:

```
/betterend          # the settings menu, jump straight to any setting
/betterend setup    # the guided tour, five screens, about 2 minutes
```

The tour explains each setting in plain words and saves as you go. Ops get a one-time reminder to run it when they join, until it's done or skipped.

{% content-ref url="config-menu.md" %}
[config-menu.md](config-menu.md)
{% endcontent-ref %}

***

## The three decisions worth making

Everything else can stay as it is. These three actually change how your server plays.

### How often can a player get an elytra?

`elytra.claim-mode`. Choose `per-ship` (the default, one per player per ship), `per-refresh` (claimable again on every loot refresh), or `global` (one per player, ever).

`global` keeps elytras genuinely rare. `per-refresh` makes them something players can come back for. [More](../guides/elytra-claims.md#claim-modes)

### Should an elytra cost something?

Free by default. You can charge any item, including custom items from other plugins, picked from your own inventory in `/betterend`, **Choose Cost Item**. [More](../guides/elytra-claims.md#claim-cost)

### How often does loot come back?

`loot.refresh-hours`, 12 by default. Set it to `0` to never refresh, so each player gets exactly one copy of each city forever. [More](../guides/per-player-loot.md)

***

## What's next?

{% content-ref url="basic-configuration.md" %}
[basic-configuration.md](basic-configuration.md)
{% endcontent-ref %}

{% content-ref url="../guides/elytra-claims.md" %}
[elytra-claims.md](../guides/elytra-claims.md)
{% endcontent-ref %}
