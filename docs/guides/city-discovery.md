# Finding Cities

End Cities register themselves when a player gets close enough for them to load. There is nothing to select or approve.

The plugin reads each city's exact shape from the world: every tower, bridge and the ship. A looted or damaged city still registers correctly. It starts working as soon as it's found.

```
[BetterEndCities] Discovered End City #1 in world_the_end (1264,0,-368)..(1329,100,-303), 14 pieces
```

Staff with `betterend.discovery.notify` also get a chat message they can click to teleport there.

***

## Settings

| Setting | Default | What it does |
|---|---|---|
| `discovery.enabled` | `true` | Register new cities. Off: no new cities; registered ones keep working |
| `discovery.startup-sweep` | `true` | Also check areas already loaded when the server starts, which would otherwise be missed until they load again |
| `discovery.excluded-worlds` | `[]` | Worlds whose cities behave like vanilla |

### Leave a world alone

```yaml
discovery:
  excluded-worlds:
    - end_vanilla
    - resource_end
```

Capital letters don't matter.

{% hint style="info" %}
Cities registered before you excluded a world keep working. Remove them:

```
/betterend list          # find the numbers
/betterend delete <id>   # remove each one
```
{% endhint %}

***

## Which cities can register

* **Naturally generated cities** always register
* **Cities from a datapack or custom world generation** register if they generate as real structures
* **Cities built by hand or pasted from a schematic** can't, because the world has no record of them as a structure

***

## Look at your cities

| Command | Shows |
|---|---|
| `/betterend list` | Every registered city and its number |
| `/betterend info <id>` | Size, number of pieces, ship, saved copy, loot countdown |
| `/betterend tp <id>` | Teleports you on top of the city's base tower |
| `/betterend delete <id>` | Stops managing it |

Press Tab to complete city numbers.

{% hint style="warning" %}
**`delete` doesn't demolish anything.** The blocks stay, and the city is found again the next time it loads, unless you've excluded its world or turned off `discovery.enabled`.
{% endhint %}

***

## What's next?

{% content-ref url="../configuration/config.yml.md" %}
[config.yml.md](../configuration/config.yml.md)
{% endcontent-ref %}
