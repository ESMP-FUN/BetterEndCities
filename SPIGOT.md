[CENTER][SIZE=7][B]Better End Cities[/B][/SIZE]

[SIZE=5][B]Every player gets the elytra, and the item frame stays an item frame.[/B][/SIZE]

[SIZE=3]Paper · Folia · Purpur · Minecraft 26.1+[/SIZE]

The first player to reach an End Ship takes [B]the[/B] elytra.
Everyone after them finds an empty frame and gutted chests.
[SIZE=3][COLOR=#808080]
------------------------------
[/COLOR][/SIZE][/CENTER]
[SIZE=6][COLOR=#0000ff][B]No vault block. No datapack. No key item.[/B][/COLOR][/SIZE]
Other elytra plugins swap the ship's item frame for a vault block, then make you install a datapack and hand out a key item. This one leaves the frame alone. Players punch it exactly like they always have.
[LIST]
[*][B]First player takes the only elytra[/B] - every player gets their own
[*][B]Chests stay empty forever[/B] - every player gets their own loot
[*][B]Griefers strip the towers and ship[/B] - protected
[*][B]Cities never come back[/B] - put them back whenever you like
[*][B]Set up each city by hand[/B] - found automatically
[/LIST]
[CENTER][SIZE=3][COLOR=#808080]
------------------------------
[/COLOR][/SIZE][/CENTER]
[SIZE=6][COLOR=#0000ff][B]Setup[/B][/COLOR][/SIZE]
[LIST=1]
[*]Drop the jar into [ICODE]plugins/[/ICODE] ([ICODE]-mc26[/ICODE] for Minecraft 26.1 to 26.2, [ICODE]-mc263[/ICODE] for 26.3 and up)
[*]Restart
[*]There is no step 3
[/LIST]
Everything is switched on already. Cities add themselves as players travel near them.

Want to change something? [ICODE]/betterend[/ICODE] opens a menu, or [ICODE]/betterend setup[/ICODE] asks you one question at a time. [B]You never have to open a settings file.[/B]
[CENTER][SIZE=3][COLOR=#808080]
------------------------------
[/COLOR][/SIZE][/CENTER]
[SIZE=6][COLOR=#0000ff][B]What it does[/B][/COLOR][/SIZE]
[SPOILER="Elytras"]
Punch the ship's item frame and you get your own elytra. The frame, and its elytra, stay there for the next player.

Nobody can break the frame or knock the elytra out of it.

Choose: once per ship, once per player, or claimable again after every loot refresh.

Free, or charge any item you like. Pick it out of your own inventory in-game.
[/SPOILER]
[SPOILER="Loot"]
Every player gets their own copy of what's in a city's chests.

Refreshes on a timer you set, and each city runs its own timer, so a hundred cities never all refresh at the same moment.

Chests players placed themselves are left alone.
[/SPOILER]
[SPOILER="Protection"]
Towers, bridges and the ship survive creepers, TNT and griefers.

The empty space between them is still yours to build in, so builders aren't fenced off the island.
[/SPOILER]
[SPOILER="Putting cities back"]
A copy of every city is saved the moment it's found.

[ICODE]/betterend reset[/ICODE] puts the blocks back and gives everyone fresh loot.

Or let that happen by itself on every loot refresh.
[/SPOILER]
[CENTER][SIZE=3][COLOR=#808080]
------------------------------
[/COLOR][/SIZE][/CENTER]
[SIZE=6][COLOR=#0000ff][B]Will it work on my server?[/B][/COLOR][/SIZE]
[LIST]
[*][B]Server software:[/B] Paper, Folia, or Purpur
[*][B]Minecraft:[/B] 26.1 and up. Use the [ICODE]-mc26[/ICODE] download on 26.1 or 26.2, and [ICODE]-mc263[/ICODE] on 26.3 or newer
[*][B]Java:[/B] 25+
[*][B]Anything else:[/B] nothing. No datapack, no resource pack, no other plugins
[/LIST]
[B]Minecraft 26 only.[/B] This plugin uses pop-up menus and world information that don't exist in 1.21. There is no 1.21 version, and it will not start on one.

Optional: with [B]Better Anti-Dupe[/B] installed, claimed elytras are marked as genuine, so renewable elytras never look like copies.
[CENTER][SIZE=3][COLOR=#808080]
------------------------------
[/COLOR][/SIZE][/CENTER]
[SIZE=6][COLOR=#0000ff][B]Commands[/B][/COLOR][/SIZE]
[LIST]
[*][ICODE]/betterend[/ICODE] - the settings menu
[*][ICODE]/betterend setup[/ICODE] - walks you through the settings
[*][ICODE]/betterend list[/ICODE] - every city found so far
[*][ICODE]/betterend info <id>[/ICODE] - details about one city
[*][ICODE]/betterend tp <id>[/ICODE] - go there
[*][ICODE]/betterend snapshot <id>[/ICODE] - save a copy of the city so it can be put back
[*][ICODE]/betterend reset <id>[/ICODE] - put the blocks back, fresh loot for everyone
[*][ICODE]/betterend resetloot <id> <player>[/ICODE] - let one player loot it again
[*][ICODE]/betterend clearclaims <id>[/ICODE] - let everyone claim the elytra again
[*][ICODE]/betterend delete <id>[/ICODE] - stop managing this city
[*][ICODE]/betterend reload[/ICODE] - re-read the settings file
[/LIST]
[ICODE]betterend.admin[/ICODE] gets everything.
[ICODE]betterend.bypass.protection[/ICODE] can build inside cities.
[ICODE]betterend.discovery.notify[/ICODE] gets told when a city is found.
All three are OP by default.
[CENTER][SIZE=3][COLOR=#808080]
------------------------------
[/COLOR][/SIZE][/CENTER]
[SIZE=6][COLOR=#0000ff][B]Free and source available[/B][/COLOR][/SIZE]
No licence key. Nothing locked behind a premium version.

The source is on [URL='https://github.com/ESMP-FUN/BetterEndCities']GitHub[/URL], and issues are welcome.

[SPOILER="Automatic bug reports and anonymous statistics"]
Both are on by default, and either can be switched off.

[B]Bug reports.[/B] When something in the plugin goes wrong, it's reported automatically, so it gets fixed without you having to notice and write it up. Only this plugin's errors are ever sent. Before anything leaves your server, addresses, file paths containing your username, database passwords and player ids are stripped out and replaced. Each report says which plugin version, which Minecraft version, whether you run Folia, roughly how many cities you have, and what the plugin was doing. Player names, chat, inventories and anything about your world are never included.

[B]Statistics.[/B] Which storage you use, your claim setting, whether per-player loot is on, and roughly how many cities you have. That's it. City coordinates are never sent, and the city count is a range, not a list.

Set [ICODE]metrics.enabled: false[/ICODE] to send nothing at all, or [ICODE]metrics.error-reporting: false[/ICODE] for bug reports only.
[/SPOILER]
[CENTER][SIZE=3][COLOR=#808080]
------------------------------
[/COLOR][/SIZE][/CENTER]
[SIZE=6][COLOR=#0000ff][B]Links[/B][/COLOR][/SIZE]
[LIST]
[*][B]Discord:[/B] [URL='https://discord.gg/qwYcTpHsNC']support, announcements, feature requests[/URL]
[*][B]Source and issues:[/B] [URL='https://github.com/ESMP-FUN/BetterEndCities']github.com/ESMP-FUN/BetterEndCities[/URL]
[*][B]Ko-fi:[/B] [URL='https://ko-fi.com/darkstarworks'](anonymous) donations are very welcome[/URL]
[/LIST]
[CENTER][SIZE=3][I]Tell me "if it did X, I'd use it" on Discord - there's a good chance it ships.[/I]
[I]And if this made your End worth revisiting, a positive review here is the best way to support development.[/I][/SIZE]

[URL='https://faststats.dev/project/better-end-cities'][IMG]https://img.shields.io/endpoint?url=https%3A%2F%2Ffaststats.dev%2Fapi%2Fshields%2Fbetter-end-cities%3Fmetric%3Dservers%26color%3Dblueviolet%26icon%3D1&style=flat[/IMG][/URL][/CENTER]
