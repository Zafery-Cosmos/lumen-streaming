package app.lumen.db

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import java.security.MessageDigest

@Composable
actual fun rememberLumenDb(): LumenDb {
    val context = LocalContext.current.applicationContext
    return remember {
        val driver = AndroidSqliteDriver(LumenDb.Schema, context, "lumen.db")
        // Même filet que sur desktop : une base d'ancienne version n'a pas les
        // tables ajoutées depuis (le onCreate d'Android ne rejoue jamais).
        ensureLumenTables(driver)
        LumenDb(driver)
    }
}

actual fun sha256Hex(input: String): String =
    MessageDigest.getInstance("SHA-256").digest(input.encodeToByteArray())
        .joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

actual fun epochMillis(): Long = System.currentTimeMillis()
