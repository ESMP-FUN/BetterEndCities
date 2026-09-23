package com.esmpfun.betterend.models

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World

/**
 * A registered End City.
 *
 * [region] is the envelope AABB (union of all structure pieces). [origin] is
 * the raw structure bounding-box min corner — the stable identity used to
 * dedup the same city seen across many chunk loads. [pieces] are the
 * per-structure-piece bounds used for exact provenance: a block counts as
 * city content only if it falls inside one of these. When the city generated
 * with a ship, the ship is simply one more piece.
 *
 * No approval state: a discovered city is active immediately.
 */
data class EndCity(
    val id: Int,
    val world: String,
    val region: IntBox,
    val origin: Triple<Int, Int, Int>,
    val pieces: List<IntBox>,
    val createdAt: Long,
    val lastReset: Long? = null,
    val snapshotFile: String? = null,
    /**
     * True once a ship has been positively identified in this city. The ship
     * is the `end_city/ship` template piece; since [org.bukkit.generator.structure.StructurePiece]
     * exposes no piece names, we fingerprint it by its unique dragon-head
     * block (no other end city template contains one) — detected during
     * snapshot capture, or when the ship's elytra frame is first seen.
     */
    val hasShip: Boolean = false,
    /** A block inside the ship (its dragon head or elytra frame); picks the ship out of [pieces]. */
    val shipAnchor: Triple<Int, Int, Int>? = null,
    /** The ship's dragon head was taken, so resets must not put it back. */
    val headTaken: Boolean = false,
) {
    fun getWorld(): World? = Bukkit.getWorld(world)

    /** Whether [loc] is anywhere within the city's envelope (protection-box test). */
    fun containsInRegion(loc: Location): Boolean =
        loc.world?.name == world && region.contains(loc)

    /**
     * Whether [loc] is within the city's envelope expanded by [pad] blocks. The
     * pad covers edge decoration that generates a few blocks outside the
     * structure's declared bounding box.
     */
    fun containsInPaddedRegion(loc: Location, pad: Int): Boolean =
        loc.world?.name == world && region.expanded(pad).contains(loc)

    /**
     * Whether [loc] is inside an actual generated structure piece — the test
     * that decides if a container at [loc] is city loot vs. a player-built
     * block.
     */
    fun inStructurePiece(loc: Location): Boolean = inStructurePiece(loc, 0)

    /**
     * Whether [loc] is inside any structure piece expanded by [pad] blocks.
     * With [pad] = 0 this is exact piece membership (loot provenance); a small
     * pad is used by protection to cover edge decoration and a thin shell
     * around each tower while leaving the void *between* pieces untouched.
     */
    fun inStructurePiece(loc: Location, pad: Int): Boolean {
        if (loc.world?.name != world) return false
        val x = loc.blockX; val y = loc.blockY; val z = loc.blockZ
        return pieces.any { it.expanded(pad).contains(x, y, z) }
    }

    /** Whether [loc] is inside the ship piece expanded by [pad]. False while the ship's position is unknown. */
    fun inShip(loc: Location, pad: Int): Boolean {
        val a = shipAnchor ?: return false
        if (loc.world?.name != world) return false
        val x = loc.blockX; val y = loc.blockY; val z = loc.blockZ
        return pieces.any { it.contains(a.first, a.second, a.third) && it.expanded(pad).contains(x, y, z) }
    }
}
