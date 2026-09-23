package com.esmpfun.betterend.scheduler

import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin

/**
 * One scheduling API for Paper and Folia. On Paper everything runs on the main
 * thread; on Folia work that touches blocks or entities must run on the region
 * that owns them, so use [runAtLocation] and [runAtEntity] for that.
 */
interface SchedulerAdapter {

    val isFolia: Boolean

    /** Main thread on Paper, global region on Folia. */
    fun runTask(task: Runnable)

    fun runTaskAsync(task: Runnable)

    /** Delay in ticks (20 ticks = 1 second). */
    fun runTaskLater(task: Runnable, delayTicks: Long): ScheduledTask

    fun runTaskLaterAsync(task: Runnable, delayTicks: Long): ScheduledTask

    fun runTaskTimer(task: Runnable, delayTicks: Long, periodTicks: Long): ScheduledTask

    fun runTaskTimerAsync(task: Runnable, delayTicks: Long, periodTicks: Long): ScheduledTask

    /** Runs on the region that owns [location]. Use for block reads and writes. */
    fun runAtLocation(location: Location, task: Runnable)

    fun runAtLocationLater(location: Location, task: Runnable, delayTicks: Long)

    /** Runs on the region that owns [entity]; [retired] runs instead if it's gone by then. */
    fun runAtEntity(entity: Entity, task: Runnable, retired: Runnable? = null)

    fun runAtEntityLater(entity: Entity, task: Runnable, delayTicks: Long, retired: Runnable? = null)

    fun cancelAllTasks()

    companion object {
        fun create(plugin: Plugin): SchedulerAdapter =
            if (isFoliaServer()) FoliaSchedulerAdapter(plugin) else BukkitSchedulerAdapter(plugin)

        private fun isFoliaServer(): Boolean = try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }
}

interface ScheduledTask {
    fun cancel()
    val isCancelled: Boolean
}
