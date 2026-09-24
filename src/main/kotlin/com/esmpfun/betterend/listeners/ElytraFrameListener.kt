package com.esmpfun.betterend.listeners

import com.esmpfun.betterend.BetterEnd
import com.esmpfun.betterend.utils.AntiDupeCompat
import com.esmpfun.betterend.utils.StructureUtil
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.entity.Display
import org.bukkit.entity.ItemFrame
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.hanging.HangingBreakEvent
import org.bukkit.event.hanging.HangingPlaceEvent
import org.bukkit.event.world.EntitiesLoadEvent
import org.bukkit.generator.structure.Structure
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/**
 * Punching the ship's elytra frame hands out a new elytra; the frame keeps its
 * own. An elytra frame inside an End City is tagged as a ship frame the first
 * time it's seen; frames players hang are tagged at placement and never are.
 */
class ElytraFrameListener(private val plugin: BetterEnd) : Listener {

    companion object {
        private fun frameTag(plugin: BetterEnd) = NamespacedKey(plugin, "ship_elytra_frame")
        private fun playerPlacedTag(plugin: BetterEnd) = NamespacedKey(plugin, "player_placed_frame")
        private fun displayTag(plugin: BetterEnd) = NamespacedKey(plugin, "elytra_frame_text")

        private fun hintText(plugin: BetterEnd): Component {
            val cost = plugin.elytraClaimManager.costStack()
            val levels = plugin.config.getInt("elytra.cost.levels", 0)
            val price = priceText(plugin, levels, cost, cost?.amount ?: 0)
                ?: return plugin.messages.get("elytra.hint-free")
            return plugin.messages.get("elytra.hint-cost", "price" to price)
        }

        // "20 levels and 10 x Netherite Scrap", or null when it's free.
        private fun priceText(plugin: BetterEnd, levels: Int, item: ItemStack?, items: Int): Component? {
            val m = plugin.messages
            val parts = buildList {
                if (levels > 0) add(m.get("elytra.price-levels", "levels" to levels))
                if (item != null && items > 0) add(m.get("elytra.price-items", "amount" to items, "item" to item.effectiveName()))
            }
            if (parts.isEmpty()) return null
            return parts.reduce { a, b -> a.append(m.get("elytra.price-and")).append(b) }
        }

        /** Updates hints above loaded ship frames after a settings change; unloaded ones update on load. */
        fun refreshLoaded(plugin: BetterEnd) {
            val fTag = frameTag(plugin)
            val dTag = displayTag(plugin)
            val wantHint = plugin.config.getBoolean("elytra.enabled", true) &&
                plugin.config.getBoolean("elytra.text-display", true)
            if (plugin.scheduler.isFolia) {
                // No Folia thread may scan a whole world, so each frame updates on its own region.
                for (frame in loadedShipFrames) {
                    plugin.scheduler.runAtEntity(frame, Runnable {
                        val hints = frame.world.getNearbyEntitiesByType(TextDisplay::class.java, hintLocation(frame), 1.5) {
                            it.persistentDataContainer.has(dTag, PersistentDataType.BYTE)
                        }
                        when {
                            !wantHint -> hints.forEach { it.remove() }
                            hints.isEmpty() -> spawnHintIfMissing(plugin, frame)
                            else -> hints.forEach { it.text(hintText(plugin)) }
                        }
                    })
                }
                return
            }
            for (world in plugin.server.worlds) {
                if (world.environment != World.Environment.THE_END) continue
                for (display in world.getEntitiesByClass(TextDisplay::class.java)) {
                    if (!display.persistentDataContainer.has(dTag, PersistentDataType.BYTE)) continue
                    if (wantHint) display.text(hintText(plugin)) else display.remove()
                }
                if (!wantHint) continue
                for (frame in world.getEntitiesByClass(ItemFrame::class.java)) {
                    if (!frame.persistentDataContainer.has(fTag, PersistentDataType.BYTE)) continue
                    spawnHintIfMissing(plugin, frame)
                }
            }
        }

        private fun spawnHintIfMissing(plugin: BetterEnd, frame: ItemFrame) {
            val dTag = displayTag(plugin)
            val loc = hintLocation(frame)
            val world = frame.world
            val existing = world.getNearbyEntitiesByType(TextDisplay::class.java, loc, 1.5) {
                it.persistentDataContainer.has(dTag, PersistentDataType.BYTE)
            }
            if (existing.isNotEmpty()) return
            world.spawn(loc, TextDisplay::class.java) { display ->
                display.text(hintText(plugin))
                display.billboard = Display.Billboard.CENTER
                display.isSeeThrough = false
                display.persistentDataContainer.set(dTag, PersistentDataType.BYTE, 1)
            }
        }

        private fun hintLocation(frame: ItemFrame): Location =
            frame.location.clone().add(0.0, 0.9, 0.0)

        private val loadedShipFrames: MutableSet<ItemFrame> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    }

    private val frameTag = frameTag(plugin)
    private val playerPlacedTag = playerPlacedTag(plugin)

    private fun enabled() = plugin.config.getBoolean("elytra.enabled", true)
    private fun textDisplay() = plugin.config.getBoolean("elytra.text-display", true)

    // ── identification ───────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    fun onEntitiesLoad(event: EntitiesLoadEvent) {
        if (!enabled()) return
        if (event.world.environment != World.Environment.THE_END) return
        for (entity in event.entities) {
            if (entity is TextDisplay &&
                entity.persistentDataContainer.has(displayTag(plugin), PersistentDataType.BYTE)
            ) {
                if (textDisplay()) entity.text(hintText(plugin)) else entity.remove()
                continue
            }
            val frame = entity as? ItemFrame ?: continue
            if (!isShipFrame(frame)) continue
            loadedShipFrames.add(frame)
            rememberShip(frame)
            if (textDisplay()) spawnHintIfMissing(plugin, frame)
        }
    }

    // ── aura ─────────────────────────────────────────────────────────────────

    init {
        // Frames already loaded before the plugin started never fire EntitiesLoadEvent.
        if (!plugin.scheduler.isFolia) {
            for (world in plugin.server.worlds) {
                if (world.environment != World.Environment.THE_END) continue
                world.getEntitiesByClass(ItemFrame::class.java)
                    .filterTo(loadedShipFrames) { it.persistentDataContainer.has(frameTag, PersistentDataType.BYTE) }
            }
        }
        plugin.scheduler.runTaskTimer(Runnable { auraTick() }, 40L, 10L)
    }

    private fun auraTick() {
        // Pruned even with the aura off: every chunk reload adds a fresh frame object.
        loadedShipFrames.removeIf { !it.isValid }
        if (!enabled() || !plugin.config.getBoolean("elytra.frame-aura", false)) return
        for (frame in loadedShipFrames) {
            plugin.scheduler.runAtEntity(frame, Runnable {
                val loc = frame.location.toCenterLocation()
                val world = frame.world
                if (world.getNearbyPlayers(loc, 24.0).isEmpty()) return@Runnable
                val random = java.util.concurrent.ThreadLocalRandom.current()
                world.spawnParticle(org.bukkit.Particle.REVERSE_PORTAL, loc, 6, 0.35, 0.35, 0.35, 0.02)
                if (random.nextInt(100) < 35) {
                    world.spawnParticle(org.bukkit.Particle.SOUL_FIRE_FLAME, loc, 2, 0.25, 0.25, 0.25, 0.005)
                }
                if (random.nextInt(100) < 4) {
                    world.playSound(loc, Sound.AMBIENT_SOUL_SAND_VALLEY_ADDITIONS, 0.5f, 0.55f)
                }
            })
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onHangingPlace(event: HangingPlaceEvent) {
        val frame = event.entity as? ItemFrame ?: return
        if (frame.world.environment != World.Environment.THE_END) return
        frame.persistentDataContainer.set(playerPlacedTag, PersistentDataType.BYTE, 1)
    }

    /** Tag check first; the structure test runs once and stamps the tag. */
    private fun isShipFrame(frame: ItemFrame): Boolean {
        if (frame.persistentDataContainer.has(frameTag, PersistentDataType.BYTE)) return true
        if (frame.persistentDataContainer.has(playerPlacedTag, PersistentDataType.BYTE)) return false
        if (frame.item.type != Material.ELYTRA) return false
        if (frame.world.environment != World.Environment.THE_END) return false
        if (!StructureUtil.isNear(frame.location, Structure.END_CITY, 8.0)) return false
        frame.persistentDataContainer.set(frameTag, PersistentDataType.BYTE, 1)
        // Also marks the ship when snapshot.auto-capture is off and no dragon head was seen.
        plugin.cityManager.getCachedCityAt(frame.location)?.takeIf { !it.hasShip }?.let { city ->
            plugin.launchAsync { plugin.cityManager.setHasShip(city.id, true) }
        }
        return true
    }

    // ── claiming ─────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onFrameChange(event: PlayerItemFrameChangeEvent) {
        if (!enabled() || !plugin.isReady) return
        val frame = event.itemFrame
        if (!isShipFrame(frame)) return

        // The frame's elytra never leaves. A right-click shows the price instead of turning it.
        event.isCancelled = true
        val player = event.player

        val city = plugin.cityManager.getCachedCityAt(frame.location)
        if (city == null) {
            // Only possible in the moment between the city generating and registering.
            player.sendActionBar(plugin.messages.get("elytra.still-registering"))
            return
        }
        rememberShip(frame)

        if (plugin.elytraClaimManager.hasClaimed(city.id, player.uniqueId)) {
            player.sendActionBar(plugin.messages.get("elytra.already-claimed-${plugin.elytraClaimManager.mode().key}"))
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.7f)
            return
        }

        val price = plugin.elytraClaimManager.priceFor(player.uniqueId)
        if (event.action == PlayerItemFrameChangeEvent.ItemFrameChangeAction.ROTATE) {
            player.sendMessage(priceMessage(price))
            return
        }
        if (event.action != PlayerItemFrameChangeEvent.ItemFrameChangeAction.REMOVE) return

        val item = price.item
        val missingItems = item != null && price.items > 0 && !player.inventory.containsAtLeast(item, price.items)
        if (player.level < price.levels || missingItems) {
            player.sendMessage(priceMessage(price))
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.7f)
            return
        }
        if (price.levels > 0) player.giveExpLevels(-price.levels)
        if (item != null) {
            var left = price.items
            while (left > 0) {
                val take = minOf(left, item.maxStackSize)
                player.inventory.removeItem(item.clone().apply { amount = take })
                left -= take
            }
        }

        val elytra = ItemStack(Material.ELYTRA)
        AntiDupeCompat.tagOwner(elytra, player.uniqueId)
        giveOrDrop(player, elytra)

        plugin.elytraClaimManager.record(city.id, player.uniqueId)
        player.sendActionBar(plugin.messages.get("elytra.claimed"))
        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f)
        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
            plugin.logger.info("[Elytra] ${player.name} claimed at city #${city.id} (${frame.location.blockX},${frame.location.blockY},${frame.location.blockZ})")
        }
    }

    // "This elytra costs 20 levels and 10 x Netherite Scrap", plus the doubling rule.
    private fun priceMessage(price: com.esmpfun.betterend.managers.ElytraClaimManager.Price): Component {
        val m = plugin.messages
        val parts = priceText(plugin, price.levels, price.item, price.items)
            ?: return m.get("elytra.price-free")
        var line = m.get("elytra.price-cost", "price" to parts)
        if (plugin.config.getBoolean("elytra.cost.double-each-claim", false)) {
            val hours = plugin.config.getInt("loot.refresh-hours", 12)
            val span = when {
                hours <= 0 -> null
                hours % 24 == 0 -> (hours / 24).let { if (it == 1) m.raw("elytra.time-day") else m.raw("elytra.time-days", "count" to it) }
                else -> if (hours == 1) m.raw("elytra.time-hour") else m.raw("elytra.time-hours", "count" to hours)
            }
            line = line.append(if (span == null) m.get("elytra.doubling") else m.get("elytra.doubling-until", "time" to span))
        }
        return line
    }

    // Lets protection pick the ship out of the city's pieces.
    private fun rememberShip(frame: ItemFrame) {
        val city = plugin.cityManager.getCachedCityAt(frame.location) ?: return
        if (city.shipAnchor != null) return
        val b = frame.location.block
        plugin.launchAsync { plugin.cityManager.setShipAnchor(city.id, b.x, b.y, b.z) }
    }

    private fun giveOrDrop(player: Player, item: ItemStack) {
        val leftover = player.inventory.addItem(item)
        for (rest in leftover.values) {
            player.world.dropItemNaturally(player.location, rest)
        }
    }

    // ── frame protection ─────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onHangingBreak(event: HangingBreakEvent) {
        if (!enabled()) return
        val frame = event.entity as? ItemFrame ?: return
        if (isShipFrame(frame)) event.isCancelled = true
    }

    // Arrows and other non-player hits would pop the elytra out; punches go on to onFrameChange.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onFrameDamage(event: EntityDamageByEntityEvent) {
        if (!enabled()) return
        val frame = event.entity as? ItemFrame ?: return
        if (event.damager is Player) return
        if (isShipFrame(frame)) event.isCancelled = true
    }
}
