package com.example.todoapplication.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GamificationDao {
    @Query("SELECT * FROM gamification WHERE id = 0")
    suspend fun get(): GamificationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GamificationEntity)
}

@Dao
interface TaskCacheDao {
    @Query("SELECT * FROM task_cache ORDER BY cachedAt ASC")
    suspend fun getAll(): List<TaskCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TaskCacheEntity>)

    @Query("DELETE FROM task_cache")
    suspend fun clear()
}
