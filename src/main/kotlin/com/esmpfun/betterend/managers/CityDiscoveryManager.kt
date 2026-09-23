package com.esmpfun.betterend.managers

import com.esmpfun.betterend.BetterEnd
import com.esmpfun.betterend.models.EndCity
import com.esmpfun.betterend.models.IntBox
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.World
import org.bukkit.generator.structure.GeneratedStructure
import org.bukkit.generator.structure.Structure
import java.util.concurrent.ConcurrentHashMap

/**
 * Registers End Cities from the structure API, which gives every piece's
 * bounds from any one chunk of the city. Every chunk reports the same city,
 * so [seen] rejects repeats before the database's UNIQUE origin does.
 */
class CityDiscoveryManager(private val plugin: BetterEnd) {

    /** "world:ox:oy:oz" of cities already handled this session. */
    private val seen = ConcurrentHashMap.newKeySet<String>()

    private fun enabled() = plugin.config.getBoolean("discovery.enabled", true)

    private fun excluded(world: World) =
        plugin.config.getStringList("discovery.excluded-worlds")
            .any { it.equals(world.name, ignoreCase = true) }

    /** Must run on the region that owns the structure's chunk; the database work goes async. */
    fun handle(world: World, gs: GeneratedStructure) {
        if (!enabled()) return
        if (excluded(world)) return

        val bb = gs.boundingBox
        val origin = Triple(
            Math.floor(bb.minX).toInt(),
            Math.floor(bb.minY).toInt(),
            Math.floor(bb.minZ).toInt(),
        )
        val key = "${world.name}:${origin.first}:${origin.second}:${origin.third}"
        if (!seen.add(key)) return

        val pieces = gs.pieces.map { IntBox.fromBukkit(it.boundingBox) }
        val region = if (pieces.isNotEmpty()) IntBox.union(pieces) else IntBox.fromBukkit(bb)

        plugin.launchAsync {
            if (plugin.cityManager.existsAt(world.name, origin)) return@launchAsync
            val city = plugin.cityManager.registerCity(world.name, region, origin, pieces) ?: return@launchAsync
            notifyDiscovery(city)
            if (plugin.config.getBoolean("snapshot.auto-capture", true)) {
                plugin.snapshotManager.capture(city)
            }
        }
    }

    /** Lets a deleted city be found again the next time its chunks load, without a restart. */
    fun forget(city: EndCity) {
        seen.remove("${city.world}:${city.origin.first}:${city.origin.second}:${city.origin.third}")
    }

    private fun notifyDiscovery(city: EndCity) {
        val c = city.region
        plugin.logger.info(
            "Discovered End City #${city.id} in ${city.world} " +
                "(${c.minX},${c.minY},${c.minZ})..(${c.maxX},${c.maxY},${c.maxZ}), ${city.pieces.size} pieces"
        )
        val comp = MiniMessage.miniMessage().deserialize(
            "<light_purple>[BetterEndCities] <gray>Discovered End City <gray>#<white>${city.id} <white>${city.world} " +
                "<click:run_command:'/betterend tp ${city.id}'><hover:show_text:'<gray>Teleport to city <white>#${city.id}'>" +
                "<green>[${c.minX} ${c.minY} ${c.minZ}]</green></hover></click> " +
                "<dark_gray>• ${city.pieces.size} pieces"
        )
        plugin.scheduler.runTask(Runnable {
            plugin.server.onlinePlayers
                .filter { it.hasPermission("betterend.discovery.notify") }
                .forEach { it.sendMessage(comp) }
        })
    }

    /** Checks chunks already loaded at enable, which never fire a ChunkLoadEvent. */
    fun startupSweep() {
        if (!enabled() || !plugin.config.getBoolean("discovery.startup-sweep", true)) return
        for (world in plugin.server.worlds) {
            if (world.environment != World.Environment.THE_END) continue
            for (chunk in world.loadedChunks) {
                val loc = org.bukkit.Location(world, (chunk.x shl 4).toDouble(), world.minHeight.toDouble(), (chunk.z shl 4).toDouble())
                plugin.scheduler.runAtLocation(loc, Runnable {
                    for (gs in world.getStructures(chunk.x, chunk.z, Structure.END_CITY)) {
                        handle(world, gs)
                    }
                })
            }
        }
    }
}
