package com.esmpfun.betterend.managers

import com.esmpfun.betterend.BetterEnd
import com.esmpfun.betterend.models.EndCity
import com.esmpfun.betterend.models.IntBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.Location
import java.sql.Statement
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Every registered city, fully cached; there are few enough that lookups are a linear scan. */
class CityManager(private val plugin: BetterEnd) {

    private val cache = ConcurrentHashMap<Int, EndCity>()

    // Loot cycle start per city, epoch ms. Only checked when a chest opens, so
    // there is no timer and cities refresh staggered by their first looting.
    private val cycleStarts = ConcurrentHashMap<Int, AtomicLong>()

    /** Call once at startup. */
    suspend fun preload() = withContext(Dispatchers.IO) {
        cache.clear()
        cycleStarts.clear()
        plugin.databaseManager.connection.use { conn ->
            conn.prepareStatement(
                "SELECT id, world, min_x, min_y, min_z, max_x, max_y, max_z, " +
                    "origin_x, origin_y, origin_z, created_at, last_reset, snapshot_file, loot_cycle_start, has_ship, ship_x, ship_y, ship_z, head_taken FROM cities"
            ).use { stmt ->
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val id = rs.getInt("id")
                        val region = IntBox(
                            rs.getInt("min_x"), rs.getInt("min_y"), rs.getInt("min_z"),
                            rs.getInt("max_x"), rs.getInt("max_y"), rs.getInt("max_z"),
                        )
                        cache[id] = EndCity(
                            id = id,
                            world = rs.getString("world"),
                            region = region,
                            origin = Triple(rs.getInt("origin_x"), rs.getInt("origin_y"), rs.getInt("origin_z")),
                            pieces = loadPieces(conn, id),
                            createdAt = rs.getLong("created_at"),
                            lastReset = rs.getLong("last_reset").takeIf { !rs.wasNull() },
                            snapshotFile = rs.getString("snapshot_file"),
                            hasShip = rs.getInt("has_ship") != 0,
                            shipAnchor = rs.getInt("ship_x").let { x ->
                                if (rs.wasNull()) null else Triple(x, rs.getInt("ship_y"), rs.getInt("ship_z"))
                            },
                            headTaken = rs.getInt("head_taken") != 0,
                        )
                        val cycle = rs.getLong("loot_cycle_start")
                        if (!rs.wasNull() && cycle > 0L) cycleStarts[id] = AtomicLong(cycle)
                    }
                }
            }
        }
        plugin.logger.info("Loaded ${cache.size} End ${if (cache.size == 1) "City" else "Cities"} into cache")
    }

    private fun loadPieces(conn: java.sql.Connection, cityId: Int): List<IntBox> {
        val out = mutableListOf<IntBox>()
        conn.prepareStatement(
            "SELECT min_x, min_y, min_z, max_x, max_y, max_z FROM city_pieces WHERE city_id = ?"
        ).use { stmt ->
            stmt.setInt(1, cityId)
            stmt.executeQuery().use { rs ->
                while (rs.next()) out.add(
                    IntBox(
                        rs.getInt("min_x"), rs.getInt("min_y"), rs.getInt("min_z"),
                        rs.getInt("max_x"), rs.getInt("max_y"), rs.getInt("max_z"),
                    )
                )
            }
        }
        return out
    }

    suspend fun existsAt(world: String, origin: Triple<Int, Int, Int>): Boolean =
        withContext(Dispatchers.IO) {
            cache.values.any { it.world == world && it.origin == origin } || run {
                plugin.databaseManager.connection.use { conn ->
                    conn.prepareStatement(
                        "SELECT 1 FROM cities WHERE world = ? AND origin_x = ? AND origin_y = ? AND origin_z = ?"
                    ).use { stmt ->
                        stmt.setString(1, world)
                        stmt.setInt(2, origin.first); stmt.setInt(3, origin.second); stmt.setInt(4, origin.third)
                        stmt.executeQuery().use { it.next() }
                    }
                }
            }
        }

    /** Null when a city with this origin already exists, or the write failed. */
    suspend fun registerCity(
        world: String,
        region: IntBox,
        origin: Triple<Int, Int, Int>,
        pieces: List<IntBox>,
    ): EndCity? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.autoCommit = false
                try {
                    val cityId = conn.prepareStatement(
                        "INSERT INTO cities (world, min_x, min_y, min_z, max_x, max_y, max_z, " +
                            "origin_x, origin_y, origin_z, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS
                    ).use { stmt ->
                        stmt.setString(1, world)
                        stmt.setInt(2, region.minX); stmt.setInt(3, region.minY); stmt.setInt(4, region.minZ)
                        stmt.setInt(5, region.maxX); stmt.setInt(6, region.maxY); stmt.setInt(7, region.maxZ)
                        stmt.setInt(8, origin.first); stmt.setInt(9, origin.second); stmt.setInt(10, origin.third)
                        stmt.setLong(11, now)
                        stmt.executeUpdate()
                        stmt.generatedKeys.use { if (it.next()) it.getInt(1) else error("no generated city id") }
                    }
                    conn.prepareStatement(
                        "INSERT INTO city_pieces (city_id, min_x, min_y, min_z, max_x, max_y, max_z) VALUES (?,?,?,?,?,?,?)"
                    ).use { stmt ->
                        for (p in pieces) {
                            stmt.setInt(1, cityId)
                            stmt.setInt(2, p.minX); stmt.setInt(3, p.minY); stmt.setInt(4, p.minZ)
                            stmt.setInt(5, p.maxX); stmt.setInt(6, p.maxY); stmt.setInt(7, p.maxZ)
                            stmt.addBatch()
                        }
                        stmt.executeBatch()
                    }
                    conn.commit()
                    val city = EndCity(cityId, world, region, origin, pieces, now)
                    cache[cityId] = city
                    city
                } catch (e: Exception) {
                    conn.rollback()
                    throw e
                } finally {
                    conn.autoCommit = true
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[CityManager] registerCity failed for $world @ $origin: ${e.message}")
            null
        }
    }

    /**
     * True for exactly one caller once the cycle is over [refreshMs] old; that
     * caller clears the copies and persists. [refreshMs] <= 0 never refreshes.
     */
    fun beginCycleIfDue(cityId: Int, refreshMs: Long): Boolean {
        if (refreshMs <= 0L) return false
        val al = cycleStarts.getOrPut(cityId) { AtomicLong(0L) }
        while (true) {
            val cur = al.get()
            val now = System.currentTimeMillis()
            if (cur != 0L && now - cur < refreshMs) return false
            if (al.compareAndSet(cur, now)) return true
        }
    }

    /** Epoch ms, 0 when no cycle has started. */
    fun cycleStart(cityId: Int): Long = cycleStarts[cityId]?.get() ?: 0L

    /** Starts a cycle now; the caller clears the copies and persists. */
    fun forceNewCycle(cityId: Int) {
        cycleStarts.getOrPut(cityId) { AtomicLong(0L) }.set(System.currentTimeMillis())
    }

    suspend fun persistCycleStart(cityId: Int) = withContext(Dispatchers.IO) {
        val value = cycleStarts[cityId]?.get() ?: return@withContext
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement("UPDATE cities SET loot_cycle_start = ? WHERE id = ?").use { stmt ->
                    stmt.setLong(1, value)
                    stmt.setInt(2, cityId)
                    stmt.executeUpdate()
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[CityManager] persistCycleStart($cityId) failed: ${e.message}")
        }
    }

    suspend fun setLastReset(id: Int, at: Long) = withContext(Dispatchers.IO) {
        if (!cache.containsKey(id)) return@withContext
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement("UPDATE cities SET last_reset = ? WHERE id = ?").use { stmt ->
                    stmt.setLong(1, at)
                    stmt.setInt(2, id)
                    stmt.executeUpdate()
                }
            }
            cache.computeIfPresent(id) { _, c -> c.copy(lastReset = at) }
        } catch (e: Exception) {
            plugin.logger.warning("[CityManager] setLastReset($id) failed: ${e.message}")
        }
    }

    suspend fun setHasShip(id: Int, hasShip: Boolean) = withContext(Dispatchers.IO) {
        val city = cache[id] ?: return@withContext
        if (city.hasShip == hasShip) return@withContext
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement("UPDATE cities SET has_ship = ? WHERE id = ?").use { stmt ->
                    stmt.setInt(1, if (hasShip) 1 else 0)
                    stmt.setInt(2, id)
                    stmt.executeUpdate()
                }
            }
            cache.computeIfPresent(id) { _, c -> c.copy(hasShip = hasShip) }
        } catch (e: Exception) {
            plugin.logger.warning("[CityManager] setHasShip($id) failed: ${e.message}")
        }
    }

    /** Only the first position counts; also marks the city as having a ship. */
    suspend fun setShipAnchor(id: Int, x: Int, y: Int, z: Int) = withContext(Dispatchers.IO) {
        val city = cache[id] ?: return@withContext
        if (city.shipAnchor != null) return@withContext
        cache.computeIfPresent(id) { _, c -> c.copy(shipAnchor = Triple(x, y, z), hasShip = true) }
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement("UPDATE cities SET ship_x = ?, ship_y = ?, ship_z = ?, has_ship = 1 WHERE id = ?").use { stmt ->
                    stmt.setInt(1, x); stmt.setInt(2, y); stmt.setInt(3, z)
                    stmt.setInt(4, id)
                    stmt.executeUpdate()
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[CityManager] setShipAnchor($id) failed: ${e.message}")
        }
    }

    suspend fun setHeadTaken(id: Int) = withContext(Dispatchers.IO) {
        val city = cache[id] ?: return@withContext
        if (city.headTaken) return@withContext
        cache.computeIfPresent(id) { _, c -> c.copy(headTaken = true) }
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement("UPDATE cities SET head_taken = 1 WHERE id = ?").use { stmt ->
                    stmt.setInt(1, id)
                    stmt.executeUpdate()
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[CityManager] setHeadTaken($id) failed: ${e.message}")
        }
    }

    suspend fun setSnapshotFile(id: Int, fileName: String?) = withContext(Dispatchers.IO) {
        if (!cache.containsKey(id)) return@withContext
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement("UPDATE cities SET snapshot_file = ? WHERE id = ?").use { stmt ->
                    stmt.setString(1, fileName)
                    stmt.setInt(2, id)
                    stmt.executeUpdate()
                }
            }
            cache.computeIfPresent(id) { _, c -> c.copy(snapshotFile = fileName) }
        } catch (e: Exception) {
            plugin.logger.warning("[CityManager] setSnapshotFile($id) failed: ${e.message}")
        }
    }

    fun getCachedCityAt(loc: Location): EndCity? =
        cache.values.firstOrNull { it.containsInRegion(loc) }

    fun getCachedCityInPaddedRegion(loc: Location, pad: Int): EndCity? =
        cache.values.firstOrNull { it.containsInPaddedRegion(loc, pad) }

    fun byId(id: Int): EndCity? = cache[id]

    /** A copy, safe to iterate. */
    fun all(): Collection<EndCity> = cache.values.toList()

    suspend fun deleteCity(id: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement("DELETE FROM cities WHERE id = ?").use { stmt ->
                    stmt.setInt(1, id)
                    val removed = stmt.executeUpdate() > 0
                    if (removed) {
                        cache.remove(id)
                        cycleStarts.remove(id)
                    }
                    removed
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[CityManager] deleteCity($id) failed: ${e.message}")
            false
        }
    }
}
