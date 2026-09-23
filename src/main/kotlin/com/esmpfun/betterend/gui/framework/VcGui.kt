package com.esmpfun.betterend.gui.framework

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory

/**
 * Base class for every TCP chest GUI.
 *
 * Subclasses construct themselves with `super(...)`, populate slots via
 * [set] and the [VcGuiItem.Companion.of] builders, then call [open] to
 * show the GUI to a player. Click dispatch happens via [VcGuiItem.onClick]
 * closures - there is no central click-handler method to override.
 *
 * Lifecycle hooks:
 * - [handleClose] - fires once when the player closes the inventory.
 *   Override to flush debounced saves, cancel timers, etc.
 * - [handleDrag] - fires when a drag lands on a slot whose [VcGuiItem]
 *   declares `acceptsDrag = true`. Default cancels (stamp semantics);
 *   override and DON'T cancel to allow the bulk-deposit pattern (drag
 *   many items in, read on close).
 *
 * Layout strategies (pagination, tabs, scrolling) are *composable
 * utilities* you instantiate as fields, not subclasses you extend.
 * Each strategy provides an `applyTo(gui)` method that fills slots.
 *
 * @property rows 1..6, becomes a chest inventory of [rows] x 9 slots.
 * @property title Adventure [Component]. On Paper 1.21+ the title is
 *   serialized to legacy via [LegacyComponentSerializer] because
 *   `Bukkit.createInventory(holder, size, String)` is what's stable
 *   across the API revisions we target - we'll switch to the MenuType
 *   API if/when we drop 1.21.1 compat.
 * @property holder The session-state holder. Set up by the subclass
 *   constructor (concrete `BaseHolder` subclass with whatever fields).
 *   The framework attaches the inventory to it and back-references the
 *   gui.
 * @property requiredPermission Re-checked on every click by
 *   [VcGuiListener]. Null = no permission check (default-open GUI).
 */
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

    /** Snapshot of the current slot wiring. Used by [VcGuiListener] to look up handlers. */
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

    /**
     * Render the current slot wiring into the given inventory. Called
     * by [open] once on creation and by [update] when slot contents
     * change after open.
     */
    open fun render(inv: Inventory) {
        for (i in slots.indices) inv.setItem(i, slots[i]?.stack)
    }

    /**
     * Re-render the GUI in-place if [holder]'s inventory is still open.
     * Safe to call from inside an [VcGuiItem.onClick] handler after
     * mutating slot state.
     */
    fun update() {
        render(holder.inventory)
    }

    /**
     * Build the inventory and open it for the player. Re-entrant -
     * calling [open] on an already-open GUI reuses the inventory.
     */
    fun open(player: Player) {
        val legacyTitle = LegacyComponentSerializer.legacySection().serialize(title)
        val inv = Bukkit.createInventory(holder, rows * 9, legacyTitle)
        holder.attach(inv)
        render(inv)
        player.openInventory(inv)
    }

    /**
     * If true, [VcGuiListener] skips ALL click and drag dispatch - players
     * can place, take, swap, hotbar, and drag freely. [handleClose] still
     * fires (and is typically where freely-editable GUIs read the final
     * inventory contents).
     *
     * Default: false (standard controlled GUI). Override to `true` for
     * bulk-deposit / paint-bucket / item-staging style inventories where
     * the player is the source of truth, not the slot wiring.
     *
     * Subclasses generally shouldn't put `VcGuiItem`s into the inventory
     * when this is true - anything they set would still be visible but
     * non-clickable, which is confusing.
     */
    open val freelyEditable: Boolean = false

    /** Hook for cleanup on inventory close. Default: no-op. */
    open fun handleClose(player: Player) {}

    /** Hook for drag handling. Default: cancel (the dup-exploit guard). The
     *  central listener only routes here when [VcGuiItem.acceptsDrag] is true
     *  for the target slot, so override this to consume the stamped item.
     *
     *  For the bulk-deposit pattern (drag many items in, commit on close),
     *  the deposit GUI overrides [VcGui.handleDrag] to NOT cancel, plus
     *  overrides [VcGuiListener]'s general click-cancel behaviour by having
     *  every slot declare `acceptsDrag = true` - but in practice you can
     *  just override `handleDrag` to a no-op and let drags through. */
    open fun handleDrag(ctx: DragContext) {
        ctx.event.isCancelled = true
    }
}
