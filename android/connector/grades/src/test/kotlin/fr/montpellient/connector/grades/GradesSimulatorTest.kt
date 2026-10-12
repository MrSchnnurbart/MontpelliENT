package fr.montpellient.connector.grades

import fr.montpellient.core.model.GradeEntry
import fr.montpellient.core.model.GradeReport
import org.junit.Assert.*
import org.junit.Test

class GradesSimulatorTest {

    @Test
    fun testWeightedAverageCalculation() {
        val entries = listOf(
            GradeEntry(id = "ue1", code = "HAI501I", label = "Génie logiciel", level = 1, grade = "14,00", credits = "6"),
            GradeEntry(id = "ue2", code = "HAI502I", label = "Bases de données", level = 1, grade = "10,00", credits = "6"),
            GradeEntry(id = "ue3", code = "HAI503I", label = "Réseaux", level = 1, grade = "12,00", credits = "6")
        )

        val avg = GradesSimulator.calculateAverage(entries)
        assertNotNull(avg)
        assertEquals(12.00, avg!!, 0.01)
    }

    @Test
    fun testSimulationWithOverrides() {
        val entries = listOf(
            GradeEntry(id = "ue1", code = "HAI501I", label = "Génie logiciel", level = 1, grade = "8,00", credits = "6"),
            GradeEntry(id = "ue2", code = "HAI502I", label = "Bases de données", level = 1, grade = null, credits = "6")
        )

        val report = GradeReport(id = "r1", academicYear = "2025-2026", program = "L3", entries = entries)

        // Avant override
        val initialResult = GradesSimulator.evaluateReport(report)
        assertEquals(8.00, initialResult.currentAverage!!, 0.01)
        assertFalse(initialResult.isPassed)
        assertEquals("Ajourné (Compensable sur l'année)", initialResult.compensationStatus)

        // Simulation : l'étudiant simule un 14/20 pour ue2
        val simResult = GradesSimulator.evaluateReport(report, customOverrides = mapOf("ue2" to 14.0))
        assertEquals(11.00, simResult.simulatedAverage!!, 0.01)
        assertTrue(simResult.isPassed)
        assertEquals("Admis / Validé", simResult.compensationStatus)
    }
}
