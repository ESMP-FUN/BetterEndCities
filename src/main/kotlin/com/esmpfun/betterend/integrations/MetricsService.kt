package com.esmpfun.betterend.integrations

import com.esmpfun.betterend.BetterEnd
import dev.faststats.Attributes
import dev.faststats.ErrorTracker
import dev.faststats.Metrics
import dev.faststats.bukkit.BukkitContext
import dev.faststats.data.Metric

/**
 * FastStats integration. Anonymous usage metrics that drive feature
 * prioritization: which database backend servers actually run, elytra
 * claim-mode choice, per-player loot adoption, and fleet city counts.
 *
 * Respect knobs (either disables collection entirely):
 *  - BetterEnd's own `metrics.enabled` in config.yml
 *  - FastStats' global opt-out (`plugins/faststats/config.properties`)
 *
 * The first server start is deliberately silent: FastStats writes its
 * config and submits nothing until the next restart, giving admins a
 * window to set `enabled=false` before any data leaves the box.
 *
 * All metric callables are evaluated by FastStats on its own submission
 * schedule; every supplier below reads cheap in-memory state only.
 *
 * **Error reporting.** On by default (`metrics.error-reporting`). Two paths:
 * `contextAware(classLoader)` auto-captures throwables that escape uncaught
 * and belong to this plugin, and [reportHandled] sends one we caught and
 * recovered from. The second matters most: a discovery pass or a snapshot
 * restore that quietly failed never becomes a ticket otherwise. See
 * [buildErrorTracker] for scope and redaction.
 */
object MetricsService {

    /**
     * FastStats project token for BetterEnd. A blank value disables
     * metrics init entirely.
     */
    private const val PROJECT_TOKEN: String = "0f6a0acd2f476c81fd3241d567a8c546"

    @Volatile
    private var context: BukkitContext? = null

    @Volatile
    private var errorTracker: ErrorTracker? = null

    fun init(plugin: BetterEnd): String {
        if (PROJECT_TOKEN.isBlank()) return "Disabled (no project token)"
        if (!plugin.config.getBoolean("metrics.enabled", true)) return "Disabled (config)"
        // A second context would mean a second submission scheduler.
        if (context != null) return "Enabled"

        val tracker = if (plugin.config.getBoolean("metrics.error-reporting", true)) {
            buildErrorTracker(plugin)
        } else null

        return try {
            val ctx = BukkitContext.Factory(plugin, PROJECT_TOKEN)
                .also { f -> tracker?.let { f.errorTrackerService(it) } }
                .metrics { factory -> registerMetrics(plugin, factory) }
                .create()

            // ready() must run on the main thread during enable; on Paper it also
            // installs the server exception handlers. The caller is already inside
            // scheduler.runTask, so we're on the right thread.
            ctx.ready()
            context = ctx
            errorTracker = tracker
            "Enabled"
        } catch (e: Exception) {
            plugin.logger.warning("FastStats init failed: ${e.message}")
            "Failed"
        }
    }

    private fun registerMetrics(plugin: BetterEnd, factory: Metrics.Factory): Metrics =
        factory
            .addMetric(Metric.string("database_type") {
                plugin.databaseManager.databaseType.toString().lowercase()
            })
            .addMetric(Metric.string("elytra_claim_mode") {
                plugin.config.getString("elytra.claim-mode", "per-ship")
            })
            .addMetric(Metric.bool("per_player_loot") {
                plugin.config.getBoolean("loot.enabled", true)
            })
            .addMetric(Metric.string("city_count") {
                cityCountBucket(plugin.cityManager.all().size)
            })
            .create()

    /**
     * Reports an exception we caught and recovered from. No-op when error
     * reporting is off. `operation` is a short fixed label (`city-discovery`,
     * `snapshot-restore`, ...). `context` values land on the report as-is, so
     * keep them to counts, buckets and fixed enums, never names or coordinates.
     */
    fun reportHandled(t: Throwable, operation: String, vararg context: Pair<String, Any?>) {
        val tracker = errorTracker ?: return
        if (t is java.util.concurrent.CancellationException) return
        runCatching {
            val attrs = Attributes.empty().put("operation", operation)
            for ((k, v) in context) {
                when (v) {
                    null -> {}
                    is Number -> attrs.put(k, v)
                    is Boolean -> attrs.put(k, v)
                    else -> attrs.put(k, v.toString())
                }
            }
            tracker.trackError(t).attributes(attrs).handled(true)
        }
    }

    /**
     * Builds the error tracker: scoped to this plugin, cancellation noise
     * filtered out, extra redaction layered on the SDK's built-ins.
     *
     * **Scope.** `contextAware(loader)` auto-captures uncaught throwables whose
     * class loader matches ours. Passing the loader explicitly rather than using
     * the no-arg overload keeps that unambiguous: another plugin's exception is
     * never ours to report.
     *
     * **Cancellation.** Shutdown cancels `pluginScope`, so cancellation is normal
     * control flow. The SDK matches ignored types by exact class rather than
     * `isAssignableFrom`, so registering `CancellationException` alone would miss
     * kotlinx's `JobCancellationException`; hence the message pattern too.
     *
     * **Redaction.** The SDK already strips IP addresses, home-directory paths
     * (which covers our `jdbc:sqlite:<abs path>` URL), the OS username, and
     * `user:pass@host` JDBC credentials. Added here: query-string credentials, in
     * case a driver echoes connection properties back in a message, and player
     * UUIDs, so "no player data is collected" stays literally true even when a
     * stack trace happens to carry one.
     *
     * **Context.** Plugin and Minecraft version, database type, Folia, and a
     * city-count band ride on every report. All fixed for the server's lifetime,
     * none carry player data, and together they let a fix target the setup a
     * crash actually came from.
     */
    private fun buildErrorTracker(plugin: BetterEnd): ErrorTracker {
        val tracker = ErrorTracker.contextAware(MetricsService::class.java.classLoader)
            .ignoreError(java.util.concurrent.CancellationException::class.java)
            .ignoreError("(?i).*\\b(?:job|coroutine)\\b.*\\bcancell?ed\\b.*")
            .anonymize("(?i)([?&;](?:user|username|password|pass|pwd)=)[^&;\\s\"']*", "$1[hidden]")
            .anonymize(
                "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b",
                "[uuid hidden]"
            )

        runCatching {
            tracker.attributes
                .put("be_version", plugin.pluginMeta.version)
                .put("mc_version", plugin.server.minecraftVersion)
                .put("database", plugin.databaseManager.databaseType.toString().lowercase())
                .put("folia", plugin.scheduler.isFolia)
                .put("cities", cityCountBucket(plugin.cityManager.all().size))
        }
        return tracker
    }

    /** Coarse fleet band. Shared by the metric and the error context. */
    private fun cityCountBucket(n: Int): String = when (n) {
        0 -> "0"
        in 1..5 -> "1-5"
        in 6..20 -> "6-20"
        in 21..50 -> "21-50"
        in 51..100 -> "51-100"
        else -> "100+"
    }

    /** Flushes and stops the submission scheduler. Safe to call when init never ran. */
    fun shutdown() {
        val ctx = context ?: return
        context = null
        errorTracker = null
        runCatching { ctx.shutdown() }
    }
}
