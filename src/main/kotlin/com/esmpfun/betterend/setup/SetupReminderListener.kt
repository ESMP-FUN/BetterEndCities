package com.esmpfun.betterend.setup

import com.esmpfun.betterend.BetterEnd
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Until the setup tour is finished or skipped, admins get one clickable reminder per session. */
class SetupReminderListener(private val plugin: BetterEnd) : Listener {

    private val remindedThisSession = ConcurrentHashMap.newKeySet<UUID>()

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        if (plugin.config.getBoolean("setup.completed", false)) return
        val player = event.player
        if (!player.hasPermission("betterend.admin")) return
        if (!remindedThisSession.add(player.uniqueId)) return

        plugin.scheduler.runAtEntityLater(player, Runnable {
            if (!player.isOnline || plugin.config.getBoolean("setup.completed", false)) return@Runnable
            player.sendMessage(plugin.messages.get("setup.reminder"))
        }, 60L)
    }
}
