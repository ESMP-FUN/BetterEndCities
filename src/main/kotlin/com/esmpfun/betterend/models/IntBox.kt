package com.esmpfun.betterend.models

import org.bukkit.Location
import org.bukkit.util.BoundingBox

/**
 * An inclusive, integer block-coordinate axis-aligned box. World-agnostic
 * (the owning [EndCity] carries the world). Used both for a city's region
 * envelope and for each structure piece's bounds.
 */
data class IntBox(
    val minX: Int, val minY: Int, val minZ: Int,
    val maxX: Int, val maxY: Int, val maxZ: Int,
) {
    fun contains(x: Int, y: Int, z: Int): Boolean =
        x in minX..maxX && y in minY..maxY && z in minZ..maxZ

    fun contains(loc: Location): Boolean = contains(loc.blockX, loc.blockY, loc.blockZ)

    /** This box expanded by [pad] blocks on every face. */
    fun expanded(pad: Int): IntBox =
        IntBox(minX - pad, minY - pad, minZ - pad, maxX + pad, maxY + pad, maxZ + pad)

    companion object {
        /**
         * Converts a structure or structure-piece [BoundingBox] to block coords.
         * Paper builds those boxes straight from the game's own box, whose max
         * corner is already the last block inside, so nothing is subtracted.
         */
        fun fromBukkit(b: BoundingBox): IntBox = IntBox(
            b.minX.toInt(), b.minY.toInt(), b.minZ.toInt(),
            b.maxX.toInt(), b.maxY.toInt(), b.maxZ.toInt(),
        )

        /** The smallest box enclosing all of [boxes]. Throws on an empty list. */
        fun union(boxes: List<IntBox>): IntBox {
            require(boxes.isNotEmpty()) { "cannot union zero boxes" }
            return IntBox(
                boxes.minOf { it.minX }, boxes.minOf { it.minY }, boxes.minOf { it.minZ },
                boxes.maxOf { it.maxX }, boxes.maxOf { it.maxY }, boxes.maxOf { it.maxZ },
            )
        }
    }
}
