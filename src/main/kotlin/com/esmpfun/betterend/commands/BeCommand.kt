package com.esmpfun.betterend.commands

import com.esmpfun.betterend.BetterEnd
import com.esmpfun.betterend.gui.dialog.BeDialogs
import com.esmpfun.betterend.models.EndCity
import com.esmpfun.betterend.setup.SetupTour
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import kotlinx.coroutines.future.await
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.UUID

/** `/betterend` and its subcommands. */
@Suppress("UnstableApiUsage")
class BeCommand(private val plugin: BetterEnd) : BasicCommand {

    private val msg get() = plugin.messages
    private val subcommands = listOf(
        "menu", "setup", "list", "info", "tp", "snapshot", "reset",
        "resetloot", "clearclaims", "delete", "reload", "update", "help",
    )

    override fun permission(): String = "betterend.admin"

    override fun execute(source: CommandSourceStack, args: Array<String>) {
        val sender = source.sender
        if (!plugin.isReady && args.getOrNull(0)?.lowercase() != "help") {
            sender.sendMessage(msg.get("command.starting-up"))
            return
        }
        when (args.getOrNull(0)?.lowercase()) {
            null, "menu" -> openMenu(sender)
            "setup" -> {
                val player = sender as? Player ?: run { sender.sendMessage(msg.get("command.setup-players-only")); return }
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
            "reload" -> {
                plugin.reloadConfig()
                msg.load()
                sender.sendMessage(msg.get("command.reload-done"))
            }
            "update" -> io.github.darkstarworks.pluginpulse.PluginPulse.handleUpdateCommand(
                plugin, sender, args.copyOfRange(1, args.size))
            "help" -> sendHelp(sender)
            else -> sender.sendMessage(msg.get("command.unknown"))
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
        val player = sender as? Player ?: run { sender.sendMessage(msg.get("command.menu-players-only")); return }
        BeDialogs.openMainMenu(plugin, player)
    }

    private fun sendHelp(sender: CommandSender) {
        msg.lines("command.help").forEach(sender::sendMessage)
    }

    private fun resolve(sender: CommandSender, idArg: String?): EndCity? {
        val id = idArg?.toIntOrNull() ?: run {
            // No id: fall back to the city the sender is standing in.
            val player = sender as? Player
            val here = player?.let { plugin.cityManager.getCachedCityAt(it.location) }
            if (here == null) sender.sendMessage(msg.get("command.need-city"))
            return here
        }
        return plugin.cityManager.byId(id) ?: run {
            sender.sendMessage(msg.get("command.unknown-city", "id" to id))
            null
        }
    }

    private fun handleList(sender: CommandSender) {
        val cities = plugin.cityManager.all().sortedBy { it.id }
        if (cities.isEmpty()) {
            sender.sendMessage(msg.get("command.list.empty"))
            return
        }
        sender.sendMessage(msg.get("command.list.header", "count" to cities.size))
        for (c in cities) {
            val r = c.region
            sender.sendMessage(msg.get(
                "command.list.entry",
                "id" to c.id, "world" to c.world,
                "x" to r.minX, "y" to r.minY, "z" to r.minZ,
                "pieces" to c.pieces.size,
                "ship" to if (c.hasShip) msg.raw("command.list.ship") else "",
                "snapshot" to if (plugin.snapshotManager.hasSnapshot(c.id)) msg.raw("command.list.snapshot") else "",
            ))
        }
    }

    private fun handleInfo(sender: CommandSender, idArg: String?) {
        val city = resolve(sender, idArg) ?: return
        val r = city.region
        sender.sendMessage(msg.get("command.info.header", "id" to city.id))
        sender.sendMessage(msg.get("command.info.world", "world" to city.world))
        sender.sendMessage(msg.get(
            "command.info.bounds",
            "min-x" to r.minX, "min-y" to r.minY, "min-z" to r.minZ,
            "max-x" to r.maxX, "max-y" to r.maxY, "max-z" to r.maxZ,
        ))
        sender.sendMessage(msg.get("command.info.pieces", "count" to city.pieces.size))
        sender.sendMessage(msg.get(if (city.hasShip) "command.info.ship-found" else "command.info.ship-none"))
        val snapshotKey = if (plugin.snapshotManager.hasSnapshot(city.id)) "command.info.snapshot-saved" else "command.info.snapshot-none"
        sender.sendMessage(msg.get(snapshotKey, "id" to city.id))
        val cycle = plugin.cityManager.cycleStart(city.id)
        if (cycle > 0) {
            val hours = (System.currentTimeMillis() - cycle) / 3_600_000
            sender.sendMessage(msg.get("command.info.loot-cycle", "hours" to hours))
        }
    }

    private fun handleTp(sender: CommandSender, idArg: String?) {
        val player = sender as? Player ?: run { sender.sendMessage(msg.get("command.players-only")); return }
        val city = resolve(sender, idArg) ?: return
        val world = city.getWorld() ?: run { sender.sendMessage(msg.get("command.tp.world-not-loaded", "world" to city.world)); return }
        val r = city.region
        // The first piece is the base tower; the middle of the city's box can be void.
        val base = city.pieces.firstOrNull() ?: r
        val x = (base.minX + base.maxX) / 2
        val z = (base.minZ + base.maxZ) / 2
        val fallback = Location(world, (r.minX + r.maxX) / 2.0 + 0.5, r.maxY + 1.0, (r.minZ + r.maxZ) / 2.0 + 0.5)
        player.sendMessage(msg.get("command.tp.teleporting", "id" to city.id))
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
        sender.sendMessage(msg.get("command.snapshot.saving", "id" to city.id))
        plugin.launchAsync {
            val n = plugin.snapshotManager.capture(city)
            sender.sendMessage(
                if (n >= 0) msg.get("command.snapshot.done", "id" to city.id, "blocks" to n)
                else msg.get("command.snapshot.failed")
            )
        }
    }

    // Starting a cycle also re-arms per-refresh claims; other claim modes are clearclaims' job.
    private fun handleReset(sender: CommandSender, idArg: String?) {
        val city = resolve(sender, idArg) ?: return
        sender.sendMessage(msg.get("command.reset.started", "id" to city.id))
        plugin.launchAsync {
            val restored = if (plugin.snapshotManager.hasSnapshot(city.id)) plugin.snapshotManager.restore(city) else -1
            val cleared = plugin.containerLootManager.clearCity(city.id)
            plugin.cityManager.forceNewCycle(city.id)
            plugin.cityManager.persistCycleStart(city.id)
            plugin.cityManager.setLastReset(city.id, System.currentTimeMillis())
            sender.sendMessage(
                if (restored >= 0) msg.get("command.reset.done", "id" to city.id, "blocks" to restored, "copies" to cleared)
                else msg.get("command.reset.done-no-snapshot", "id" to city.id, "copies" to cleared)
            )
        }
    }

    private fun handleResetLoot(sender: CommandSender, idArg: String?, name: String?) {
        val city = resolve(sender, idArg) ?: return
        val target = resolveTarget(sender, name) ?: return
        plugin.launchAsync {
            val n = plugin.containerLootManager.clearPlayer(city.id, target)
            sender.sendMessage(msg.get(
                if (n == 1) "command.resetloot.done-one" else "command.resetloot.done",
                "count" to n, "player" to name, "id" to city.id,
            ))
        }
    }

    private fun handleClearClaims(sender: CommandSender, idArg: String?) {
        val city = resolve(sender, idArg) ?: return
        plugin.launchAsync {
            val n = plugin.elytraClaimManager.clearCity(city.id)
            sender.sendMessage(msg.get(
                if (n == 1) "command.clearclaims.done-one" else "command.clearclaims.done",
                "count" to n, "id" to city.id,
            ))
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
            sender.sendMessage(if (ok) msg.get("command.delete.done", "id" to city.id) else msg.get("command.delete.failed"))
        }
    }

    private fun resolveTarget(sender: CommandSender, name: String?): UUID? {
        if (name.isNullOrBlank()) {
            sender.sendMessage(msg.get("command.need-player"))
            return null
        }
        plugin.server.getPlayerExact(name)?.let { return it.uniqueId }
        // The cached lookup never blocks on a Mojang web request.
        val off = plugin.server.getOfflinePlayerIfCached(name)
        if (off == null || (!off.hasPlayedBefore() && !off.isOnline)) {
            sender.sendMessage(msg.get("command.unknown-player", "player" to name))
            return null
        }
        return off.uniqueId
    }
}
