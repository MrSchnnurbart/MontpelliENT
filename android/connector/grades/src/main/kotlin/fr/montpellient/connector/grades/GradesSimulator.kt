package fr.montpellient.connector.grades

import fr.montpellient.core.model.GradeEntry
import fr.montpellient.core.model.GradeReport
import java.util.Locale

/**
 * Moteur de simulation de moyenne et calcul de compensation universitaire.
 *
 * Règles standard LMD (Université de Montpellier) :
 * - Compensation entre semestres d'une même année (Moyenne Année >= 10/20).
 * - Compensation entre UE d'un même semestre (pondération par crédits ECTS ou coefficients).
 * - Pas de note éliminatoire stricte sauf disposition spécifique de maquette.
 */
object GradesSimulator {

    data class SimulationResult(
        val currentAverage: Double?,
        val simulatedAverage: Double?,
        val isPassed: Boolean,
        val compensationStatus: String,
        val missingPointsForPassing: Double?
    )

    /**
     * Calcule la moyenne pondérée d'un ensemble d'entrées (UEs ou matières avec notes).
     * [customOverrides] permet à l'étudiant de simuler des notes futures ou hypothétiques.
     */
    fun calculateAverage(
        entries: List<GradeEntry>,
        customOverrides: Map<String, Double> = emptyMap()
    ): Double? {
        var totalPoints = 0.0
        var totalCoeff = 0.0

        for (entry in entries) {
            val coeff = entry.credits?.toDoubleOrNull() ?: 1.0
            val gradeVal = customOverrides[entry.id] ?: parseGrade(entry.grade)

            if (gradeVal != null) {
                totalPoints += gradeVal * coeff
                totalCoeff += coeff
            }
        }

        return if (totalCoeff > 0.0) {
            String.format(Locale.US, "%.2f", totalPoints / totalCoeff).toDouble()
        } else {
            null
        }
    }

    /**
     * Évalue si le semestre ou l'année est validé(e) ou compensable.
     */
    fun evaluateReport(
        report: GradeReport,
        customOverrides: Map<String, Double> = emptyMap()
    ): SimulationResult {
        // Filtrer sur les UEs de niveau 1
        val ues = report.entries.filter { it.level == 1 }
        val targetEntries = if (ues.isNotEmpty()) ues else report.entries.filter { it.level > 0 }

        val currentAvg = calculateAverage(targetEntries, emptyMap())
        val simAvg = calculateAverage(targetEntries, customOverrides)

        val finalAvg = simAvg ?: currentAvg
        val isPassed = finalAvg != null && finalAvg >= 10.0

        val compensationStatus = when {
            finalAvg == null -> "En attente de notes"
            finalAvg >= 10.0 -> "Admis / Validé"
            finalAvg >= 8.0 -> "Ajourné (Compensable sur l'année)"
            else -> "Ajourné"
        }

        val missing = if (finalAvg != null && finalAvg < 10.0) {
            String.format(Locale.US, "%.2f", 10.0 - finalAvg).toDouble()
        } else null

        return SimulationResult(
            currentAverage = currentAvg,
            simulatedAverage = simAvg,
            isPassed = isPassed,
            compensationStatus = compensationStatus,
            missingPointsForPassing = missing
        )
    }

    private fun parseGrade(gradeStr: String?): Double? {
        if (gradeStr.isNullOrBlank()) return null
        return gradeStr.replace(',', '.').toDoubleOrNull()
    }
}
