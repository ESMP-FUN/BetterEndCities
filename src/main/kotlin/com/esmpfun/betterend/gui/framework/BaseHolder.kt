package com.esmpfun.betterend.gui.framework

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

/** Links an open inventory back to its [VcGui]. */
abstract class BaseHolder : InventoryHolder {
    private lateinit var inv: Inventory

    lateinit var gui: VcGui
        internal set

    fun attach(inventory: Inventory) {
        inv = inventory
    }

    override fun getInventory(): Inventory = inv
}
