package com.esmpfun.betterend.utils

import org.bukkit.Location
import org.bukkit.generator.structure.Structure

object StructureUtil {

    /**
     * Whether [loc] is within [near] blocks of a [structure] that reaches its
     * chunk. Only that chunk is asked: `getStructures` loads the chunk it's
     * given, and a neighbour may belong to another Folia region.
     */
    fun isNear(loc: Location, structure: Structure, near: Double): Boolean {
        val world = loc.world ?: return false
        return world.getStructures(loc.blockX shr 4, loc.blockZ shr 4, structure).any {
            // expand() mutates; clone first so the live box is left alone.
            it.boundingBox.clone().expand(near).contains(loc.x, loc.y, loc.z)
        }
    }
}
