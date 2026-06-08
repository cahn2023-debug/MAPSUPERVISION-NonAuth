package com.mapsupervision.data.db

import androidx.room.TypeConverter
import com.mapsupervision.domain.model.PhotoLocationStatus
import com.mapsupervision.domain.model.ProjectStorageMode

class DbTypeConverters {
    @TypeConverter
    fun fromProjectStorageMode(value: ProjectStorageMode?): String? = value?.name

    @TypeConverter
    fun toProjectStorageMode(value: String?): ProjectStorageMode =
        value?.let(ProjectStorageMode::valueOf) ?: ProjectStorageMode.LEGACY_SHARED

    @TypeConverter
    fun fromPhotoLocationStatus(value: PhotoLocationStatus?): String? = value?.name

    @TypeConverter
    fun toPhotoLocationStatus(value: String?): PhotoLocationStatus =
        value?.let(PhotoLocationStatus::valueOf) ?: PhotoLocationStatus.MISSING
}
