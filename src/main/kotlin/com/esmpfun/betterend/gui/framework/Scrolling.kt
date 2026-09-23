package com.esmpfun.betterend.gui.framework

/**
 * Direction a [Scrolling] strategy advances.
 *
 * - `VERTICAL` - each step moves the viewport down by one row.
 * - `HORIZONTAL` - each step moves the viewport right by one column.
 *   Useful for tabbed horizontal browsing of wide item collections.
 */
enum class ScrollOrientation { VERTICAL, HORIZONTAL }

/**
 * Scrolling viewport strategy. Renders a rectangular viewport of items
 * from a 2D-ish source, scrolling one row or column at a time. Useful
 * when the data set is large but the GUI shape is constrained - think
 * "all available mob types" or "list of every server-side trigger
 * across 8 worlds."
 *
 * Composable utility - instantiate on a [VcGui] subclass and call
 * [applyTo] in [VcGui.render].
 *
 * ```
 * private val scroll = Scrolling(
 *     source = { allTriggers() },
 *     render = { trigger -> VcGuiItem.of(Material.LEVER, ...) },
 *     viewportRows = 4,
 *     viewportCols = 7,
 *     anchorSlot = 10,   // top-left corner of viewport
 *     orientation = ScrollOrientation.VERTICAL,
 * )
 *
 * private fun upButton() = VcGuiItem.of(...) { scroll.scrollUp(this); update() }
 * private fun downButton() = VcGuiItem.of(...) { scroll.scrollDown(this); update() }
 * ```
 *
 * @property viewportRows Number of rows visible at once.
 * @property viewportCols Number of columns visible at once.
 * @property anchorSlot Top-left slot of the viewport in the GUI.
 * @property orientation [ScrollOrientation.VERTICAL] (default) or HORIZONTAL.
 */
class Scrolling<T>(
    val source: () -> List<T>,
    val render: (T) -> VcGuiItem,
    val viewportRows: Int,
    val viewportCols: Int,
    val anchorSlot: Int,
    val orientation: ScrollOrientation = ScrollOrientation.VERTICAL,
) {
    private val cols: Int = viewportCols
    private val rows: Int = viewportRows
    private val anchorRow: Int = anchorSlot / 9
    private val anchorCol: Int = anchorSlot % 9

    var offset: Int = 0
        private set

    fun isAtStart(): Boolean = offset == 0
    fun isAtEnd(): Boolean {
        val total = source().size
        val pageWindow = rows * cols
        return offset >= (total - pageWindow).coerceAtLeast(0)
    }

    fun scrollUp() { offset = (offset - cols).coerceAtLeast(0) }
    fun scrollDown() {
        val total = source().size
        val maxOffset = (total - rows * cols).coerceAtLeast(0)
        offset = (offset + cols).coerceAtMost(maxOffset)
    }
    fun scrollLeft() { offset = (offset - rows).coerceAtLeast(0) }
    fun scrollRight() {
        val total = source().size
        val maxOffset = (total - rows * cols).coerceAtLeast(0)
        offset = (offset + rows).coerceAtMost(maxOffset)
    }

    /** Fill the viewport with `rows x cols` entries starting at [offset]. */
    fun applyTo(gui: VcGui) {
        val all = source()
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val slot = (anchorRow + row) * 9 + (anchorCol + col)
                val sourceIndex = when (orientation) {
                    ScrollOrientation.VERTICAL -> offset + row * cols + col
                    ScrollOrientation.HORIZONTAL -> offset + col * rows + row
                }
                val entry = all.getOrNull(sourceIndex)
                gui.set(slot, entry?.let { render(it) })
            }
        }
    }
}
