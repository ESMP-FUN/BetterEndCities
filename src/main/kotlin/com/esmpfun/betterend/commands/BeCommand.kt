package com.esmpfun.betterend.commands

import com.esmpfun.betterend.BetterEnd
import com.esmpfun.betterend.gui.dialog.BeDialogs
import com.esmpfun.betterend.models.EndCity
import com.esmpfun.betterend.setup.SetupTour
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import kotlinx.coroutines.future.await
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.UUID

/**
 * `/betterend` - menu-first admin command (Paper plugins register commands in
 * code, not via paper-plugin.yml).
 *
 *   /betterend                 the dialog config menu
 *   /betterend setup           guided setup tour
 *   /betterend list|info|tp    city inspection
 *   /betterend snapshot|reset  structure capture/restore
 *   /betterend resetloot|clearclaims|delete  surgical resets
 *   /betterend reload|update   housekeeping
 */
@Suppress("UnstableApiUsage")
class BeCommand(private val plugin: BetterEnd) : BasicCommand {

    private val mm = MiniMessage.miniMessage()
    private val subcommands = listOf(
        "menu", "setup", "list", "info", "tp", "snapshot", "reset",
        "resetloot", "clearclaims", "delete", "reload", "update", "help",
    )

    override fun permission(): String = "betterend.admin"

    override fun execute(source: CommandSourceStack, args: Array<String>) {
        val sender = source.sender
        if (!plugin.isReady && args.getOrNull(0)?.lowercase() != "help") {
            sender.sendMessage("§7Better End Cities is still starting up, try again in a moment.")
            return
        }
        when (args.getOrNull(0)?.lowercase()) {
            null, "menu" -> openMenu(sender)
            "setup" -> {
                val player = sender as? Player ?: run { sender.sendMessage("§cThe setup tour is players-only."); return }
                SetupTour.start(plugin, player)
            }
            "list" -> handleList(sender)
            "info" -> handleInfo(sender, args.getOrNull(1))
            "tp" -> handleTp(sender, args.getOrNull(1))
            "snapshot" -> handleSnapshot(sender, args.getOrNull(1))
            "reset" -> handleReset(sender, args.getOrNull(1))
            "resetloot" -> handleResetLoot(sender, args.getOrNull(1), args.getOrNull(2))
            "clearclaims" -> handleClearClaims(sender, args.getOrNull(1))
            "delete" -> handleDelete(sender, args.getOrNull(1))
            "reload" -> { plugin.reloadConfig(); sender.sendMessage("§aBetter End Cities config reloaded.") }
            "update" -> io.github.darkstarworks.pluginpulse.PluginPulse.handleUpdateCommand(
                plugin, sender, args.copyOfRange(1, args.size))
            "help" -> sendHelp(sender)
            else -> sender.sendMessage("§cUnknown subcommand. §7Try §f/betterend help§7.")
        }
    }

    override fun suggest(source: CommandSourceStack, args: Array<String>): Collection<String> = when (args.size) {
        1 -> subcommands.filter { it.startsWith(args[0], ignoreCase = true) }
        2 -> when (args[0].lowercase()) {
            "info", "tp", "snapshot", "reset", "resetloot", "clearclaims", "delete" ->
                plugin.cityManager.all().map { it.id.toString() }.filter { it.startsWith(args[1]) }
            else -> emptyList()
        }
        else -> emptyList()
    }

    private fun openMenu(sender: CommandSender) {
        val player = sender as? Player ?: run { sender.sendMessage("§cThe menu is players-only. Try §f/betterend list§c."); return }
        BeDialogs.openMainMenu(plugin, player)
    }

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage("§5§lBetter End Cities §7- admin commands")
        sender.sendMessage("§f/betterend §7- open the settings menu")
        sender.sendMessage("§f/betterend setup §7- guided first-time setup tour")
        sender.sendMessage("§f/betterend list §7- list the End Cities found so far")
        sender.sendMessage("§f/betterend info <id> §7- details of one city")
        sender.sendMessage("§f/betterend tp <id> §7- teleport to a city")
        sender.sendMessage("§f/betterend snapshot <id> §7- save a copy of the city's blocks to put back later")
        sender.sendMessage("§f/betterend reset <id> §7- put the city's blocks back and give everyone fresh loot")
        sender.sendMessage("§f/betterend resetloot <id> <player> §7- let one player loot the city fresh")
        sender.sendMessage("§f/betterend clearclaims <id> §7- forget who claimed this ship's elytra")
        sender.sendMessage("§f/betterend delete <id> §7- stop managing a city (its loot and claims go with it)")
        sender.sendMessage("§f/betterend reload §7- reload config.yml")
        sender.sendMessage("§f/betterend update §7- check for updates")
    }

    private fun resolve(sender: CommandSender, idArg: String?): EndCity? {
        val id = idArg?.toIntOrNull() ?: run {
            // No id: fall back to the city the sender is standing in.
            val player = sender as? Player
            val here = player?.let { plugin.cityManager.getCachedCityAt(it.location) }
            if (here == null) sender.sendMessage("§cProvide a city id (see §f/betterend list§c) or stand inside one.")
            return here
        }
        return plugin.cityManager.byId(id) ?: run {
            sender.sendMessage("§cNo city #$id. See §f/betterend list§c.")
            null
        }
    }

    private fun handleList(sender: CommandSender) {
        val cities = plugin.cityManager.all().sortedBy { it.id }
        if (cities.isEmpty()) {
            sender.sendMessage("§7No End Cities found yet. They register as players find them.")
            return
        }
        sender.sendMessage("§5End Cities §7(${cities.size})")
        for (c in cities) {
            val r = c.region
            sender.sendMessage(mm.deserialize(
                "<gray>#<white>${c.id} <white>${c.world} " +
                    "<click:run_command:'/betterend tp ${c.id}'><hover:show_text:'<gray>Teleport to city <white>#${c.id}'>" +
                    "<green>[${r.minX} ${r.minY} ${r.minZ}]</green></hover></click> " +
                    "<dark_gray>• ${c.pieces.size} pieces" +
                    (if (c.hasShip) " <dark_gray>• <aqua>ship" else "") +
                    (if (plugin.snapshotManager.hasSnapshot(c.id)) " <dark_gray>• <gray>snapshot saved" else "")
            ))
        }
    }

    private fun handleInfo(sender: CommandSender, idArg: String?) {
        val city = resolve(sender, idArg) ?: return
        val r = city.region
        sender.sendMessage("§5End City §7#§f${city.id}")
        sender.sendMessage("§7World: §f${city.world}")
        sender.sendMessage("§7Bounds: §f(${r.minX}, ${r.minY}, ${r.minZ}) §7to §f(${r.maxX}, ${r.maxY}, ${r.maxZ})")
        sender.sendMessage("§7Pieces: §f${city.pieces.size}")
        sender.sendMessage("§7End Ship: ${if (city.hasShip) "§byes" else "§7none found (not every city has one)"}")
        sender.sendMessage("§7Snapshot: ${if (plugin.snapshotManager.hasSnapshot(city.id)) "§asaved" else "§enone yet, run /betterend snapshot ${city.id}"}")
        val cycle = plugin.cityManager.cycleStart(city.id)
        if (cycle > 0) {
            val hours = (System.currentTimeMillis() - cycle) / 3_600_000
            sender.sendMessage("§7Loot cycle: §fstarted ${hours}h ago")
        }
    }

    private fun handleTp(sender: CommandSender, idArg: String?) {
        val player = sender as? Player ?: run { sender.sendMessage("§cPlayers only."); return }
        val city = resolve(sender, idArg) ?: return
        val world = city.getWorld() ?: run { sender.sendMessage("§cWorld '${city.world}' is not loaded."); return }
        val r = city.region
        // Stand on top of the first piece (the city's base tower): the middle
        // of the whole city's box can be open void.
        val base = city.pieces.firstOrNull() ?: r
        val x = (base.minX + base.maxX) / 2
        val z = (base.minZ + base.maxZ) / 2
        val fallback = Location(world, (r.minX + r.maxX) / 2.0 + 0.5, r.maxY + 1.0, (r.minZ + r.maxZ) / 2.0 + 0.5)
        player.sendMessage("§7Teleporting to city §f#${city.id}§7.")
        plugin.launchAsync {
            world.getChunkAtAsync(x shr 4, z shr 4).await()
            plugin.scheduler.runAtLocation(Location(world, x.toDouble(), 0.0, z.toDouble()), Runnable {
                val top = world.getHighestBlockYAt(x, z)
                val dest = if (top > world.minHeight) Location(world, x + 0.5, top + 1.0, z + 0.5) else fallback
                // Folia refuses teleport(); teleportAsync works everywhere.
                plugin.scheduler.runAtEntity(player, Runnable { player.teleportAsync(dest) })
            })
        }
    }

    private fun handleSnapshot(sender: CommandSender, idArg: String?) {
        val city = resolve(sender, idArg) ?: return
        sender.sendMessage("§7Saving a copy of city #${city.id}. This may take a few seconds...")
        plugin.launchAsync {
            val n = plugin.snapshotManager.capture(city)
            sender.sendMessage(if (n >= 0) "§aSnapshot captured for city #${city.id} ($n cells)." else "§cSnapshot failed (see console).")
        }
    }

    /**
     * Full city refresh: restore blocks from the snapshot (when one exists),
     * clear everyone's loot copies, and start a fresh loot cycle - which also
     * re-arms elytra claims in per-refresh mode. Per-ship/global claims are
     * deliberately untouched; that's what clearclaims is for.
     */
    private fun handleReset(sender: CommandSender, idArg: String?) {
        val city = resolve(sender, idArg) ?: return
        sender.sendMessage("§7Resetting city #${city.id}...")
        plugin.launchAsync {
            val restored = if (plugin.snapshotManager.hasSnapshot(city.id)) plugin.snapshotManager.restore(city) else -1
            val cleared = plugin.containerLootManager.clearCity(city.id)
            plugin.cityManager.forceNewCycle(city.id)
            plugin.cityManager.persistCycleStart(city.id)
            plugin.cityManager.setLastReset(city.id, System.currentTimeMillis())
            val blocks = if (restored >= 0) "$restored blocks restored" else "no snapshot, so blocks untouched"
            sender.sendMessage("§aCity #${city.id} reset: $blocks, $cleared loot copies cleared, fresh loot for everyone.")
        }
    }

    private fun handleResetLoot(sender: CommandSender, idArg: String?, name: String?) {
        val city = resolve(sender, idArg) ?: return
        val target = resolveTarget(sender, name) ?: return
        plugin.launchAsync {
            val n = plugin.containerLootManager.clearPlayer(city.id, target)
            sender.sendMessage("§aCleared $n container cop${if (n == 1) "y" else "ies"} for $name in city #${city.id}. They can loot it fresh.")
        }
    }

    private fun handleClearClaims(sender: CommandSender, idArg: String?) {
        val city = resolve(sender, idArg) ?: return
        plugin.launchAsync {
            val n = plugin.elytraClaimManager.clearCity(city.id)
            sender.sendMessage("§aCleared $n elytra claim${if (n == 1) "" else "s"} for city #${city.id}. Everyone can claim its ship's elytra again.")
        }
    }

    private fun handleDelete(sender: CommandSender, idArg: String?) {
        val city = resolve(sender, idArg) ?: return
        plugin.launchAsync {
            val ok = plugin.cityManager.deleteCity(city.id)
            if (ok) {
                plugin.snapshotManager.deleteSnapshot(city.id)
                plugin.elytraClaimManager.dropCity(city.id)
                plugin.containerLootManager.dropCity(city.id)
                plugin.discoveryManager.forget(city)
            }
            sender.sendMessage(if (ok) "§aDeleted city #${city.id} (loot, claims and snapshot removed)." else "§cDelete failed.")
        }
    }

    private fun resolveTarget(sender: CommandSender, name: String?): UUID? {
        if (name.isNullOrBlank()) {
            sender.sendMessage("§cProvide a player name.")
            return null
        }
        plugin.server.getPlayerExact(name)?.let { return it.uniqueId }
        // The cached lookup never blocks on a Mojang web request.
        val off = plugin.server.getOfflinePlayerIfCached(name)
        if (off == null || (!off.hasPlayedBefore() && !off.isOnline)) {
            sender.sendMessage("§cNo player named '$name' has been seen on this server.")
            return null
        }
        return off.uniqueId
    }
}
