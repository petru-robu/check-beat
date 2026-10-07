package com.debloated.player.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "albums",
    indices = [Index(value = ["folderUri"], unique = true)],
)
data class Album(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderUri: String,
    val name: String,
    @ColumnInfo(defaultValue = "0") val playCount: Int = 0,
)
