package app.lumen.db

import app.cash.sqldelight.db.SqlDriver

/**
 * Rattrapage de schéma pour les bases créées par d'ANCIENNES versions.
 *
 * Schema.create ne tourne qu'à la création du fichier : toute table ajoutée
 * dans Lumen.sq après coup n'existe pas dans les bases déjà en place (vu en
 * 1.4.0 : « no such table: storage_source » sur l'onglet Service). D'où ce
 * filet, IDEMPOTENT, exécuté à CHAQUE ouverture — les définitions doivent
 * rester le miroir exact de Lumen.sq.
 */
fun ensureLumenTables(driver: SqlDriver) {
    listOf(
        """CREATE TABLE IF NOT EXISTS hls_entry (
            id TEXT NOT NULL PRIMARY KEY,
            title TEXT NOT NULL,
            year INTEGER,
            masterPath TEXT NOT NULL,
            posterUrl TEXT,
            backdropUrl TEXT,
            overview TEXT,
            durationSeconds REAL NOT NULL,
            resolution TEXT,
            segmentFormat TEXT NOT NULL,
            importedAt INTEGER NOT NULL
        )""",
        """CREATE TABLE IF NOT EXISTS storage_source (
            id TEXT NOT NULL PRIMARY KEY,
            label TEXT NOT NULL,
            kind TEXT NOT NULL,
            endpoint TEXT NOT NULL,
            region TEXT,
            bucket TEXT NOT NULL,
            accessKey TEXT NOT NULL,
            secretKey TEXT NOT NULL,
            importedAt INTEGER NOT NULL,
            folders TEXT NOT NULL DEFAULT ''
        )""",
        """CREATE TABLE IF NOT EXISTS webdav_source (
            id TEXT NOT NULL PRIMARY KEY,
            label TEXT NOT NULL,
            baseUrl TEXT NOT NULL,
            username TEXT NOT NULL,
            password TEXT NOT NULL,
            importedAt INTEGER NOT NULL
        )""",
        """CREATE TABLE IF NOT EXISTS bucket_entry (
            id TEXT NOT NULL PRIMARY KEY,
            sourceId TEXT NOT NULL,
            title TEXT NOT NULL,
            year INTEGER,
            kind TEXT NOT NULL,
            objectKey TEXT NOT NULL,
            posterUrl TEXT,
            backdropUrl TEXT,
            overview TEXT,
            indexedAt INTEGER NOT NULL
        )""",
        """CREATE TABLE IF NOT EXISTS ftp_source (
            id TEXT NOT NULL PRIMARY KEY,
            label TEXT NOT NULL,
            host TEXT NOT NULL,
            port INTEGER NOT NULL,
            username TEXT NOT NULL,
            password TEXT NOT NULL,
            importedAt INTEGER NOT NULL
        )""",
    ).forEach { driver.execute(null, it, 0) }

    // Colonnes ajoutées APRÈS coup : « CREATE TABLE IF NOT EXISTS » ne touche
    // pas une table déjà présente. SQLite refuse un ADD COLUMN en double, on
    // ignore donc l'échec — c'est ce qui rend l'opération rejouable.
    listOf(
        "ALTER TABLE storage_source ADD COLUMN folders TEXT NOT NULL DEFAULT ''",
    ).forEach { runCatching { driver.execute(null, it, 0) } }
}
