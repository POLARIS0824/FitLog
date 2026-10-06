package com.example.fitlog.data.vault

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
enum class DiaryDateFormat(private val pattern: String) {
    Dashed("yyyy-MM-dd"), Compact("yyyyMMdd"), Chinese("yyyy年MM月dd日");

    fun fileName(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern(pattern, Locale.ROOT)) + ".md"
}

@Serializable
data class DiarySettings(
    val directoryPath: List<String> = emptyList(),
    val dateFormat: DiaryDateFormat = DiaryDateFormat.Dashed,
)

interface DiarySettingsStore {
    suspend fun read(vaultId: String): DiarySettings
    suspend fun save(vaultId: String, settings: DiarySettings)
}

internal fun diarySettingsKey(vaultId: String): Preferences.Key<String> {
    requireVaultId(vaultId)
    return stringPreferencesKey("diary_" + fingerprint(vaultId.toByteArray(Charsets.UTF_8)))
}

class DiaryPreferences(private val store: DataStore<Preferences>) : DiarySettingsStore {

    override suspend fun read(vaultId: String): DiarySettings = store.data.first()[diarySettingsKey(vaultId)]
        ?.let { Json.decodeFromString<DiarySettings>(it) } ?: DiarySettings()

    override suspend fun save(vaultId: String, settings: DiarySettings) {
        store.edit { it[diarySettingsKey(vaultId)] = Json.encodeToString(settings) }
    }
}

data class DiaryDirectory(val uri: String, val name: String)

interface DiaryDirectories {
    suspend fun resolveDirectory(vaultUri: String, path: List<String>): String
    suspend fun directories(directory: String): List<DiaryDirectory>
    suspend fun canCreate(directory: String): Boolean
}

class DiaryCreationUnavailable : IOException()

data class ResolvedTodayLog(
    val vaultUri: String,
    val vaultId: String,
    val document: String?,
    val date: LocalDate,
    val directory: String,
    val fileName: String,
    val displayPath: String,
)

class TodayLogResolver(
    private val settings: DiarySettingsStore,
    private val directories: DiaryDirectories,
    private val documents: MarkdownDocuments,
) {
    suspend fun resolve(vaultUri: String, date: LocalDate, vaultId: String): ResolvedTodayLog {
        requireVaultId(vaultId)
        val config = settings.read(vaultId)
        val directory = directories.resolveDirectory(vaultUri, config.directoryPath)
        val name = config.dateFormat.fileName(date)
        val existing = documents.find(directory, name)
        if (existing == null && !directories.canCreate(directory)) throw DiaryCreationUnavailable()
        return ResolvedTodayLog(
            vaultUri = vaultUri, vaultId = vaultId, document = existing?.uri, date = date,
            directory = directory, fileName = name,
            displayPath = (config.directoryPath + name).joinToString("/"),
        )
    }
}
