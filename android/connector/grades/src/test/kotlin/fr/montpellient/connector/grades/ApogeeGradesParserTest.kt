package fr.montpellient.connector.grades

import fr.montpellient.core.model.GradeEntry
import org.junit.Assert.*
import org.junit.Test

class ApogeeGradesParserTest {

    private val sampleApogeeHtml = """
        <!DOCTYPE html>
        <html>
        <head><title>Mon Dossier Web - Notes</title></head>
        <body>
            <div class="divNotes">
                <div class="titreEtape">Licence 3 Informatique (2025-2026)</div>
                <table class="tableauNotes">
                    <thead>
                        <tr>
                            <th>Code</th>
                            <th>Libellé</th>
                            <th>Session</th>
                            <th>Note</th>
                            <th>Résultat</th>
                            <th>Crédits</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr class="niveau0">
                            <td class="cod_el">HAS5I</td>
                            <td class="lib_el">Semestre 5</td>
                            <td class="ses">Session 1</td>
                            <td class="not_el">14,10</td>
                            <td class="res">ADM</td>
                            <td class="ects">30</td>
                        </tr>
                        <tr class="niveau1">
                            <td class="cod_el">HAI501I</td>
                            <td class="lib_el">Génie logiciel</td>
                            <td class="ses">Session 1</td>
                            <td class="not_el">15,50</td>
                            <td class="res">ADM</td>
                            <td class="ects">6</td>
                        </tr>
                        <tr class="niveau2">
                            <td class="cod_el">HAI501IA</td>
                            <td class="lib_el">Contrôle Continu</td>
                            <td class="ses">Session 1</td>
                            <td class="not_el">16,00</td>
                            <td class="res"></td>
                            <td class="ects"></td>
                        </tr>
                        <tr class="niveau2">
                            <td class="cod_el">HAI501IB</td>
                            <td class="lib_el">Examen Terminal</td>
                            <td class="ses">Session 1</td>
                            <td class="not_el">15,00</td>
                            <td class="res"></td>
                            <td class="ects"></td>
                        </tr>
                        <tr class="niveau1">
                            <td class="cod_el">HAI502I</td>
                            <td class="lib_el">Bases de données avancées</td>
                            <td class="ses">Session 1</td>
                            <td class="not_el">12,70</td>
                            <td class="res">ADM</td>
                            <td class="ects">6</td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </body>
        </html>
    """.trimIndent()

    @Test
    fun testParseApogeeGradesReport() {
        val reports = ApogeeGradesParser.parse(sampleApogeeHtml)

        assertEquals(1, reports.size)
        val report = reports[0]
        assertEquals("Licence 3 Informatique", report.program)
        assertEquals("2025-2026", report.academicYear)
        assertEquals(5, report.entries.size)

        // Semestre 5 (niveau 0)
        val s5 = report.entries[0]
        assertEquals("HAS5I", s5.code)
        assertEquals("Semestre 5", s5.label)
        assertEquals(0, s5.level)
        assertEquals("14,10", s5.grade)
        assertEquals("ADM", s5.result)
        assertEquals("30", s5.credits)

        // UE Génie Logiciel (niveau 1)
        val ue1 = report.entries[1]
        assertEquals("HAI501I", ue1.code)
        assertEquals("Génie logiciel", ue1.label)
        assertEquals(1, ue1.level)
        assertEquals("15,50", ue1.grade)
        assertEquals("ADM", ue1.result)
        assertEquals("6", ue1.credits)

        // Épreuve CC (niveau 2)
        val cc = report.entries[2]
        assertEquals("HAI501IA", cc.code)
        assertEquals("Contrôle Continu", cc.label)
        assertEquals(2, cc.level)
        assertEquals("16,00", cc.grade)
    }
}
