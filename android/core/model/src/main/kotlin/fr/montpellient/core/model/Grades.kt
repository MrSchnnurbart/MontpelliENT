package fr.montpellient.core.model

import java.time.LocalDateTime

/**
 * Ligne du relevé de notes officiel, affichée telle que publiée par l'ENT
 * (pas de calcul de moyenne côté appli).
 *
 * [level] reproduit l'arborescence : 0 = année / semestre, 1 = UE, 2 = élément (ECUE).
 */
data class GradeEntry(
    val id: String,
    val code: String?,
    val label: String,
    val level: Int,
    val grade: String? = null,
    val scale: String? = "20",
    val result: String? = null,
    val session: String? = null,
    val credits: String? = null,
    val publishedAt: LocalDateTime? = null,
    val isNew: Boolean = false,
)

/** Relevé d'une inscription (année universitaire + formation). */
data class GradeReport(
    val id: String,
    val academicYear: String,
    val program: String,
    val entries: List<GradeEntry>,
)

/** Note d'un élément de note Moodle (affichée séparément des notes officielles). */
data class MoodleGrade(
    val id: Long,
    val courseId: Long,
    val courseName: String,
    val itemName: String,
    val grade: String?,
    val range: String?,
    val feedback: String? = null,
    val gradedAt: LocalDateTime? = null,
)
