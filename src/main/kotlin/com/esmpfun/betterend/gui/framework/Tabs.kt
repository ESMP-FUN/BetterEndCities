package com.esmpfun.betterend.gui.framework

import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/**
 * One tab in a [Tabs] strategy.
 *
 * @property icon Material rendered as the tab button (top row, by default).
 * @property displayName MiniMessage; the active tab gets a `<u>` underline applied automatically.
 * @property populate Called with the [VcGui] every time this tab becomes active. Should fill
 *   the slots outside [Tabs.tabRow] (the strategy handles the tab buttons themselves).
 */
data class Tab(
    val icon: Material,
    val displayName: String,
    val populate: (VcGui) -> Unit,
)

/**
 * Tabbed GUI strategy. Renders a row of [Tab.icon] buttons; the active
 * tab is highlighted; clicking another tab swaps the active populate.
 *
 * Composable utility - instantiate on a [VcGui] subclass and call
 * [applyTo] in [VcGui.render].
 *
 * ```
 * private val tabs = Tabs(listOf(
 *     Tab(Material.LODESTONE,    "<gold>Chambers", ::populateChambersTab),
 *     Tab(Material.GOLD_INGOT,   "<yellow>Loot",   ::populateLootTab),
 * ), tabRow = 0)
 *
 * override fun render(inv: Inventory) {
 *     tabs.applyTo(this)
 *     super.render(inv)
 * }
 * ```
 *
 * @property tabRow Row index that hosts the tab buttons. Default 0 (top
 *   row). Tab buttons are placed from column 0 leftwards.
 */
class Tabs(
    val tabs: List<Tab>,
    val tabRow: Int = 0,
) {
    private val mm: MiniMessage = MiniMessage.miniMessage()

    var active: Int = 0
        private set

    init {
        require(tabs.isNotEmpty()) { "Tabs must have at least one tab" }
    }

    /** Switch to the given tab and re-apply layout. Call [VcGui.update] after this to repaint. */
    fun select(gui: VcGui, index: Int) {
        active = index.coerceIn(0, tabs.size - 1)
        applyTo(gui)
    }

    /** Render the tab buttons in [tabRow] and run the active tab's [Tab.populate]. */
    fun applyTo(gui: VcGui) {
        // Clear tabRow first so re-renders don't accumulate old buttons.
        for (col in 0 until 9) gui.set(tabRow, col, null)

        for ((index, tab) in tabs.withIndex()) {
            if (index >= 9) break  // can't fit more than 9 tabs in a row
            val isActive = index == active
            val name = if (isActive) "<gold><u>${tab.displayName}</u>" else tab.displayName
            val lore = if (isActive)
                listOf(mm.deserialize("<dark_gray>Selected").decoration(TextDecoration.ITALIC, false))
            else
                listOf(mm.deserialize("<gray>Click to switch").decoration(TextDecoration.ITALIC, false))
            val displayName = mm.deserialize(name).decoration(TextDecoration.ITALIC, false)
            val stack = ItemStack(tab.icon, 1)
            stack.editMeta { meta ->
                meta.displayName(displayName)
                meta.lore(lore)
                if (isActive) meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true)
            }
            gui.set(tabRow, index, VcGuiItem(
                stack = stack,
                onClick = { _ ->
                    // `gui` is captured from applyTo's parameter - no need
                    // to dig it out of the click context. `select` calls
                    // applyTo (which mutates in-memory slots); `update`
                    // flushes those changes into the open inventory.
                    if (active != index) {
                        select(gui, index)
                        gui.update()
                    }
                },
            ))
        }
        // Run the active tab's populate to fill non-tab slots.
        tabs[active].populate(gui)
    }
}
