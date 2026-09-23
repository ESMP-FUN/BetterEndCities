package com.esmpfun.betterend.utils

import org.bukkit.Location
import org.bukkit.generator.structure.Structure

object StructureUtil {

    /**
     * True if [loc] is inside, or within [near] blocks of, an instance of
     * [structure] that reaches [loc]'s own chunk.
     *
     * Only that one chunk is asked: `World.getStructures` loads (or even
     * generates) the chunk it is asked about, and on Folia a neighbouring
     * chunk may belong to another region thread. Any point inside a
     * structure's box has a reference in its own chunk, so nothing inside is
     * missed.
     */
    fun isNear(loc: Location, structure: Structure, near: Double): Boolean {
        val world = loc.world ?: return false
        return world.getStructures(loc.blockX shr 4, loc.blockZ shr 4, structure).any {
            // expand() mutates; clone first so the live box is left alone.
            it.boundingBox.clone().expand(near).contains(loc.x, loc.y, loc.z)
        }
    }
}
