package com.esmpfun.betterend.listeners

import com.esmpfun.betterend.BetterEnd
import com.esmpfun.betterend.managers.ContainerLootManager
import com.esmpfun.betterend.models.EndCity
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.block.BlockState
import org.bukkit.block.Chest
import org.bukkit.block.Container
import org.bukkit.block.DoubleChest
import org.bukkit.block.TileState
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryMoveItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.BlockInventoryHolder
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.loot.LootContext
import org.bukkit.loot.LootTable
import org.bukkit.loot.LootTables
import org.bukkit.loot.Lootable
import org.bukkit.persistence.PersistentDataType
import kotlin.coroutines.resume
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-player End City container loot (Lootr-style), the BetterAncientCities
 * approach ported to the End.
 *
 * Every player who opens a city chest sees their own private copy of its
 * contents, so the second player into a city doesn't find gutted containers.
 * Cooldown semantics are content *freshness*, not lockout: after a reset,
 * [ContainerLootManager.clearCity] drops everyone's copies and the next open
 * re-clones the template.
 *
 * Eligibility: a container is city loot only if it's inside a registered city
 * AND inside an actual generated structure piece ([EndCity.inStructurePiece]).
 * The piece test rejects player-built chests that happen to sit within the
 * city's envelope but outside the structure.
 *
 * How it works:
 *  - Each container has a shared **template** - the canonical contents every
 *    first-open copy is cloned from, materialized once by rolling the block's
 *    vanilla loot table (a naturally-generated city chest holds an UNROLLED
 *    loot table / empty inventory until first opened). Persists across resets.
 *  - Ops with `betterend.admin` **sneak-open** the template to edit it; a
 *    normal click gets a per-player copy.
 *  - Double chests are keyed by the left half (one 54-slot template/copy).
 *  - Player-placed containers are PDC-tagged at place time and keep vanilla
 *    behaviour. So does any untagged container that holds items but no loot
 *    table (see [isCityLoot]).
 *  - Hopper movement in/out of an eligible container is cancelled.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ContainerLootListener(private val plugin: BetterEnd) : Listener {

    private val playerPlacedKey = NamespacedKey("betterend", "player_placed_container")

    /** Anti double-fire: last virtual-open millis per player. */
    private val openDebounce = ConcurrentHashMap<UUID, Long>()

    /** Marks a player's private copy inventory (saved to player_container_loot). */
    class CopyHolder(
        val cityId: Int,
        val pos: ContainerLootManager.ContainerPos
    ) : InventoryHolder {
        lateinit var backing: Inventory
        override fun getInventory(): Inventory = backing
    }

    /** Marks an op editing the shared template (saved to container_template). */
    class TemplateHolder(
        val cityId: Int,
        val pos: ContainerLootManager.ContainerPos
    ) : InventoryHolder {
        lateinit var backing: Inventory
        override fun getInventory(): Inventory = backing
    }

    private companion object {
        val ELIGIBLE = setOf(
            Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL,
            Material.DISPENSER, Material.DROPPER
        )
        val COPY_TITLE: Component = Component.text("End City Loot")
        val TEMPLATE_TITLE: Component = Component.text("Loot Template (shared)")
    }

    private fun enabled() = plugin.config.getBoolean("loot.enabled", true)

    /** Refresh window in ms (`loot.refresh-hours`); <= 0 disables refresh. */
    private fun refreshMs(): Long = (plugin.config.getInt("loot.refresh-hours", 12) * 3_600_000L)

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onContainerOpen(event: PlayerInteractEvent) {
        if (!enabled() || !plugin.isReady) return
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        if (event.hand != org.bukkit.inventory.EquipmentSlot.HAND) return
        val block = event.clickedBlock ?: return
        if (block.type !in ELIGIBLE) return

        val city = plugin.cityManager.getCachedCityAt(block.location) ?: return
        // Provenance: only actual structure-piece containers are city loot.
        if (!city.inStructurePiece(block.location)) return

        // Player-placed containers keep vanilla behaviour.
        val state = block.state
        if (state is TileState &&
            state.persistentDataContainer.has(playerPlacedKey, PersistentDataType.BYTE)
        ) return

        val container = state as? Container ?: return
        val inv = container.inventory
        val holder = inv.holder
        if (!isCityLoot(state, holder, inv)) return
        val player = event.player

        // Sneak + admin = edit the shared template; otherwise a per-player copy.
        val isAdminEdit = player.isSneaking && player.hasPermission("betterend.admin")

        event.isCancelled = true

        val now = System.currentTimeMillis()
        val last = openDebounce[player.uniqueId]
        if (last != null && now - last < 700) return
        openDebounce[player.uniqueId] = now
        if (openDebounce.size > 100) openDebounce.entries.removeIf { now - it.value > 10_000 }

        // Resolve the normalized key block (left half of a double chest) and the
        // inventory size SYNCHRONOUSLY, while we're on the block's region thread.
        val keyBlock = if (holder is DoubleChest) (holder.leftSide as? Chest)?.block ?: block else block
        val size = if (holder is DoubleChest) 54 else inv.size
        val pos = ContainerLootManager.ContainerPos(keyBlock.x, keyBlock.y, keyBlock.z)
        val keyLoc = keyBlock.location
        val keyMaterial = keyBlock.type

        plugin.launchAsync {
            var template = plugin.containerLootManager.loadTemplate(city.id, pos)
            if (template == null) {
                template = materializeOnRegion(keyLoc, size)
                plugin.containerLootManager.saveTemplate(city.id, pos, template, keyMaterial)
            }

            if (isAdminEdit) {
                openVirtual(player, TemplateHolder(city.id, pos), size, TEMPLATE_TITLE, template)
                player.sendMessage(Component.text("§7Editing the shared loot template. Changes apply to every player's first open."))
            } else {
                // First looter past the refresh window starts a new per-city cycle:
                // wipe everyone's copies so the city's loot is fresh again. Editing
                // the template (above) never triggers a cycle. Lazy - no scheduler.
                if (plugin.cityManager.beginCycleIfDue(city.id, refreshMs())) {
                    plugin.containerLootManager.clearCity(city.id)
                    plugin.cityManager.persistCycleStart(city.id)
                    // Optionally restore the structure too (revert griefing) on
                    // the cycle roll. Opt-in - a restore rewrites blocks.
                    if (plugin.config.getBoolean("snapshot.auto-reset-on-refresh", false) &&
                        plugin.snapshotManager.hasSnapshot(city.id)
                    ) {
                        plugin.snapshotManager.restore(city)
                    }
                }
                val existing = plugin.containerLootManager.loadContents(city.id, pos, player.uniqueId)
                openVirtual(player, CopyHolder(city.id, pos), size, COPY_TITLE, existing ?: template)
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onContainerClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        when (val holder = event.inventory.getHolder(false)) {
            is CopyHolder -> plugin.containerLootManager.queueSaveContents(
                holder.cityId, holder.pos, player.uniqueId, snapshot(event.inventory))
            is TemplateHolder -> plugin.containerLootManager.queueUpdateTemplate(
                holder.cityId, holder.pos, snapshot(event.inventory))
            else -> return
        }
    }

    private fun snapshot(inv: Inventory): Array<ItemStack?> = inv.contents.map { it?.clone() }.toTypedArray()

    /**
     * Saves and closes every open loot inventory, then writes out anything
     * still waiting. Called from onDisable while listeners are still
     * registered: without it, loot taken from a copy that was open at
     * shutdown or reload stays in the player's inventory while the copy
     * itself reverts to full on the next start.
     */
    fun saveOpenOnShutdown() {
        for (player in plugin.server.onlinePlayers) {
            runCatching {
                val top = player.openInventory.topInventory
                when (val holder = top.getHolder(false)) {
                    is CopyHolder -> plugin.containerLootManager.queueSaveContents(
                        holder.cityId, holder.pos, player.uniqueId, snapshot(top))
                    is TemplateHolder -> plugin.containerLootManager.queueUpdateTemplate(
                        holder.cityId, holder.pos, snapshot(top))
                    else -> return@runCatching
                }
                // Folia can't touch a player's screen from here; the snapshot above still counts.
                if (!plugin.scheduler.isFolia) player.closeInventory()
            }
        }
        plugin.containerLootManager.flushPending()
    }

    /**
     * Tag containers players place so they keep vanilla behaviour. Every End
     * placement is tagged, not just those inside a known city, so a chest
     * placed before its city finishes registering is covered too.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onContainerPlace(event: BlockPlaceEvent) {
        if (event.block.type !in ELIGIBLE) return
        if (event.block.world.environment != org.bukkit.World.Environment.THE_END) return
        val state = event.block.state as? TileState ?: return
        state.persistentDataContainer.set(playerPlacedKey, PersistentDataType.BYTE, 1)
        state.update()
    }

    /** Block hopper automation against the pristine template. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onHopperMove(event: InventoryMoveItemEvent) {
        if (!enabled()) return
        if (isProtectedTemplate(event.source) || isProtectedTemplate(event.destination)) {
            event.isCancelled = true
        }
    }

    // ==== Helpers ====

    private suspend fun materializeOnRegion(keyLoc: Location, size: Int): Array<ItemStack?> =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            plugin.scheduler.runAtLocation(keyLoc, Runnable {
                val result = try {
                    materializeTemplate(keyLoc.block, size)
                } catch (e: Exception) {
                    plugin.logger.warning("[ContainerLoot] Template materialize failed at ${keyLoc.blockX},${keyLoc.blockY},${keyLoc.blockZ}: ${e.message}")
                    arrayOfNulls<ItemStack?>(size)
                }
                cont.resume(result)
            })
        }

    private fun materializeTemplate(block: Block, size: Int): Array<ItemStack?> {
        val container = block.state as? Container ?: return arrayOfNulls(size)
        val holder = container.inventory.holder
        return if (holder is DoubleChest) {
            val out = arrayOfNulls<ItemStack?>(54)
            rollHalf(holder.leftSide as? Chest, out, 0)
            rollHalf(holder.rightSide as? Chest, out, 27)
            out
        } else {
            rollSingle(block.state, container.inventory, size)
        }
    }

    private fun rollSingle(state: BlockState, inv: Inventory, size: Int): Array<ItemStack?> {
        val table = tableFor(state as? Lootable, state.type)
        val source: Array<ItemStack?> = if (table != null) {
            val temp = Bukkit.createInventory(null, size)
            table.fillInventory(temp, java.util.Random(), LootContext.Builder(state.block.location).build())
            temp.contents
        } else {
            inv.contents
        }
        return Array(size) { source.getOrNull(it)?.clone() }
    }

    private fun rollHalf(chest: Chest?, out: Array<ItemStack?>, offset: Int) {
        chest ?: return
        val table = tableFor(chest, chest.type)
        val half: Array<ItemStack?> = if (table != null) {
            val temp = Bukkit.createInventory(null, 27)
            table.fillInventory(temp, java.util.Random(), LootContext.Builder(chest.block.location).build())
            temp.contents
        } else {
            chest.blockInventory.contents
        }
        for (i in 0 until 27) out[offset + i] = half.getOrNull(i)?.clone()
    }

    /**
     * The container's own unrolled loot table. A chest without one was looted
     * before this plugin ran ([isCityLoot] only lets empty ones through), so it
     * gets fresh End City loot instead of a permanently empty copy.
     */
    private fun tableFor(lootable: Lootable?, type: Material): LootTable? =
        lootable?.lootTable ?: if (type == Material.CHEST || type == Material.TRAPPED_CHEST) LootTables.END_CITY_TREASURE.lootTable else null

    /**
     * Whether an untagged container in a structure piece is the city's own:
     * its loot is still unrolled, or it stands empty (looted before this
     * plugin ran). One holding items with no loot table was stocked by a
     * player, so it stays an ordinary chest. Treating it as city loot would
     * copy that player's items to everyone.
     */
    private fun isCityLoot(state: BlockState, holder: InventoryHolder?, inv: Inventory): Boolean {
        val tables = if (holder is DoubleChest) {
            listOf((holder.leftSide as? Chest)?.lootTable, (holder.rightSide as? Chest)?.lootTable)
        } else {
            listOf((state as? Lootable)?.lootTable)
        }
        return tables.any { it != null } || inv.isEmpty
    }

    private suspend fun openVirtual(
        player: Player,
        holder: InventoryHolder,
        size: Int,
        title: Component,
        contents: Array<ItemStack?>
    ) = kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
        // The retired callback resumes too, or a player logging out mid-open would strand this coroutine.
        plugin.scheduler.runAtEntity(player, retired = Runnable { cont.resume(Unit) }, task = Runnable {
            try {
                if (player.isOnline) {
                    val virtual = plugin.server.createInventory(holder, size, title)
                    when (holder) {
                        is CopyHolder -> holder.backing = virtual
                        is TemplateHolder -> holder.backing = virtual
                    }
                    for (i in 0 until minOf(size, contents.size)) {
                        virtual.setItem(i, contents[i])
                    }
                    player.openInventory(virtual)
                }
            } catch (e: Exception) {
                plugin.logger.warning("[ContainerLoot] Failed to open container for ${player.name}: ${e.message}")
            }
            cont.resume(Unit)
        })
    }

    /**
     * Scans every chunk in [city]'s region for eligible structure-piece
     * containers and materializes a shared template for any without one. Lets
     * admins prep all city loot from a command without opening each container
     * in-world. Returns the number of new templates created. Folia-safe
     * (region-hops per chunk).
     */
    suspend fun materializeCity(city: EndCity): Int {
        val world = city.getWorld() ?: return 0
        var created = 0
        val minCX = city.region.minX shr 4; val maxCX = city.region.maxX shr 4
        val minCZ = city.region.minZ shr 4; val maxCZ = city.region.maxZ shr 4
        for (cx in minCX..maxCX) {
            for (cz in minCZ..maxCZ) {
                val rep = Location(world, (cx shl 4).toDouble(), city.region.minY.toDouble(), (cz shl 4).toDouble())
                val rolled = kotlinx.coroutines.suspendCancellableCoroutine<List<Triple<ContainerLootManager.ContainerPos, Array<ItemStack?>, Material>>> { cont ->
                    plugin.scheduler.runAtLocation(rep, Runnable {
                        val results = mutableListOf<Triple<ContainerLootManager.ContainerPos, Array<ItemStack?>, Material>>()
                        try {
                            if (!world.isChunkLoaded(cx, cz)) world.getChunkAt(cx, cz)
                            for (te in world.getChunkAt(cx, cz).tileEntities) {
                                if (te !is Container) continue
                                val b = te.block
                                if (b.type !in ELIGIBLE) continue
                                if (!city.inStructurePiece(b.location)) continue
                                if ((b.state as? TileState)?.persistentDataContainer
                                        ?.has(playerPlacedKey, PersistentDataType.BYTE) == true) continue
                                val holder = te.inventory.holder
                                if (!isCityLoot(te, holder, te.inventory)) continue
                                val keyBlock = if (holder is DoubleChest) (holder.leftSide as? Chest)?.block ?: b else b
                                if (holder is DoubleChest && keyBlock != b) continue // right half - handled by left
                                val size = if (holder is DoubleChest) 54 else te.inventory.size
                                results.add(
                                    Triple(
                                        ContainerLootManager.ContainerPos(keyBlock.x, keyBlock.y, keyBlock.z),
                                        materializeTemplate(keyBlock, size),
                                        keyBlock.type
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            plugin.logger.warning("[ContainerLoot] City scan failed in chunk $cx,$cz: ${e.message}")
                        }
                        cont.resume(results)
                    })
                }
                for ((pos, contents, material) in rolled) {
                    if (!plugin.containerLootManager.hasTemplate(city.id, pos)) {
                        plugin.containerLootManager.saveTemplate(city.id, pos, contents, material)
                        created++
                    }
                }
            }
        }
        return created
    }

    private fun isProtectedTemplate(inv: Inventory): Boolean {
        // Hoppers fire this constantly server-wide: reject by position before
        // building any block state.
        val loc = inv.location ?: return false
        if (plugin.cityManager.getCachedCityAt(loc) == null) return false
        val holder = inv.getHolder(false)
        val block: Block = when (holder) {
            is DoubleChest -> (holder.leftSide as? Chest)?.block ?: return false
            is BlockInventoryHolder -> holder.block
            else -> return false
        }
        if (block.type !in ELIGIBLE) return false
        val city = plugin.cityManager.getCachedCityAt(block.location) ?: return false
        if (!city.inStructurePiece(block.location)) return false
        val state = block.state as? TileState ?: return false
        if (state.persistentDataContainer.has(playerPlacedKey, PersistentDataType.BYTE)) return false
        return isCityLoot(state, holder, inv)
    }
}
