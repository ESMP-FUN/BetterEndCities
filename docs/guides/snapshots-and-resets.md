# Saved Copies and Resets

The plugin keeps a saved copy of each city's blocks. With one, a city can be rebuilt exactly as it first generated, undoing griefing or making a looted city new again.

***

## Saved automatically

```yaml
snapshot:
  auto-capture: true
```

On by default. When a city is found, a copy of its blocks is saved to a file, so there's always something to restore from without anyone having to remember to make one.

Copies live in `plugins/BetterEndCities/snapshots/`, one file per city:

```
plugins/BetterEndCities/snapshots/
├── city_1.dat
├── city_2.dat
└── city_3.dat
```

{% hint style="info" %}
**The copy is made when the city is found**, which is normally before players get to it, so it's a copy of an untouched city. If a city is found after players have already wrecked it, the copy is of the wrecked version. Repair it by hand, then run `/betterend snapshot <id>` to save a fresh copy.
{% endhint %}

### The size limit

```yaml
snapshot:
  max-cells: 3000000
```

A limit on how many blocks one copy may hold, so a strange or enormous structure can't eat all your memory. Three million is far larger than any real End City, so you should never need to change it.

***

## Saving a copy yourself

```
/betterend snapshot <id>
```

Saves a new copy of that city right now, replacing any existing one. Use it after repairing a city by hand, or if automatic saving was switched off when the city was found.

***

## Resetting a city

```
/betterend reset <id>
```

Puts the city's blocks back from its saved copy **and** clears everyone's loot copies. The city is new again for every player.

There has to be a saved copy for this to work. `/betterend info <id>` tells you whether there is one.

***

## Putting blocks back on every refresh

```yaml
snapshot:
  auto-reset-on-refresh: false
```

**Off by default.** With it on, every [loot refresh](per-player-loot.md#when-loot-comes-back) also rebuilds the city's blocks, so griefing and player changes are undone on the same schedule as the loot.

{% hint style="warning" %}
**Why it's off by default.** Rebuilding rewrites the whole city at once:

* Players standing inside can be suffocated or shoved out of the way
* Anything a player has built **inside the city** is erased
* On a large city it's a lot of blocks changing at once

None of that matters on a server where cities are just something to go and farm. All of it matters where players treat cities as bases. So the choice is yours.
{% endhint %}

### Turn it on when

* Cities are something players visit, not somewhere they live
* You want them pristine every cycle without staff having to do anything
* You've told players not to build inside cities

### Leave it off when

* Players make bases in End Cities
* You'd rather undo griefing yourself, when you notice it
* Your refresh time is short, so it would happen often

***

## Choosing what comes back

The two settings give you four sensible combinations:

| `loot.refresh-hours` | `auto-reset-on-refresh` | What you get |
|---|---|---|
| `12` | `false` | **Default.** Loot comes back, the city stays as players left it |
| `12` | `true` | Cities completely renew on a cycle, pristine every time |
| `0` | `false` | One-shot cities. Each player loots once, ever |
| `0` | `true` | Loot never comes back, but you can still rebuild the blocks by hand |

***

## Smaller options

A full reset isn't always what you want:

| Command | Puts blocks back | Clears loot for | Affects |
|---|---|---|---|
| `/betterend reset <id>` | yes | everyone | The whole city |
| `/betterend resetloot <id> <player>` | no | one player | That player only |
| `/betterend clearclaims <id>` | no | nobody | Elytra claims only |

Use `resetloot` to make it up to one player, and `clearclaims` to reopen a ship's elytra without touching anything else.

***

## Where they're kept

Saved copies are compressed and kept as files on disk rather than in the database, so they don't bloat your storage. A typical city compresses to well under a megabyte.

Deleting a city with `/betterend delete <id>` stops the plugin managing it. Its saved copy is no longer used, and you can delete `city_<id>.dat` by hand if you want the space back.

{% hint style="info" %}
**Back up `plugins/BetterEndCities/` before updating.** Saved copies are the one thing that can't be recreated once a city has been looted or griefed, because the original blocks are gone.
{% endhint %}

***

## What's next?

{% content-ref url="city-discovery.md" %}
[city-discovery.md](city-discovery.md)
{% endcontent-ref %}
