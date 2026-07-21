package com.ottotech.workoutbuilder.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {

    @Query("SELECT * FROM exercises ORDER BY name")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercise_tags")
    fun observeAllTags(): Flow<List<ExerciseTagEntity>>

    @Query("SELECT * FROM exercises")
    suspend fun getAll(): List<ExerciseEntity>

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(rows: List<ExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(rows: List<ExerciseTagEntity>)

    /** Exercises and their tags must land together or not at all. */
    @Transaction
    suspend fun insertAll(exercises: List<ExerciseEntity>, tags: List<ExerciseTagEntity>) {
        insertExercises(exercises)
        insertTags(tags)
    }

    @Query("UPDATE exercises SET demoUrl = :url WHERE id = :id")
    suspend fun setDemoUrl(id: String, url: String?)
}
