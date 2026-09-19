# Permissions

There are three permissions. All of them are given to ops by default.

***

## The full list

| Permission | Default | What it grants |
|---|---|---|
| `betterend.admin` | op | Every `/betterend` command, and the settings menu |
| `betterend.bypass.protection` | op | Build and break inside protected cities |
| `betterend.discovery.notify` | op | A message when a city is found |

***

## `betterend.admin`

Controls every command. Without it, `/betterend` does nothing at all.

There's no separate "look but don't touch" permission. This one covers `list` and `info` as well as `reset` and `delete`, so give it to staff you'd trust to reset a city.

## `betterend.bypass.protection`

Lets a player break and place blocks inside cities, ignoring [protection](../guides/protection.md).

{% hint style="warning" %}
**This is the one that trips people up.** Ops have it by default, so testing protection while opped makes it look completely broken. You'll break a purpur block, see nothing stop you, and conclude the plugin isn't working.

Test on a normal account, or take it away from yourself:

```
/lp user <you> permission set betterend.bypass.protection false
```
{% endhint %}

Give it to a builder rank if staff need to work inside cities. That's much better than turning protection off for the whole server.

## `betterend.discovery.notify`

Sends a clickable message whenever a city is found:

```
[BetterEndCities] Discovered End City #3 world_the_end [1264 55 -368] • 14 pieces
```

Clicking the coordinates teleports you there. The console logs every discovery whether or not anyone has this.

Handy while you're setting up, or on a new world where you want to watch the End fill in. On an established server it gets noisy, so consider taking it away from staff who don't need it.

***

## Rank examples

### Regular players

Nothing at all. Players need no permission to claim elytras or get their own loot. Those work for everyone by design.

```yaml
# No permissions needed
```

### Builders and staff who work inside cities

```
/lp group builder permission set betterend.bypass.protection true
```

They can build inside cities, but can't reset or delete them.

### Moderators

```
/lp group mod permission set betterend.admin true
/lp group mod permission set betterend.bypass.protection false
```

Full commands, but protection still applies to them, so they can't accidentally grief a city while moderating.

### Admins

Default op is fine, which gives all three.

### Testing protection as an admin

```
/lp user <you> permission set betterend.bypass.protection false
```

Set it back to `true`, or unset it, when you're done.

***

## Notes

* **Three permissions, on purpose.** `betterend.admin` is all-or-nothing rather than one permission per command. Three you can remember beats twenty you have to look up. If you genuinely need finer control, say so on [Discord](https://discord.gg/qwYcTpHsNC).
* **Nobody can bypass per-player loot.** Loot copies belong to a player with no override, so admins get their own copy like everyone else. Use `/betterend resetloot <id> <player>` to give someone a fresh one.
* **Nobody can bypass elytra claims** either. The claim setting applies to everyone. Use `/betterend clearclaims <id>` to reopen a ship.
