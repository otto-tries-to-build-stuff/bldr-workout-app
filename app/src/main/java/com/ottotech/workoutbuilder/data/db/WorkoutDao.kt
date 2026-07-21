package com.ottotech.workoutbuilder.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    @Query("SELECT * FROM workouts ORDER BY createdAt DESC")
    fun observeWorkouts(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getWorkout(id: String): WorkoutEntity?

    @Query("SELECT * FROM sections WHERE workoutId = :workoutId ORDER BY orderIndex")
    suspend fun getSections(workoutId: String): List<SectionEntity>

    @Query("SELECT * FROM workout_items WHERE sectionId IN (:sectionIds) ORDER BY orderIndex")
    suspend fun getItems(sectionIds: List<String>): List<WorkoutItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkout(row: WorkoutEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSections(rows: List<SectionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(rows: List<WorkoutItemEntity>)

    @Query("DELETE FROM sections WHERE workoutId = :workoutId")
    suspend fun deleteSectionsFor(workoutId: String)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteWorkout(id: String)

    @Query("UPDATE workouts SET lastPerformedAt = :timestamp WHERE id = :id")
    suspend fun markPerformed(id: String, timestamp: Long)

    /**
     * Replaces a workout wholesale. Deleting the sections cascades to their items, so
     * an edit that removes a section cannot leave orphaned rows behind.
     */
    @Transaction
    suspend fun upsertFull(
        workout: WorkoutEntity,
        sections: List<SectionEntity>,
        items: List<WorkoutItemEntity>,
    ) {
        insertWorkout(workout)
        deleteSectionsFor(workout.id)
        insertSections(sections)
        insertItems(items)
    }
}
