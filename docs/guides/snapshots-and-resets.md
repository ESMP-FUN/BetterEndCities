# Saved Copies and Resets

A saved copy of a city's blocks lets you rebuild it exactly as it was, undoing griefing.

***

## Saved automatically

`snapshot.auto-capture` (on) saves a copy of each city when it's found, usually before any player reaches it. Copies are compressed files in `plugins/BetterEndCities/snapshots/`, one per city (`city_1.dat`, `city_2.dat`, and so on), usually well under a megabyte each.

{% hint style="info" %}
**Found after it was wrecked?** Then the copy is of the wrecked city. Repair it by hand, then run `/betterend snapshot <id>` to save a new copy.
{% endhint %}

`snapshot.max-cells` (`3000000`) caps how many blocks one copy may hold. It's far above any real city; leave it.

***

## Save a copy yourself

```
/betterend snapshot <id>
```

Replaces the city's saved copy with its blocks as they are now. Use it after repairing a city, or if automatic saving was off when the city was found.

***

## Reset a city

```
/betterend reset <id>
```

Puts the city's blocks back from its saved copy and clears everyone's loot copies. Without a saved copy it only clears the loot. `/betterend info <id>` shows whether a copy is saved.

A ship's dragon head that a player took (with `protection.dragon-head-takeable`) is never put back.

***

## Putting blocks back on every refresh

`snapshot.auto-reset-on-refresh` (off). When on, every [loot refresh](per-player-loot.md#when-loot-comes-back) also puts the city's blocks back.

{% hint style="warning" %}
Putting blocks back rewrites the whole city at once. Players inside can be suffocated or pushed out, and anything built inside the city is erased.
{% endhint %}

* **Turn it on** when cities are for farming, you want them pristine every cycle, or you use `protection.scope: ship-only`
* **Leave it off** when players build bases in cities, or your refresh time is short

| `loot.refresh-hours` | `auto-reset-on-refresh` | Result |
|---|---|---|
| `12` | `false` | **Default.** Loot comes back, blocks stay as players left them |
| `12` | `true` | The whole city renews each cycle |
| `0` | `false` | Each player loots once, ever |
| `0` | `true` | Loot never comes back; blocks only return with `/betterend reset` |

***

## Smaller resets

| Command | Puts blocks back | Clears loot for | Affects |
|---|---|---|---|
| `/betterend reset <id>` | yes, if a copy is saved | everyone | the whole city |
| `/betterend resetloot <id> <player>` | no | one player | that player |
| `/betterend clearclaims <id>` | no | nobody | elytra claims only |

***

## Deleting a city

`/betterend delete <id>` stops managing the city and deletes its saved copy. The blocks in the world stay as they are.

{% hint style="info" %}
**Back up `plugins/BetterEndCities/` before updating.** A saved copy can't be recreated once the city has been looted or griefed.
{% endhint %}

***

## What's next?

{% content-ref url="city-discovery.md" %}
[city-discovery.md](city-discovery.md)
{% endcontent-ref %}
