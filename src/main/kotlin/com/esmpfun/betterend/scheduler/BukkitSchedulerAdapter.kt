package com.esmpfun.betterend.scheduler

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask

/** Paper: every "region" is the main thread. */
class BukkitSchedulerAdapter(private val plugin: Plugin) : SchedulerAdapter {

    override val isFolia: Boolean = false

    private val scheduler get() = Bukkit.getScheduler()

    override fun runTask(task: Runnable) {
        if (Bukkit.isPrimaryThread()) {
            task.run()
        } else {
            scheduler.runTask(plugin, task)
        }
    }

    override fun runTaskAsync(task: Runnable) {
        scheduler.runTaskAsynchronously(plugin, task)
    }

    override fun runTaskLater(task: Runnable, delayTicks: Long): ScheduledTask {
        val bukkitTask = scheduler.runTaskLater(plugin, task, delayTicks)
        return BukkitScheduledTask(bukkitTask)
    }

    override fun runTaskLaterAsync(task: Runnable, delayTicks: Long): ScheduledTask {
        val bukkitTask = scheduler.runTaskLaterAsynchronously(plugin, task, delayTicks)
        return BukkitScheduledTask(bukkitTask)
    }

    override fun runTaskTimer(task: Runnable, delayTicks: Long, periodTicks: Long): ScheduledTask {
        val bukkitTask = scheduler.runTaskTimer(plugin, task, delayTicks, periodTicks)
        return BukkitScheduledTask(bukkitTask)
    }

    override fun runTaskTimerAsync(task: Runnable, delayTicks: Long, periodTicks: Long): ScheduledTask {
        val bukkitTask = scheduler.runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks)
        return BukkitScheduledTask(bukkitTask)
    }

    override fun runAtLocation(location: Location, task: Runnable) {
        runTask(task)
    }

    override fun runAtLocationLater(location: Location, task: Runnable, delayTicks: Long) {
        runTaskLater(task, delayTicks)
    }

    override fun runAtEntity(entity: Entity, task: Runnable, retired: Runnable?) {
        runTask {
            if (entity.isValid) {
                task.run()
            } else {
                retired?.run()
            }
        }
    }

    override fun runAtEntityLater(entity: Entity, task: Runnable, delayTicks: Long, retired: Runnable?) {
        runTaskLater({
            if (entity.isValid) {
                task.run()
            } else {
                retired?.run()
            }
        }, delayTicks)
    }

    override fun cancelAllTasks() {
        scheduler.cancelTasks(plugin)
    }

    private class BukkitScheduledTask(private val task: BukkitTask) : ScheduledTask {
        override fun cancel() {
            task.cancel()
        }

        override val isCancelled: Boolean
            get() = task.isCancelled
    }
}
