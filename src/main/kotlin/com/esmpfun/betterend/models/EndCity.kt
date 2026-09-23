package com.esmpfun.betterend.models

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World

/**
 * A registered End City. [region] encloses every piece, [origin] (the
 * structure's min corner) identifies the city across chunk loads, and
 * [pieces] are the exact tower, bridge and ship boxes.
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
    // Pieces carry no names, so the ship is recognised by its dragon head (no
    // other end city piece has one) or by its elytra frame.
    val hasShip: Boolean = false,
    /** A block inside the ship (its dragon head or elytra frame); picks the ship out of [pieces]. */
    val shipAnchor: Triple<Int, Int, Int>? = null,
    /** The ship's dragon head was taken, so resets must not put it back. */
    val headTaken: Boolean = false,
) {
    fun getWorld(): World? = Bukkit.getWorld(world)

    /** Whether [loc] is anywhere within [region]. */
    fun containsInRegion(loc: Location): Boolean =
        loc.world?.name == world && region.contains(loc)

    /** Whether [loc] is within [region] grown by [pad] blocks. */
    fun containsInPaddedRegion(loc: Location, pad: Int): Boolean =
        loc.world?.name == world && region.expanded(pad).contains(loc)

    /** Whether [loc] is inside a piece itself; decides whether a container is city loot. */
    fun inStructurePiece(loc: Location): Boolean = inStructurePiece(loc, 0)

    /** Whether [loc] is inside any piece grown by [pad] blocks. */
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
