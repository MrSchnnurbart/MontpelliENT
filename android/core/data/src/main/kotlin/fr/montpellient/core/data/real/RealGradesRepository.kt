package fr.montpellient.core.data.real

import fr.montpellient.connector.grades.GradesClient
import fr.montpellient.core.data.GradesRepository
import fr.montpellient.core.data.demo.DemoData
import fr.montpellient.core.database.GradesLocalDataSource
import fr.montpellient.core.model.GradeReport
import fr.montpellient.core.model.MoodleGrade
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf

/**
 * Dépôt réel des notes connectant [GradesClient] avec cache sécurisé hors-ligne [GradesLocalDataSource].
 * En cas d'indisponibilité réseau ou compte non connecté, bascule en douceur sur le cache local ou la démo.
 */
class RealGradesRepository(
    private val gradesClient: GradesClient,
    private val gradesLocalDataSource: GradesLocalDataSource,
    private val demoData: DemoData
) : GradesRepository {

    override val reports: Flow<List<GradeReport>> = gradesLocalDataSource.reportsFlow

    override val moodleGrades: Flow<List<MoodleGrade>> = flowOf(demoData.moodleGrades)

    override suspend fun markAllSeen() {
        gradesLocalDataSource.markAllSeen()
    }

    override suspend fun refresh() {
        val result = gradesClient.fetchGrades()
        if (result.isSuccess) {
            val list = result.getOrNull().orEmpty()
            if (list.isNotEmpty()) {
                gradesLocalDataSource.saveReports(list)
            }
        } else {
            // Si le cache est vide, initialiser avec les données de démonstration
            val current = gradesLocalDataSource.reportsFlow.first()
            if (current.isEmpty()) {
                gradesLocalDataSource.saveReports(demoData.gradeReports)
            }
        }
    }
}
