package com.trililingo.data.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.trililingo.data.db.entities.LanguageItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LanguageItemDao {

    @Query("SELECT COUNT(*) FROM language_items")
    suspend fun countAll(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<LanguageItemEntity>)

    @Query("SELECT * FROM language_items WHERE language = :language AND skill = :skill")
    suspend fun getBySkill(language: String, skill: String): List<LanguageItemEntity>

    @Query("SELECT * FROM language_items WHERE language = :language")
    suspend fun getAllByLanguage(language: String): List<LanguageItemEntity>

    @Query("SELECT * FROM language_items WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<LanguageItemEntity>
}
