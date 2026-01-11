package com.trililingo.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * MIGRATION_1_2
 *
 * ✅ Corrige o cenário em que versões antigas criaram a tabela com typo:
 *     - activity_attemps (ERRADO)
 *   e agora o app usa:
 *     - activity_attempts (CERTO)
 *
 * Também adiciona as novas colunas (hints/penalidade/XP) de forma idempotente
 * e garante os índices esperados pelo Room.
 */
object Migrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val hasCorrect = tableExists(db, "activity_attempts")
            val hasTypo = tableExists(db, "activity_attemps")

            // 1) Se ambas existem (estado intermediário), tenta preservar dados do typo dentro da correta
            if (hasCorrect && hasTypo) {
                // Garante colunas na tabela correta antes de copiar
                ensureNewColumns(db, "activity_attempts")

                val hintExpr = if (columnExists(db, "activity_attemps", "hintCount")) "hintCount" else "0"
                val baseExpr = if (columnExists(db, "activity_attemps", "baseXp")) "baseXp" else "0"
                val multExpr = if (columnExists(db, "activity_attemps", "xpMultiplier")) "xpMultiplier" else "1.0"
                val awardExpr = if (columnExists(db, "activity_attemps", "xpAwarded")) "xpAwarded" else "0"

                // Copia attempts preservando o id quando possível. Se já existir, ignora.
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO activity_attempts
                    (id, sessionId, itemId, isCorrect, responseMs, chosenAnswer, correctAnswer, createdAtMs,
                     hintCount, baseXp, xpMultiplier, xpAwarded)
                    SELECT
                      id, sessionId, itemId, isCorrect, responseMs, chosenAnswer, correctAnswer, createdAtMs,
                      $hintExpr, $baseExpr, $multExpr, $awardExpr
                    FROM activity_attemps
                    """.trimIndent()
                )

                // Remove a tabela typo para evitar confusão futura
                db.execSQL("DROP TABLE activity_attemps")
            }

            // 2) Se só existe a tabela com typo, renomeia para o nome correto
            val nowHasCorrect = tableExists(db, "activity_attempts")
            val nowHasTypo = tableExists(db, "activity_attemps")

            if (!nowHasCorrect && nowHasTypo) {
                db.execSQL("ALTER TABLE activity_attemps RENAME TO activity_attempts")
            }

            // 3) Se a tabela correta existe, adiciona colunas novas (idempotente)
            if (tableExists(db, "activity_attempts")) {
                ensureNewColumns(db, "activity_attempts")

                // 4) Garante índices esperados pelo Room (@Index)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_activity_attempts_sessionId ON activity_attempts(sessionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_activity_attempts_itemId ON activity_attempts(itemId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_activity_attempts_createdAtMs ON activity_attempts(createdAtMs)")
            }
        }
    }

    private fun ensureNewColumns(db: SupportSQLiteDatabase, table: String) {
        addColumnIfMissing(
            db = db,
            table = table,
            column = "hintCount",
            ddl = "ALTER TABLE $table ADD COLUMN hintCount INTEGER NOT NULL DEFAULT 0"
        )
        addColumnIfMissing(
            db = db,
            table = table,
            column = "baseXp",
            ddl = "ALTER TABLE $table ADD COLUMN baseXp INTEGER NOT NULL DEFAULT 0"
        )
        addColumnIfMissing(
            db = db,
            table = table,
            column = "xpMultiplier",
            ddl = "ALTER TABLE $table ADD COLUMN xpMultiplier REAL NOT NULL DEFAULT 1.0"
        )
        addColumnIfMissing(
            db = db,
            table = table,
            column = "xpAwarded",
            ddl = "ALTER TABLE $table ADD COLUMN xpAwarded INTEGER NOT NULL DEFAULT 0"
        )
    }

    private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean {
        db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(table)
        ).use { c -> return c.moveToFirst() }
    }

    private fun columnExists(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
        db.query("PRAGMA table_info($table)").use { cursor ->
            val nameIdx = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIdx) == column) return true
            }
        }
        return false
    }

    private fun addColumnIfMissing(db: SupportSQLiteDatabase, table: String, column: String, ddl: String) {
        if (!columnExists(db, table, column)) {
            db.execSQL(ddl)
        }
    }
}
