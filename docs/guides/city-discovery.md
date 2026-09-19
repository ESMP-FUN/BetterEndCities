# Finding Cities

End Cities register themselves. There's no command to run for each city, no area to select, and nothing to approve.

***

## How it works

When a player gets close enough to an End City for it to load in, Better End Cities asks **the world itself** for that city's exact shape and its list of towers, then registers it.

Because it reads the world's own information rather than scanning blocks, finding a city is:

* **Exact.** The real shape of every tower, not a guessed box around it
* **Complete straight away.** Every tower and bridge, plus the ship if the city has one
* **Cheap.** A quick lookup, not a block-by-block search
* **Unaffected by looting.** A city stripped bare still registers correctly, because what the world knows about it doesn't change when blocks do

A city starts working **the moment it's found**. Loot, elytra frames, protection and saved copies are all active immediately. There's no waiting list to approve.

```
[BetterEndCities] Discovered End City #1 in world_the_end (1264,0,-368)..(1329,100,-303), 14 pieces
```

Ops with `betterend.discovery.notify` also get a message in chat they can click to teleport there.

***

## The check at startup

```yaml
discovery:
  startup-sweep: true
```

Normally a city is found when it loads in. But parts of the world that were already loaded when the plugin started never "load in" again, so a city sitting in one would be missed after a restart.

The startup check looks over those already-loaded areas once, to catch them. On by default. Only turn it off if you've actually measured it slowing your startup down.

***

## Leaving a world alone

```yaml
discovery:
  excluded-worlds:
    - end_vanilla
    - resource_end
```

World names, and capital letters don't matter. Cities in these worlds are never registered and behave exactly like vanilla.

Useful when you run one managed End and one resource End, and only want one of them renewable.

{% hint style="info" %}
**Excluding a world doesn't undo what's already registered in it.** Cities registered before you excluded it keep working. Remove them yourself:

```
/betterend list          # find the numbers
/betterend delete <id>   # remove each one
```
{% endhint %}

***

## Turning it off

```yaml
discovery:
  enabled: false
```

No new cities register. Cities already registered keep working normally.

There's **no command to register a city by hand**, because the plugin works from what the world knows about its own structures. A city the world has no record of can't be registered. That means:

* **Cities that generated naturally** always work
* **Cities a player built by hand** can't be registered
* **Cities from a datapack or custom world generation** work if they generate as real structures, and don't if they were pasted in from a schematic

{% hint style="info" %}
Building a custom End City by hand and wanting the plugin to manage it is a fair thing to want, and isn't supported today. If you need it, say so on [Discord](https://discord.gg/qwYcTpHsNC).
{% endhint %}

***

## Looking at your cities

| Command | Shows |
|---|---|
| `/betterend list` | Every registered city, with its number |
| `/betterend info <id>` | Size, how many towers, whether there's a ship, whether a copy is saved, and its loot countdown |
| `/betterend tp <id>` | Teleport there |
| `/betterend delete <id>` | Stop managing it |

City numbers just count up from 1, and every command that takes one will complete it for you when you press Tab.

{% hint style="warning" %}
**`delete` stops managing a city, it doesn't demolish it.** The blocks stay exactly where they are. The city will simply be found again next time it loads in, unless you've also excluded its world or turned finding cities off.
{% endhint %}

***

## What's next?

{% content-ref url="../configuration/config.yml.md" %}
[config.yml.md](../configuration/config.yml.md)
{% endcontent-ref %}
