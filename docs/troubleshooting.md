# Troubleshooting

Most problems have a known cause. Start here.

***

## The plugin won't load

**`Unsupported class file major version`, or it's missing from `/plugins`**
You're on Java 24 or older. This plugin needs **Java 25 or newer**. Check with `java -version` on the machine running the server, not your own computer.

**`Unknown API version`, or it disables itself on a 1.21 server**
This plugin is **Minecraft 26 only**. There is no 1.21 build. See [Installation](getting-started/installation.md#before-you-start).

**It won't load on my 26.1 or 26.2 server**
You've got the `-mc263` download, which is built for 26.3 and up. Use `-mc26` instead. See [Download](getting-started/installation.md#download).

**Nothing in the log at all**
Check the jar is directly in `plugins/`, not in a folder inside it, and that it's the release jar from [Releases](https://github.com/ESMP-FUN/BetterEndCities/releases).

***

## Cities aren't being found

**Nothing happens when I fly to a city**, most likely cause first:

1. **It hasn't loaded in yet.** Flying fast on an elytra outruns the world loading. Stop, stand still a moment, watch the console.
2. **The world is excluded.** Check `discovery.excluded-worlds` matches the world's actual name.
3. **Finding cities is switched off.** Check `discovery.enabled: true`.
4. **It isn't a real generated city.** A city a player built, or one pasted from a schematic, can't be registered. See [Finding Cities](guides/city-discovery.md#turning-it-off).

Check what's registered with `/betterend list`.

**Cities already loaded get missed after a restart**
That's what `discovery.startup-sweep: true` is for, and it's on by default. If you turned it off, turn it back on.

***

## Elytra claims aren't working

**Punching the frame does nothing**

* Check `elytra.enabled: true`, and that the city is registered with `/betterend list`
* Check the city actually **has a ship**, with `/betterend info <id>`. Not every End City generates one
* If you've set a cost, the player needs the item. Without it they get a red message **just above their hotbar**, which is easy to miss if they're watching chat

**A player can't claim a second time**
That's it working as you've set it up. `per-ship` needs a different ship, `global` is one per player ever, and `per-refresh` means waiting for that city's loot to come back. To reopen a ship: `/betterend clearclaims <id>`.

**The cost item isn't being accepted**
The cost stores a **specific** item, including its name and enchantments. If you set it to a custom named item, an ordinary one won't pay it. Pick it again with `/betterend`, **Choose Cost Item**.

{% hint style="warning" %}
Never edit `elytra.cost.item` by hand. It stores a whole item, not an item name.
{% endhint %}

**No floating note above the frame**
Check `elytra.text-display: true`. A plugin or client mod that hides floating labels will also do it.

***

## Per-player loot isn't working

**Everyone sees the same chest contents**
Check `loot.enabled: true`, that the city is registered, and that the chest is one that **came with the city**. Chests players placed are deliberately normal and shared.

**Loot never comes back**
Nothing refreshes on a clock. Loot comes back when someone next loots the city *after* the time is up, so a city nobody visits never refreshes. Also check `loot.refresh-hours` isn't `0`.

**A player lost their items**
Per-player copies are storage, not just loot. Items left in a copy are gone when the city refreshes. Tell players not to store things in city chests, or set `loot.refresh-hours: 0`.

***

## Protection isn't working

**I can break blocks in a city**
You're almost certainly an op. `betterend.bypass.protection` is given to ops by default. This is by far the most common "bug report" for this plugin. Test on a normal account, or take it away from yourself:

```
/lp user <you> permission set betterend.bypass.protection false
```

**Players can build between the towers**
Intended. Protection covers [each tower separately](guides/protection.md#each-tower-is-protected-on-its-own), so the space between them stays buildable. If decoration just outside a tower is breakable, raise `protection.piece-padding` a little.

**Explosions still damage the city**
Check `protection.block-explosions: true`.

**Cushions can be placed or taken inside a protected city**
You're on Minecraft 26.3 running the `-mc26` download, which was built before cushions existed and can't protect against them. Swap to the `-mc263` download and restart.

***

## Resets and saved copies

**`/betterend reset` says there's no saved copy**
Make one with `/betterend snapshot <id>`. Note it saves the city **as it is right now**, so repair it first if it's already been wrecked.

**A reset erased a player's base**
Expected, and why putting blocks back on every refresh is off by default. If players make bases in cities, keep `snapshot.auto-reset-on-refresh: false` and reset by hand.

**Saved copies take up too much space**
They're compressed, usually well under a megabyte each, in `plugins/BetterEndCities/snapshots/`. Files for cities you've deleted are no longer used and can be removed by hand.

***

## Storage

**`Invalid database.type, defaulting to SQLITE`**
A typo. The only valid values are `sqlite` and `mysql`. Your MySQL settings are being ignored completely while this shows.

**Everything reset after I switched to MySQL**
Expected. There's no way to move data across, so the new database starts empty. Cities register themselves again, but claim history and loot copies are lost. See [Storage](configuration/storage.md#switching-between-them).

***

## Performance

Finding cities, saving copies and storage work all happen in the background, and nothing is scanned on a timer. If you're seeing lag:

1. **Check it's actually this plugin.** Run `/spark profiler` and look for `com.esmpfun.betterend` before changing anything.
2. **Big resets are the one heavy job.** Rebuilding a large city writes a lot of blocks at once. Avoid putting blocks back on every refresh if your refresh time is very short.
3. **The startup check on a huge world** costs a little once, at startup. Turn `discovery.startup-sweep` off only if you've measured it as a problem.
4. **`debug.verbose-logging: true` is expensive.** Make sure it's off on a live server.

***

## Reporting bugs

[GitHub Issues](https://github.com/ESMP-FUN/BetterEndCities/issues), or [Discord](https://discord.gg/qwYcTpHsNC) if you'd rather talk it through. Please include:

* **The plugin version**, from `/betterend update status` or the jar filename
* **Your server software and version**, the full `/version` output
* **Your Java version**, from `java -version` on the server
* **The full error**, copied from the log file rather than screenshotted from chat
* **What you expected** and **what actually happened**
* **`/betterend info <id>`** for the affected city, if it's about one city

Switching on `debug.verbose-logging: true`, making the problem happen again, then attaching that part of the log usually means it can be sorted in one reply.

{% hint style="info" %}
**Feature requests are welcome too.** Several things in this plugin exist because a server owner said "if it did X, I'd use it". Say it on Discord.
{% endhint %}
