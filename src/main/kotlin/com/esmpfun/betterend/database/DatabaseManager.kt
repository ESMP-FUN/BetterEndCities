package com.esmpfun.betterend.database

import com.esmpfun.betterend.BetterEnd
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.configuration.file.FileConfiguration
import java.io.File
import java.sql.Connection

/** Pooled SQLite (default) or MySQL access, and the schema. */
class DatabaseManager(private val plugin: BetterEnd) {

    private lateinit var dataSource: HikariDataSource
    private var _databaseType: DatabaseType = DatabaseType.SQLITE

    val databaseType: DatabaseType get() = _databaseType

    enum class DatabaseType { SQLITE, MYSQL }

    /** A pooled connection. Always use inside `.use { }`. */
    val connection: Connection get() = dataSource.connection

    suspend fun initialize() = withContext(Dispatchers.IO) {
        val config = plugin.config
        _databaseType = try {
            DatabaseType.valueOf(config.getString("database.type", "SQLITE")!!.uppercase())
        } catch (_: IllegalArgumentException) {
            plugin.logger.warning("Invalid database.type, defaulting to SQLITE")
            DatabaseType.SQLITE
        }

        dataSource = when (_databaseType) {
            DatabaseType.SQLITE -> createSQLiteDataSource()
            DatabaseType.MYSQL -> createMySQLDataSource(config)
        }
        plugin.logger.info("Database pool initialized (${_databaseType.name})")
        createTables()
    }

    private fun createSQLiteDataSource(): HikariDataSource {
        val dbFile = File(plugin.dataFolder, "database.db")
        plugin.dataFolder.mkdirs()
        return HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}?foreign_keys=on"
            driverClassName = "org.sqlite.JDBC"
            maximumPoolSize = 5
            minimumIdle = 1
            connectionTimeout = 30000
            idleTimeout = 300000
            maxLifetime = 600000
            connectionTestQuery = "SELECT 1"
            poolName = "BetterEndCities-SQLite"
            leakDetectionThreshold = 10000
            addDataSourceProperty("journal_mode", "WAL")
            addDataSourceProperty("synchronous", "NORMAL")
            addDataSourceProperty("busy_timeout", "5000")
        })
    }

    private fun createMySQLDataSource(config: FileConfiguration): HikariDataSource {
        val host = config.getString("database.mysql.host", "localhost")!!
        val port = config.getInt("database.mysql.port", 3306)
        val database = config.getString("database.mysql.database", "betterend")!!
        val username = config.getString("database.mysql.username", "root")!!
        val password = config.getString("database.mysql.password", "")!!
        return HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:mysql://$host:$port/$database?useSSL=false&serverTimezone=UTC"
            driverClassName = "com.mysql.cj.jdbc.Driver"
            this.username = username
            this.password = password
            maximumPoolSize = config.getInt("database.mysql.pool-size", 10)
            connectionTestQuery = "SELECT 1"
            poolName = "BetterEndCities-MySQL"
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
            addDataSourceProperty("useServerPrepStmts", "true")
        })
    }

    private suspend fun createTables() = withContext(Dispatchers.IO) {
        val autoId = if (_databaseType == DatabaseType.SQLITE)
            "INTEGER PRIMARY KEY AUTOINCREMENT" else "INT AUTO_INCREMENT PRIMARY KEY"
        // MySQL's TEXT stops at 64 KB, which a chest of written books or full
        // shulker boxes can pass; SQLite's TEXT has no such limit.
        val bigText = if (_databaseType == DatabaseType.SQLITE) "TEXT" else "MEDIUMTEXT"
        connection.use { conn ->
            conn.createStatement().use { stmt ->
                // origin_* identifies a city across chunk loads; min/max_* enclose its pieces.
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS cities (
                        id $autoId,
                        world VARCHAR(64) NOT NULL,
                        min_x INT NOT NULL, min_y INT NOT NULL, min_z INT NOT NULL,
                        max_x INT NOT NULL, max_y INT NOT NULL, max_z INT NOT NULL,
                        origin_x INT NOT NULL, origin_y INT NOT NULL, origin_z INT NOT NULL,
                        created_at BIGINT NOT NULL,
                        last_reset BIGINT,
                        snapshot_file VARCHAR(255),
                        loot_cycle_start BIGINT,
                        has_ship INT NOT NULL DEFAULT 0,
                        ship_x INT, ship_y INT, ship_z INT,
                        head_taken INT NOT NULL DEFAULT 0,
                        UNIQUE (world, origin_x, origin_y, origin_z)
                    )
                    """.trimIndent()
                )
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS city_pieces (
                        id $autoId,
                        city_id INT NOT NULL,
                        min_x INT NOT NULL, min_y INT NOT NULL, min_z INT NOT NULL,
                        max_x INT NOT NULL, max_y INT NOT NULL, max_z INT NOT NULL,
                        FOREIGN KEY (city_id) REFERENCES cities(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                createIndex(stmt, "idx_city_pieces_city", "city_pieces(city_id)")

                // Each player's private copy of a container. Cleared per city on refresh.
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS player_container_loot (
                        city_id INT NOT NULL,
                        x INT NOT NULL, y INT NOT NULL, z INT NOT NULL,
                        player_uuid VARCHAR(36) NOT NULL,
                        contents $bigText NOT NULL,
                        updated_at BIGINT NOT NULL,
                        PRIMARY KEY (city_id, x, y, z, player_uuid),
                        FOREIGN KEY (city_id) REFERENCES cities(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                // What every first copy starts from. Kept across resets so staff edits stick.
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS container_template (
                        city_id INT NOT NULL,
                        x INT NOT NULL, y INT NOT NULL, z INT NOT NULL,
                        contents $bigText NOT NULL,
                        material VARCHAR(64) NOT NULL,
                        updated_at BIGINT NOT NULL,
                        PRIMARY KEY (city_id, x, y, z),
                        FOREIGN KEY (city_id) REFERENCES cities(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                // per-refresh mode compares claimed_at to the loot cycle start, so nothing is deleted.
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS elytra_claims (
                        city_id INT NOT NULL,
                        player_uuid VARCHAR(36) NOT NULL,
                        claimed_at BIGINT NOT NULL,
                        PRIMARY KEY (city_id, player_uuid),
                        FOREIGN KEY (city_id) REFERENCES cities(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                createIndex(stmt, "idx_elytra_claims_player", "elytra_claims(player_uuid)")
            }
            // Columns added after 0.3.0. CREATE TABLE above covers new databases.
            val existing = HashSet<String>()
            conn.metaData.getColumns(conn.catalog, null, "cities", null).use { rs ->
                while (rs.next()) existing.add(rs.getString("COLUMN_NAME").lowercase())
            }
            conn.createStatement().use { stmt ->
                for ((column, type) in listOf(
                    "ship_x" to "INT", "ship_y" to "INT", "ship_z" to "INT",
                    "head_taken" to "INT NOT NULL DEFAULT 0",
                )) {
                    if (column !in existing) stmt.execute("ALTER TABLE cities ADD COLUMN $column $type")
                }
            }
            fixInclusiveBounds(conn)
        }
    }

    // MySQL has no CREATE INDEX IF NOT EXISTS; error 1061 means it already exists.
    private fun createIndex(stmt: java.sql.Statement, name: String, on: String) {
        if (_databaseType == DatabaseType.SQLITE) {
            stmt.execute("CREATE INDEX IF NOT EXISTS $name ON $on")
            return
        }
        try {
            stmt.execute("CREATE INDEX $name ON $on")
        } catch (e: java.sql.SQLException) {
            if (e.errorCode != 1061) throw e
        }
    }

    // Boxes stored by earlier versions are one block short on their max side. Grows them once.
    private fun fixInclusiveBounds(conn: Connection) {
        conn.createStatement().use { stmt ->
            stmt.execute("CREATE TABLE IF NOT EXISTS be_meta (k VARCHAR(64) NOT NULL PRIMARY KEY, v VARCHAR(255))")
        }
        val done = conn.prepareStatement("SELECT 1 FROM be_meta WHERE k = 'inclusive_bounds'").use { stmt ->
            stmt.executeQuery().use { it.next() }
        }
        if (done) return
        conn.autoCommit = false
        try {
            conn.createStatement().use { stmt ->
                for (table in listOf("cities", "city_pieces")) {
                    stmt.executeUpdate("UPDATE $table SET max_x = max_x + 1, max_y = max_y + 1, max_z = max_z + 1")
                }
                stmt.executeUpdate("INSERT INTO be_meta (k, v) VALUES ('inclusive_bounds', '1')")
            }
            conn.commit()
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = true
        }
    }

    fun close() {
        if (::dataSource.isInitialized && !dataSource.isClosed) {
            dataSource.close()
            plugin.logger.info("Database pool closed")
        }
    }
}
