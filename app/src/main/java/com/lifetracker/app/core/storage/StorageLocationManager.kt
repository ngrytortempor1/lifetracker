package com.lifetracker.app.core.storage

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * Persists and resolves the directory where JSONL assets are stored.
 */
class StorageLocationManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasLocation(): Boolean = prefs.contains(KEY_LOCATION)

    fun getLocation(): JsonlStorageLocation? =
        prefs.getString(KEY_LOCATION, null)?.let { stored ->
            JsonlStorageLocation.entries.firstOrNull { it.id == stored }
        }

    /**
     * Stores the selected location and migrates known JSONL files from the previous directory.
     */
    fun setLocation(context: Context, location: JsonlStorageLocation) {
        val previousLocation = getLocation() ?: JsonlStorageLocation.INTERNAL_PRIVATE
        val previousDir = previousLocation.resolveDirectory(context)

        prefs.edit().putString(KEY_LOCATION, location.id).apply()

        val targetDir = resolveDirectory(context)
        if (previousDir != null && previousDir.exists() && previousDir != targetDir) {
            MIGRATION_FILES.forEach { fileName ->
                val source = File(previousDir, fileName)
                if (source.exists()) {
                    val destination = File(targetDir, fileName)
                    source.copyTo(destination, overwrite = true)
                }
            }
        }
    }

    /**
     * Resolves the directory for the current selection. Falls back to the internal location if the
     * configured directory is unavailable.
     */
    fun resolveDirectory(context: Context): File {
        val location = getLocation() ?: JsonlStorageLocation.INTERNAL_PRIVATE
        val primary = location.resolveDirectory(context)
        val directory = primary ?: JsonlStorageLocation.INTERNAL_PRIVATE.resolveDirectory(context)
        requireNotNull(directory) { "Unable to resolve storage directory for $location" }
        if (!directory.exists()) {
            directory.mkdirs()
        }
        return directory
    }

    companion object {
        private const val PREFS_NAME = "storage_location_prefs"
        private const val KEY_LOCATION = "jsonl_storage_location"
        private val MIGRATION_FILES = listOf(
            "events.jsonl",
            "habits.json",
            "tags.json",
            "task_lists.json",
            "tasks.json"
        )
    }
}

enum class JsonlStorageLocation(
    val id: String,
    private val title: String,
    private val description: String,
    private val resolver: (Context) -> File?
) {
    INTERNAL_PRIVATE(
        id = "internal_private",
        title = "Internal storage (recommended)",
        description = "Stores JSONL files inside the app's private directory. Removed automatically when the app is uninstalled.",
        resolver = { context -> context.filesDir }
    ),
    EXTERNAL_PRIVATE(
        id = "external_private",
        title = "External storage (app-specific)",
        description = "Uses the app-specific directory on external storage. Convenient for copying via USB; still removed on uninstall.",
        resolver = { context -> context.getExternalFilesDir(null) }
    ),
    EXTERNAL_DOCUMENTS(
        id = "external_documents",
        title = "External storage (Documents)",
        description = "Creates a LifeTracker folder under the Documents directory on external storage. Accessible from file managers.",
        resolver = { context ->
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?.let { File(it, "LifeTracker") }
        }
    );

    fun displayName(): String = title

    fun descriptionText(): String = description

    fun resolveDirectory(context: Context): File? = resolver(context)
}
