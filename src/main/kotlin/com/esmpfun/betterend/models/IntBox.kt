package com.esmpfun.betterend.models

import org.bukkit.Location
import org.bukkit.util.BoundingBox

/** An inclusive block-coordinate box; the owning [EndCity] carries the world. */
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
        // Structure boxes from Paper have an inclusive max corner, unlike a block's own BoundingBox.
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
