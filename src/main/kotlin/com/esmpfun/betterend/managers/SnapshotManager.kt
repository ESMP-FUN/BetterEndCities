package com.esmpfun.betterend.managers

import com.esmpfun.betterend.BetterEnd
import com.esmpfun.betterend.models.EndCity
import com.esmpfun.betterend.utils.CompressionUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import kotlin.coroutines.resume
import java.io.File
import java.io.Serializable
import java.util.concurrent.atomic.AtomicInteger

/**
 * Saves and restores a city's blocks, one gzip file per city in `snapshots/`.
 * Block data only: loot lives in the database and the elytra frame is an
 * entity, so neither needs saving. Only cells inside the padded pieces are
 * kept, air included, so the space between towers is never touched.
 */
class SnapshotManager(private val plugin: BetterEnd) {

    private data class SnapshotData(
        val worldName: String,
        val originX: Int,
        val originY: Int,
        val originZ: Int,
        val blocks: Map<Triple<Int, Int, Int>, String>, // position relative to origin -> block data
    ) : Serializable {
        companion object { private const val serialVersionUID = 1L }
    }

    private fun fileFor(cityId: Int) = File(plugin.snapshotsDir, "city_$cityId.dat")

    private fun maxCells() = plugin.config.getInt("snapshot.max-cells", 3_000_000)

    // Covers the same padded area protection does, so trim outside a piece is restored too.
    private fun capturePad() = plugin.config.getInt("protection.piece-padding", 3)

    private fun cellsByChunk(city: EndCity, pad: Int): Map<Long, MutableList<Triple<Int, Int, Int>>> {
        val seen = HashSet<Long>()
        val byChunk = HashMap<Long, MutableList<Triple<Int, Int, Int>>>()
        for (piece in city.pieces) {
            val p = piece.expanded(pad)
            for (x in p.minX..p.maxX) for (y in p.minY..p.maxY) for (z in p.minZ..p.maxZ) {
                if (!seen.add(blockKey(x, y, z))) continue
                val ck = (x shr 4).toLong() shl 32 or ((z shr 4).toLong() and 0xffffffffL)
                byChunk.getOrPut(ck) { mutableListOf() }.add(Triple(x, y, z))
            }
        }
        return byChunk
    }

    // Discovery captures a city before most of its chunks exist; generating them synchronously stalls the server.
    private suspend fun loadChunk(world: World, cx: Int, cz: Int) {
        world.getChunkAtAsync(cx, cz).await()
    }

    private fun blockKey(x: Int, y: Int, z: Int): Long =
        (x.toLong() and 0x3FFFFFF shl 38) or (z.toLong() and 0x3FFFFFF shl 12) or (y.toLong() and 0xFFF)

    /** Returns the cells saved, or -1 when there are none or more than `snapshot.max-cells`. */
    suspend fun capture(city: EndCity): Int {
        val world = city.getWorld() ?: run {
            plugin.logger.warning("[Snapshot] World '${city.world}' not loaded; cannot capture city #${city.id}")
            return -1
        }
        val byChunk = cellsByChunk(city, capturePad())
        val total = byChunk.values.sumOf { it.size }
        if (total == 0) { plugin.logger.warning("[Snapshot] City #${city.id} has no piece cells to capture"); return -1 }
        if (total > maxCells()) {
            plugin.logger.warning("[Snapshot] City #${city.id} capture aborted: $total cells exceeds snapshot.max-cells (${maxCells()})")
            return -1
        }

        val origin = Triple(city.region.minX, city.region.minY, city.region.minZ)
        val blocks = HashMap<Triple<Int, Int, Int>, String>(total)
        var failed = 0

        for ((_, cells) in byChunk) {
            val rep = cells.first()
            val loc = Location(world, rep.first.toDouble(), rep.second.toDouble(), rep.third.toDouble())
            loadChunk(world, rep.first shr 4, rep.third shr 4)
            suspendCancellableCoroutine<Unit> { cont ->
                plugin.scheduler.runAtLocation(loc, Runnable {
                    try {
                        // Normally still loaded from loadChunk; this only covers an unload in between.
                        if (!world.isChunkLoaded(rep.first shr 4, rep.third shr 4)) world.getChunkAt(rep.first shr 4, rep.third shr 4)
                        for (c in cells) {
                            try {
                                val data = world.getBlockAt(c.first, c.second, c.third).blockData.asString
                                blocks[Triple(c.first - origin.first, c.second - origin.second, c.third - origin.third)] = data
                            } catch (e: Exception) {
                                if (failed++ < 5) plugin.logger.warning("[Snapshot] capture read failed at ${c.first},${c.second},${c.third}: ${e.message}")
                            }
                        }
                    } finally {
                        cont.resume(Unit)
                    }
                })
            }
        }

        // Only the ship piece contains a dragon head, so finding one locates the ship.
        blocks.entries.firstOrNull { isDragonHead(it.value) }?.let { (rel, _) ->
            if (!city.hasShip) plugin.logger.info("[Snapshot] City #${city.id} contains an End Ship (dragon head found).")
            plugin.cityManager.setShipAnchor(
                city.id, origin.first + rel.first, origin.second + rel.second, origin.third + rel.third,
            )
        }

        return withContext(Dispatchers.IO) {
            val data = SnapshotData(city.world, origin.first, origin.second, origin.third, blocks)
            val file = fileFor(city.id)
            file.writeBytes(CompressionUtil.compressObject(data))
            plugin.cityManager.setSnapshotFile(city.id, file.name)
            plugin.logger.info("[Snapshot] Captured city #${city.id}: stored ${blocks.size}/$total cells" +
                (if (failed > 0) " ($failed read failures)" else "") + " (${CompressionUtil.formatSize(file.length())}, pad ${capturePad()})")
            blocks.size
        }
    }

    fun hasSnapshot(cityId: Int): Boolean = fileFor(cityId).exists()

    /** Returns the cells restored, or -1 without a readable snapshot. Suspends until every chunk is done. */
    suspend fun restore(city: EndCity): Int {
        val file = fileFor(city.id)
        if (!file.exists()) { plugin.logger.warning("[Snapshot] No snapshot for city #${city.id}"); return -1 }

        val data = withContext(Dispatchers.IO) {
            try { CompressionUtil.decompressObject<SnapshotData>(file.readBytes()) }
            catch (e: Exception) {
                // An unreadable snapshot makes /betterend reset silently do nothing.
                plugin.logger.warning("[Snapshot] load failed for city #${city.id}: ${e.message}")
                com.esmpfun.betterend.integrations.MetricsService.reportHandled(e, "snapshot-load")
                null
            }
        } ?: return -1

        val world = plugin.server.getWorld(data.worldName) ?: run {
            plugin.logger.warning("[Snapshot] World '${data.worldName}' not loaded; cannot restore city #${city.id}")
            return -1
        }

        val byChunk = HashMap<Long, MutableList<Pair<Location, String>>>()
        // A taken dragon head stays gone; restoring it would re-arm anything keyed on it.
        val skipHead = plugin.cityManager.byId(city.id)?.headTaken == true
        for ((rel, str) in data.blocks) {
            if (skipHead && isDragonHead(str)) continue
            val x = data.originX + rel.first; val y = data.originY + rel.second; val z = data.originZ + rel.third
            val ck = (x shr 4).toLong() shl 32 or ((z shr 4).toLong() and 0xffffffffL)
            byChunk.getOrPut(ck) { mutableListOf() }.add(Location(world, x.toDouble(), y.toDouble(), z.toDouble()) to str)
        }

        val total = data.blocks.size
        val remaining = AtomicInteger(byChunk.size)
        val done = CompletableDeferred<Unit>()
        val restored = AtomicInteger(0)
        val failed = AtomicInteger(0)
        for ((_, list) in byChunk) {
            val first = list.first().first
            loadChunk(world, first.blockX shr 4, first.blockZ shr 4)
            plugin.scheduler.runAtLocation(first, Runnable {
                try {
                    if (!world.isChunkLoaded(first.blockX shr 4, first.blockZ shr 4)) world.getChunkAt(first.blockX shr 4, first.blockZ shr 4)
                    for ((bloc, str) in list) {
                        try {
                            val bd = Bukkit.createBlockData(str)
                            bloc.block.setBlockData(bd, false) // no physics, so no cascade of updates
                            restored.incrementAndGet()
                        } catch (e: Exception) {
                            if (failed.getAndIncrement() < 5)
                                plugin.logger.warning("[Snapshot] restore failed at ${bloc.blockX},${bloc.blockY},${bloc.blockZ} for '$str': ${e.message}")
                        }
                    }
                } finally {
                    if (remaining.decrementAndGet() == 0) done.complete(Unit)
                }
            })
        }
        if (byChunk.isEmpty()) done.complete(Unit)
        done.await()
        val f = failed.get()
        plugin.logger.info("[Snapshot] Restored city #${city.id}: placed ${restored.get()}/$total cells" +
            (if (f > 0) " ($f failures, see warnings above)" else ""))
        return restored.get()
    }

    private fun isDragonHead(blockData: String) =
        blockData.contains("dragon_head") || blockData.contains("dragon_wall_head")

    fun deleteSnapshot(cityId: Int): Boolean = fileFor(cityId).let { if (it.exists()) it.delete() else false }
}
