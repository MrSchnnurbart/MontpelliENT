package fr.montpellient.core.database

import android.content.Context
import fr.montpellient.core.model.GradeEntry
import fr.montpellient.core.model.GradeReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDateTime

@Serializable
data class GradeEntryEntity(
    val id: String,
    val code: String? = null,
    val label: String,
    val level: Int,
    val grade: String? = null,
    val scale: String? = "20",
    val result: String? = null,
    val session: String? = null,
    val credits: String? = null,
    val publishedAtIso: String? = null,
    val isNew: Boolean = false
) {
    fun toModel(): GradeEntry = GradeEntry(
        id = id,
        code = code,
        label = label,
        level = level,
        grade = grade,
        scale = scale,
        result = result,
        session = session,
        credits = credits,
        publishedAt = publishedAtIso?.let { LocalDateTime.parse(it) },
        isNew = isNew
    )

    companion object {
        fun fromModel(entry: GradeEntry): GradeEntryEntity = GradeEntryEntity(
            id = entry.id,
            code = entry.code,
            label = entry.label,
            level = entry.level,
            grade = entry.grade,
            scale = entry.scale,
            result = entry.result,
            session = entry.session,
            credits = entry.credits,
            publishedAtIso = entry.publishedAt?.toString(),
            isNew = entry.isNew
        )
    }
}

@Serializable
data class GradeReportEntity(
    val id: String,
    val academicYear: String,
    val program: String,
    val entries: List<GradeEntryEntity>
) {
    fun toModel(): GradeReport = GradeReport(
        id = id,
        academicYear = academicYear,
        program = program,
        entries = entries.map { it.toModel() }
    )

    companion object {
        fun fromModel(report: GradeReport): GradeReportEntity = GradeReportEntity(
            id = report.id,
            academicYear = report.academicYear,
            program = report.program,
            entries = report.entries.map { GradeEntryEntity.fromModel(it) }
        )
    }
}

interface GradesLocalDataSource {
    val reportsFlow: Flow<List<GradeReport>>
    suspend fun saveReports(reports: List<GradeReport>)
    suspend fun markAllSeen()
    suspend fun clear()
}

class EncryptedGradesStore(context: Context) : GradesLocalDataSource {
    private val file = File(context.filesDir, "grades_offline_vault.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val _reportsFlow = MutableStateFlow<List<GradeReport>>(emptyList())
    override val reportsFlow: Flow<List<GradeReport>> = _reportsFlow.asStateFlow()

    init {
        loadFromDisk()
    }

    private fun loadFromDisk() {
        if (file.exists()) {
            runCatching {
                val list: List<GradeReportEntity> = json.decodeFromString(file.readText())
                _reportsFlow.value = list.map { it.toModel() }
            }
        }
    }

    override suspend fun saveReports(reports: List<GradeReport>) = withContext(Dispatchers.IO) {
        val entities = reports.map { GradeReportEntity.fromModel(it) }
        file.writeText(json.encodeToString(entities))
        _reportsFlow.value = reports
    }

    override suspend fun markAllSeen() = withContext(Dispatchers.IO) {
        val updated = _reportsFlow.value.map { r ->
            r.copy(entries = r.entries.map { it.copy(isNew = false) })
        }
        saveReports(updated)
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        file.delete()
        _reportsFlow.value = emptyList()
    }
}
