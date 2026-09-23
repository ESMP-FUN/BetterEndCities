# Permissions

Three permissions, all given to ops by default. Players need none to claim elytras or get their own loot.

***

## The full list

| Permission | Default | What it grants |
|---|---|---|
| `betterend.admin` | op | Every `/betterend` command and the settings menu |
| `betterend.bypass.protection` | op | Break and build inside protected cities |
| `betterend.discovery.notify` | op | A chat message when a city is found |

***

## `betterend.admin`

Every command, from `list` and `info` to `reset` and `delete`. There is no read-only version, so give it only to staff you'd trust to reset a city.

## `betterend.bypass.protection`

Lets a player ignore [protection](../guides/protection.md). Give it to a builder rank instead of turning protection off.

{% hint style="warning" %}
**Ops have it,** so protection looks broken when you test it as an op. Use a normal account, or remove it from yourself:

```
/lp user <you> permission set betterend.bypass.protection false
```
{% endhint %}

## `betterend.discovery.notify`

Sends a clickable message when a city is found. Click the position to teleport there. The console logs every discovery either way. Remove it from staff who find it noisy.

***

## Rank examples

**Builders who work inside cities:**

```
/lp group builder permission set betterend.bypass.protection true
```

**Moderators with every command, but still bound by protection:**

```
/lp group mod permission set betterend.admin true
/lp group mod permission set betterend.bypass.protection false
```

**Admins:** default op gives all three.

***

## Notes

* **Per-player loot has no bypass.** Admins get their own copy like everyone else. `/betterend resetloot <id> <player>` gives a player a fresh one. Admins can sneak and right-click a city chest to edit what every copy starts with
* **Elytra claims have no bypass.** `/betterend clearclaims <id>` reopens a ship
* Need finer control than three permissions? Ask on [Discord](https://discord.gg/aWMU2JNXex)
