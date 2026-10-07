package com.debloated.player.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracks",
    foreignKeys = [
        ForeignKey(
            entity = Album::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("albumId")],
)
data class Track(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val albumId: Long,
    val title: String,
    val fileUri: String,
    /** Length of the track in milliseconds, or 0 if it could not be read. */
    val duration: Long,
    /** True once the track has been listened to (see PlayRules) during the album's current pass. */
    @ColumnInfo(defaultValue = "0") val played: Boolean = false,
)
