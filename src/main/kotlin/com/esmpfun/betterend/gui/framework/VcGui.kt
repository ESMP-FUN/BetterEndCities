package com.esmpfun.betterend.gui.framework

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory

/** A chest GUI whose slots carry their own click handlers; see [VcGuiListener]. */
abstract class VcGui(
    val rows: Int,
    val title: Component,
    val holder: BaseHolder,
    val requiredPermission: String? = null,
) {
    init {
        require(rows in 1..6) { "VcGui rows must be 1..6, got $rows" }
        holder.gui = this
    }

    private val slots = arrayOfNulls<VcGuiItem>(rows * 9)

    internal fun items(): Array<VcGuiItem?> = slots

    fun set(slot: Int, item: VcGuiItem?) {
        require(slot in 0 until rows * 9) { "slot $slot out of range 0..${rows * 9 - 1}" }
        slots[slot] = item
    }

    fun set(row: Int, col: Int, item: VcGuiItem?) = set(row * 9 + col, item)

    fun fill(slotRange: IntRange, item: VcGuiItem) {
        for (s in slotRange) set(s, item)
    }

    fun clear() {
        for (i in slots.indices) slots[i] = null
    }

    open fun render(inv: Inventory) {
        for (i in slots.indices) inv.setItem(i, slots[i]?.stack)
    }

    fun update() {
        render(holder.inventory)
    }

    fun open(player: Player) {
        val inv = Bukkit.createInventory(holder, rows * 9, title)
        holder.attach(inv)
        render(inv)
        player.openInventory(inv)
    }

    open val freelyEditable: Boolean = false

    open fun handleClose(player: Player) {}

    open fun handleDrag(ctx: DragContext) {
        ctx.event.isCancelled = true
    }
}
