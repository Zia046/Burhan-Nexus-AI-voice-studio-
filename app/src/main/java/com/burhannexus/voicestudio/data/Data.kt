package com.burhannexus.voicestudio.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val script: String,
    val voiceId: String,
    val voiceName: String,
    val engineName: String,
    val online: Boolean,
    val emotion: String,
    val intensity: Int,
    val speed: Float,
    val pitch: Float,
    val status: String,        // draft / running / done / failed / stopped
    val totalChunks: Int,
    val doneChunks: Int,
    val audioPath: String,
    val durationMs: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun all(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun get(id: Long): Project?

    @Insert
    suspend fun insert(p: Project): Long

    @Update
    suspend fun update(p: Project)

    @Delete
    suspend fun delete(p: Project)
}

@Database(entities = [Project::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun projects(): ProjectDao

    companion object {
        fun build(ctx: Context): AppDb =
            Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "voicestudio.db").build()
    }
}

/** App settings. API keys live in a separate Keystore-encrypted file and never in source code. */
class Prefs(private val ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val secure: SharedPreferences? by lazy {
        try {
            EncryptedSharedPreferences.create(
                "secure_settings",
                MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
                ctx,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            null
        }
    }

    val secureStorageAvailable: Boolean get() = secure != null

    var theme by mutableStateOf(sp.getString("theme", "system") ?: "system")
        private set

    fun updateTheme(value: String) {
        theme = value
        sp.edit().putString("theme", value).apply()
    }

    var geminiKey: String
        get() = secure?.getString("gemini_key", "") ?: ""
        set(v) {
            secure?.edit()?.putString("gemini_key", v.trim())?.apply()
        }

    var geminiModel: String
        get() = sp.getString("gemini_model", DEFAULT_GEMINI_MODEL) ?: DEFAULT_GEMINI_MODEL
        set(v) = sp.edit().putString("gemini_model", v.trim()).apply()

    var cloudConsent: Boolean
        get() = sp.getBoolean("cloud_consent", false)
        set(v) = sp.edit().putBoolean("cloud_consent", v).apply()

    var defaultVoiceId: String
        get() = sp.getString("default_voice", "") ?: ""
        set(v) = sp.edit().putString("default_voice", v).apply()

    var defaultEmotion: String
        get() = sp.getString("default_emotion", "None") ?: "None"
        set(v) = sp.edit().putString("default_emotion", v).apply()

    var defaultSpeed: Float
        get() = sp.getFloat("default_speed", 1f)
        set(v) = sp.edit().putFloat("default_speed", v).apply()

    var defaultPitch: Float
        get() = sp.getFloat("default_pitch", 1f)
        set(v) = sp.edit().putFloat("default_pitch", v).apply()

    var exportFormat: String
        get() = sp.getString("export_format", "mp3") ?: "mp3"
        set(v) = sp.edit().putString("export_format", v).apply()

    var mp3Bitrate: Int
        get() = sp.getInt("mp3_bitrate", 128)
        set(v) = sp.edit().putInt("mp3_bitrate", v).apply()

    var favorites: Set<String>
        get() = sp.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet("favorites", v).apply()

    companion object {
        const val DEFAULT_GEMINI_MODEL = "gemini-3.1-flash-tts-preview"
    }
}
