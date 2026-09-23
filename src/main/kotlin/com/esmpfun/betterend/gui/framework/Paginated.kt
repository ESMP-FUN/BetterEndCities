package com.esmpfun.betterend.gui.framework

/**
 * Pagination utility - fills a contiguous slot range with a slice of a
 * data source.
 *
 * Composable; you instantiate one as a field on your [VcGui] subclass:
 *
 * ```
 * private val pagination = Paginated(
 *     source = { plugin.chamberManager.getAllChambers() },
 *     render = { chamber -> VcGuiItem.of(Material.LODESTONE, "<gold>${chamber.name}") { open detail... } },
 *     slotRange = 0..44,  // first 5 rows
 * )
 *
 * override fun render(inv: Inventory) {
 *     pagination.applyTo(this)
 *     super.render(inv)
 * }
 * ```
 *
 * Navigation: call [next] / [prev] from "next-page" / "prev-page" button
 * click handlers, then call [VcGui.update].
 *
 * @property source Computed each time [applyTo] is called - supports live
 *   data without explicit invalidation.
 * @property render Converts a source element into a [VcGuiItem].
 * @property slotRange Contiguous slot range to fill. Default 0..44 (first
 *   5 rows of a 6-row GUI; bottom row reserved for navigation controls).
 */
class Paginated<T>(
    val source: () -> List<T>,
    val render: (T) -> VcGuiItem,
    val slotRange: IntRange = 0..44,
) {
    private val pageSize: Int = slotRange.last - slotRange.first + 1

    var page: Int = 0
        private set

    fun pageCount(): Int {
        val total = source().size
        return ((total + pageSize - 1) / pageSize).coerceAtLeast(1)
    }

    fun isFirstPage(): Boolean = page == 0
    fun isLastPage(): Boolean = page >= pageCount() - 1

    fun next() {
        page = (page + 1).coerceAtMost(pageCount() - 1)
    }

    fun prev() {
        page = (page - 1).coerceAtLeast(0)
    }

    fun goTo(newPage: Int) {
        page = newPage.coerceIn(0, pageCount() - 1)
    }

    /** Fill [VcGui]'s slots in [slotRange] with the current page. Slots
     *  outside [slotRange] are untouched (let the subclass own nav rows). */
    fun applyTo(gui: VcGui) {
        val all = source()
        val from = page * pageSize
        val to = (from + pageSize).coerceAtMost(all.size)
        val slice = if (from < all.size) all.subList(from, to) else emptyList()

        var slotIdx = slotRange.first
        for (entry in slice) {
            gui.set(slotIdx, render(entry))
            slotIdx++
        }
        // Clear remaining slots in the range (when the last page is partial).
        while (slotIdx <= slotRange.last) {
            gui.set(slotIdx, null)
            slotIdx++
        }
    }
}
