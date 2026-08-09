package app.lumen.db

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.security.MessageDigest
import java.util.Properties

@Composable
actual fun rememberLumenDb(): LumenDb = remember {
    val dir = File(System.getProperty("user.home"), ".config/lumen").apply { mkdirs() }
    val file = File(dir, "lumen.db")
    val fresh = !file.exists()
    val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", Properties())
    if (fresh) LumenDb.Schema.create(driver)
    // Base existante : les tables ajoutées depuis sa création n'y sont pas.
    ensureLumenTables(driver)
    LumenDb(driver)
}

actual fun sha256Hex(input: String): String =
    MessageDigest.getInstance("SHA-256").digest(input.encodeToByteArray())
        .joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

actual fun epochMillis(): Long = System.currentTimeMillis()
