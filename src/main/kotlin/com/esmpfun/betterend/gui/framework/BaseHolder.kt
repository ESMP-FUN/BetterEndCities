package com.esmpfun.betterend.gui.framework

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

/**
 * Marker interface that ties a Bukkit [Inventory] back to its owning
 * [VcGui] instance. Every [VcGui.open] creates an `Inventory` whose
 * `holder` is one of these - the central [VcGuiListener] uses that
 * holder to find the GUI and dispatch the click.
 *
 * Each concrete `VcGui` subclass declares its own `Holder` data class
 * (extends [BaseHolder]) so it can carry session state (drafts, page
 * index, etc.) without external session tracking.
 *
 * ## Why a sealed-ish pattern?
 *
 * - We can't make this sealed across subclasses defined in arbitrary
 *   files (Kotlin's sealed class checks compile-unit, and our GUIs live
 *   beside one another in the same module - but new sibling premium
 *   modules would need to define their own GUIs, which a sealed
 *   interface across modules forbids). Keep it open; the dispatcher
 *   uses `is BaseHolder` for the framework-wide invariant check.
 * - We use a small abstract base class instead of a raw interface so
 *   sub-implementations don't have to re-implement the `inv` storage +
 *   permission accessor.
 */
abstract class BaseHolder : InventoryHolder {
    private lateinit var inv: Inventory

    /** The GUI this holder belongs to. Set by [VcGui.open] before the inventory opens. */
    lateinit var gui: VcGui
        internal set

    fun attach(inventory: Inventory) {
        inv = inventory
    }

    override fun getInventory(): Inventory = inv
}
