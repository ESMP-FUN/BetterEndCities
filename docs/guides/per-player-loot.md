# Per-Player Loot

Every player who opens an End City chest sees their own private copy of what's inside. The first player through no longer empties the city for everyone else.

***

## How it works

The first time anyone opens a city chest, Better End Cities remembers what rolled into it. Every player who opens that chest afterwards gets their own copy of it.

Your copy is yours. Take from it, add to it, leave things in it. No other player sees your changes, and you can't take from theirs.

Chests stay chests, with the normal look, sound and opening animation.

***

## When loot comes back

This is the part worth understanding, because nothing runs in the background waiting for a clock.

Each city works on its own:

1. The first player to loot a city that isn't already counting down **starts the countdown** for that city
2. While it's counting down, every player who opens a chest gets their own copy
3. Once the time is up, **the next player to arrive** triggers the refresh. All copies are cleared and the countdown starts again

So loot comes back because a player turned up, not because a timer went off somewhere.

```yaml
loot:
  refresh-hours: 12    # 0 = never refresh
```

### Why it works that way

* **Nothing happens all at once.** A hundred cities don't all refresh at midnight. Each one is on its own clock, started by its own first visitor.
* **Cities nobody visits cost nothing.** A city no one has been to in a month does no work at all.
* **No background scanning.** Your server isn't checking a list of cities every few seconds.

The trade-off: a city's loot doesn't come back at the exact moment the time is up, but on the next visit after that. For loot that's the right behaviour anyway, since refreshing a city nobody is standing in achieves nothing.

### Choosing a time

| Value | How it feels |
|---|---|
| `6` to `12` | Cities are a regular thing to go and do. Good for smaller or busier servers |
| `24` to `72` | Cities are a roughly weekly activity |
| `168` | Once a week. Cities stay special |
| `0` | Never. Each player gets exactly one copy of each city, forever |

`0` is worth considering if you want the End to be finite. Everyone gets a fair first run, and that's it.

***

## What counts as city loot

Only chests that **came with the city** are treated as city loot. The plugin knows the city's exact shape, so this is precise.

* **The city's own chests, barrels and other containers** get per-player copies
* **A chest a player places inside the city** stays completely normal, shared and untouched

That difference matters. Players can build a base inside an End City and use their own storage as usual, without it turning into per-player copies or being wiped when the city refreshes.

{% hint style="info" %}
**Hoppers can't reach city chests.** Moving items into or out of a city chest with a hopper is blocked, so nobody can quietly drain the loot everyone else is about to get a copy of.

Hoppers attached to a chest a **player placed** inside a city work exactly as normal. The plugin tells the two apart by a marker written when the block is placed, not by where it is.
{% endhint %}

***

## Turning it off

```yaml
loot:
  enabled: false
```

Chests go back to normal, shared and first-come-first-served. The elytra frames are separate and keep working.

***

## Admin commands

| Command | What it does |
|---|---|
| `/betterend reset <id>` | Clear everyone's copies **and** put the blocks back, a full reset |
| `/betterend resetloot <id> <player>` | Clear one player's copies for that city, so they can loot it fresh |
| `/betterend info <id>` | Show where the city is in its countdown |

`resetloot` is the precise one. Good for making it up to a player after a bug, or for an event, without resetting the city for everyone.

***

## How this interacts with elytra claims

If `elytra.claim-mode` is `per-refresh`, elytra claims come back **with the loot**. A refresh then reopens the chests and the ship's elytra at the same time.

With `per-ship` or `global` the two are separate. Loot comes back on its own timer, and elytra claims follow their own rule.

***

## What's next?

{% content-ref url="snapshots-and-resets.md" %}
[snapshots-and-resets.md](snapshots-and-resets.md)
{% endcontent-ref %}
