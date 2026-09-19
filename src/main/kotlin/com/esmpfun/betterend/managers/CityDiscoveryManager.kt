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
 * Turns a [GeneratedStructure] (an End City the server already knows about)
 * into a registered [EndCity]. The structure API hands us complete bounds +
 * per-piece bounds from a single chunk, so there's no palette scan, no
 * multi-chunk AABB growth, no retry. When the city generated with a ship,
 * the ship is included as one more piece automatically.
 *
 * No approval workflow: a discovered city is registered active. When
 * `snapshot.auto-capture` is on (default), a baseline snapshot is captured
 * right after registration so a reset target always exists.
 *
 * Dedup: every loaded chunk of a city reports the same structure, so we key
 * by the structure bounding-box min corner (the city's [EndCity.origin]) in
 * an in-memory seen-set — cheap rejection before any DB hit — backed by the
 * DB UNIQUE constraint for cross-restart and concurrent safety.
 */
class CityDiscoveryManager(private val plugin: BetterEnd) {

    /** "world:ox:oy:oz" of cities already handled this session. */
    private val seen = ConcurrentHashMap.newKeySet<String>()

    private fun enabled() = plugin.config.getBoolean("discovery.enabled", true)

    /** Worlds where BetterEnd never registers cities (discovery.excluded-worlds). */
    private fun excluded(world: World) =
        plugin.config.getStringList("discovery.excluded-worlds")
            .any { it.equals(world.name, ignoreCase = true) }

    /**
     * Considers one generated structure for registration. MUST be called on the
     * region thread owning the structure's chunk — it reads the structure's
     * bounding box and pieces synchronously here, then hands plain data to the
     * async DB path.
     */
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
        if (!seen.add(key)) return // already handled this session

        val pieces = gs.pieces.map { IntBox.fromBukkit(it.boundingBox) }
        // Tighten the envelope to the actual pieces when we have them;
        // otherwise use the raw structure bounding box.
        val region = if (pieces.isNotEmpty()) IntBox.union(pieces) else IntBox.fromBukkit(bb)

        plugin.launchAsync {
            if (plugin.cityManager.existsAt(world.name, origin)) return@launchAsync
            val city = plugin.cityManager.registerCity(world.name, region, origin, pieces) ?: return@launchAsync
            notifyDiscovery(city)
            // Baseline snapshot so a reset target always exists.
            if (plugin.config.getBoolean("snapshot.auto-capture", true)) {
                plugin.snapshotManager.capture(city)
            }
        }
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

    /**
     * One-time sweep over already-loaded chunks on enable, so cities resident
     * at startup are caught without waiting for a ChunkLoadEvent. Folia-safe:
     * hops to each chunk's region thread to read its structures.
     */
    fun startupSweep() {
        if (!enabled() || !plugin.config.getBoolean("discovery.startup-sweep", true)) return
        for (world in plugin.server.worlds) {
            if (world.environment != World.Environment.THE_END) continue
            for (chunk in world.loadedChunks) {
                val loc = chunk.getBlock(0, world.minHeight, 0).location
                plugin.scheduler.runAtLocation(loc, Runnable {
                    for (gs in world.getStructures(chunk.x, chunk.z, Structure.END_CITY)) {
                        handle(world, gs)
                    }
                })
            }
        }
    }
}
