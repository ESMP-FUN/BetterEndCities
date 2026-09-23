# Troubleshooting

Find your problem, then try the fixes in order.

***

## The plugin won't load

**`Unsupported class file major version`, or it's missing from `/plugins`**
The server runs Java 24 or older. Install Java 25 or newer, and check with `java -version` on the server machine.

**`Unknown API version`, or it disables itself on a 1.21 server**
The plugin is for Minecraft 26 only. See [Installation](getting-started/installation.md#requirements).

**It won't load on 26.1 or 26.2**
You have the `-mc263` download. Use `-mc26`. See [Pick the right download](getting-started/installation.md#pick-the-right-download).

**Nothing in the log at all**
Put the jar directly in `plugins/`, not in a folder inside it, and use the jar from [Releases](https://github.com/ESMP-FUN/BetterEndCities/releases).

***

## Cities aren't being found

Check these in order, then run `/betterend list`:

1. **It hasn't loaded yet.** Stop flying and wait a moment near the city.
2. **The world is excluded.** Check `discovery.excluded-worlds`.
3. **Finding cities is off.** Set `discovery.enabled: true`.
4. **It isn't a generated city.** Hand-built or pasted cities can't register. See [Finding Cities](guides/city-discovery.md#which-cities-can-register).

**Cities in areas loaded at startup are missed**
Set `discovery.startup-sweep: true` (the default).

***

## Elytra claims aren't working

**Punching the frame does nothing**

1. Check `elytra.enabled: true` and that the city shows in `/betterend list`.
2. Check the city has a ship with `/betterend info <id>`. Not every city has one.
3. If you set a price, the player must be able to pay. Right-clicking the frame shows their price.

**A player can't claim again**
That's the claim mode: `per-ship` needs another ship, `global` allows one in total, `per-refresh` waits for the city's loot to come back. `/betterend clearclaims <id>` reopens a ship.

**The cost item isn't accepted**
The price is one exact item, name and enchantments included. An ordinary item won't pay for a named one. Pick it again with **Choose Cost Item**, and don't edit `elytra.cost.item` by hand.

**No floating note above the frame**
Check `elytra.text-display: true`. Client mods that hide floating text also hide it.

***

## Per-player loot isn't working

**Everyone sees the same chest**
Check `loot.enabled: true` and that the city is registered. Chests players placed, or chests that held a player's items when the plugin was installed, stay shared on purpose.

**Loot never comes back**
Check `loot.refresh-hours` isn't `0`. Loot comes back on the first visit after the time is up, not before.

**A player lost items they stored in a city chest**
A player's copy is cleared when the city refreshes, including anything they put in it. Tell players not to store items in city chests.

***

## Protection isn't working

**I can break blocks in a city**
You're probably an op, and ops have `betterend.bypass.protection`. Test on a normal account, or:

```
/lp user <you> permission set betterend.bypass.protection false
```

**Players can build between the towers**
Intended: only the towers, bridges and ship are protected. If trim just outside a tower breaks, raise `protection.piece-padding` by one or two.

**Towers can be broken but the ship can't**
`protection.scope` is `ship-only`. Set it to `whole-city` to protect everything.

**Explosions still damage the city**
Set `protection.block-explosions: true`.

**Cushions can be placed or taken in a city (26.3)**
You're running the `-mc26` download. Swap to `-mc263` and restart.

***

## Resets and saved copies

**`/betterend reset` doesn't put the blocks back**
The city has no saved copy. Repair it if needed, then run `/betterend snapshot <id>`.

**A reset erased a player's base**
Putting blocks back erases anything built inside the city. Keep `snapshot.auto-reset-on-refresh: false` if players build there.

**Saved copies take up space**
They're in `plugins/BetterEndCities/snapshots/`, usually under a megabyte each.

***

## Storage

**`Invalid database.type, defaulting to SQLITE`**
`database.type` is misspelled. Use `sqlite` or `mysql`.

**Everything reset after switching to MySQL**
Nothing is copied between storage types. See [Storage](configuration/storage.md#switching-loses-history).

***

## Performance

1. Run `/spark profiler` and look for `com.esmpfun.betterend` before changing anything.
2. Putting blocks back writes a whole city at once. Avoid it with a very short `loot.refresh-hours`.
3. Make sure `debug.verbose-logging` is `false`.
4. On a huge world, turn off `discovery.startup-sweep` only if you've measured it slowing startup.

***

## Reporting bugs

Use [GitHub Issues](https://github.com/ESMP-FUN/BetterEndCities/issues) or [Discord](https://discord.gg/qwYcTpHsNC), and include:

* The plugin version (the jar name, or `/betterend update status`)
* The full `/version` output
* `java -version` from the server
* The full error from the log file, not a screenshot
* What you expected and what happened
* `/betterend info <id>` if it's about one city

Turn on `debug.verbose-logging`, make the problem happen, and attach that part of the log.

{% hint style="info" %}
**Feature requests are welcome** on Discord.
{% endhint %}
