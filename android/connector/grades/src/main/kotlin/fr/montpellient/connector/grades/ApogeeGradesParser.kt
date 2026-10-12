package fr.montpellient.connector.grades

import fr.montpellient.core.model.GradeEntry
import fr.montpellient.core.model.GradeReport
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Parseur HTML pour les relevés de notes Apogée / MonDossierWeb (MDW) et portail ENT.
 *
 * Structure typique d'une page de notes Apogée MDW :
 * - Div / Table principale avec les inscriptions administratives et pédagogiques.
 * - Tableaux par étape/semestre (`table.tableauNotes` ou `table.bdNotes`).
 * - Lignes d'arborescence :
 *   - Ligne niveau 0 : Année / Étape / Semestre (ex: Semestre 5, Année L3).
 *   - Ligne niveau 1 : Éléments constitutifs / Unités d'Enseignement (UE).
 *   - Ligne niveau 2 : Épreuves / Éléments constitutifs (ECUE, CC, CT).
 *
 * Colonnes types :
 * Code UE/Élément | Libellé | Session | Note (/20) | Barème | Résultat (ADM/AJ) | Crédits (ECTS)
 */
object ApogeeGradesParser {

    fun parse(html: String): List<GradeReport> {
        val doc: Document = Jsoup.parse(html)
        val reports = mutableListOf<GradeReport>()

        // 1. Chercher les sections d'inscriptions / étapes
        // Cas MonDossierWeb classique : conteneurs par étape
        val stepContainers = doc.select(".etapeNotes, .divNotes, .panelNotes, fieldset.notes")

        if (stepContainers.isNotEmpty()) {
            stepContainers.forEachIndexed { index, container ->
                val titleEl = container.selectFirst(".titreEtape, legend, h2, h3")
                val titleText = titleEl?.text()?.trim() ?: "Cursus"
                val academicYear = extractYear(titleText) ?: extractYear(doc.text()) ?: "Année en cours"

                val entries = parseEntriesFromElement(container)
                if (entries.isNotEmpty()) {
                    reports.add(
                        GradeReport(
                            id = "apogee-step-$index",
                            academicYear = academicYear,
                            program = cleanProgramTitle(titleText),
                            entries = entries
                        )
                    )
                }
            }
        }

        // Cas tableau global direct (ex: table.tableauNotes ou première table avec th contenant Note/Résultat)
        if (reports.isEmpty()) {
            val tables = doc.select("table")
            for ((index, table) in tables.withIndex()) {
                val entries = parseEntriesFromTable(table)
                if (entries.isNotEmpty()) {
                    val pageTitle = doc.selectFirst("h1, h2, .titrePage")?.text()?.trim() ?: "Relevé de notes"
                    val year = extractYear(pageTitle) ?: extractYear(doc.text()) ?: "Année en cours"
                    reports.add(
                        GradeReport(
                            id = "apogee-table-$index",
                            academicYear = year,
                            program = cleanProgramTitle(pageTitle),
                            entries = entries
                        )
                    )
                    break
                }
            }
        }

        return reports
    }

    private fun parseEntriesFromElement(element: Element): List<GradeEntry> {
        val table = element.selectFirst("table") ?: return emptyList()
        return parseEntriesFromTable(table)
    }

    private fun parseEntriesFromTable(table: Element): List<GradeEntry> {
        val entries = mutableListOf<GradeEntry>()
        val rows = table.select("tbody tr, tr")

        for ((rowIndex, row) in rows.withIndex()) {
            val cells = row.select("td")
            if (cells.size < 2) continue

            // Détection du niveau hiérarchique (0 = semestre, 1 = UE, 2 = épreuve/matière)
            val level = detectLevel(row, cells)

            // Détection des colonnes
            val code = cells.firstOrNull { it.hasClass("code") || it.hasClass("cod_el") }?.text()?.trim()
                ?: extractCode(cells[0].text().trim())

            val label = cells.firstOrNull { it.hasClass("libelle") || it.hasClass("lib_el") }?.text()?.trim()
                ?: if (code != null && cells[0].text().trim() == code && cells.size > 1) cells[1].text().trim()
                else cells[0].text().trim()

            if (label.isBlank() || label.equals("Libellé", ignoreCase = true) || label.equals("Élément", ignoreCase = true)) {
                continue
            }

            var grade: String? = null
            var result: String? = null
            var session: String? = null
            var credits: String? = null

            for (cell in cells) {
                val text = cell.text().trim()
                val cls = cell.className().lowercase()

                if (cls.contains("note") || cls.contains("not_el")) {
                    grade = sanitizeGrade(text)
                } else if (cls.contains("res") || cls.contains("resultat")) {
                    result = text.ifBlank { null }
                } else if (cls.contains("session") || cls.contains("ses")) {
                    session = text.ifBlank { null }
                } else if (cls.contains("credit") || cls.contains("ects")) {
                    credits = text.ifBlank { null }
                }
            }

            // Fallback si pas de classes CSS explicites
            if (grade == null) {
                for (cell in cells.drop(1)) {
                    val txt = cell.text().trim()
                    if (isGradeFormat(txt)) {
                        grade = sanitizeGrade(txt)
                        break
                    }
                }
            }
            if (result == null) {
                for (cell in cells) {
                    val txt = cell.text().trim()
                    if (txt in listOf("ADM", "AJ", "DEF", "ABI", "ABJ", "CMP", "ACQ")) {
                        result = txt
                        break
                    }
                }
            }

            entries.add(
                GradeEntry(
                    id = "entry-$rowIndex-${code ?: label.hashCode()}",
                    code = code,
                    label = label,
                    level = level,
                    grade = grade,
                    scale = "20",
                    result = result,
                    session = session,
                    credits = credits,
                    isNew = false
                )
            )
        }

        return entries
    }

    private fun detectLevel(row: Element, cells: List<Element>): Int {
        val rowClass = row.className().lowercase()
        if (rowClass.contains("niveau0") || rowClass.contains("semestre") || rowClass.contains("etape")) return 0
        if (rowClass.contains("niveau1") || rowClass.contains("ue")) return 1
        if (rowClass.contains("niveau2") || rowClass.contains("epreuve") || rowClass.contains("matiere")) return 2

        // Regarder le style d'indentation ou le premier td
        val firstTd = cells.firstOrNull() ?: return 1
        val style = firstTd.attr("style").lowercase()
        if (style.contains("padding-left: 2") || style.contains("padding-left: 3") || style.contains("margin-left: 2")) return 2
        if (style.contains("padding-left: 1") || style.contains("margin-left: 1")) return 1

        val text = firstTd.text().trim()
        if (text.startsWith("Semestre", ignoreCase = true) || text.startsWith("Année", ignoreCase = true)) {
            return 0
        }
        return 1
    }

    private fun extractCode(text: String): String? {
        val regex = Regex("^[A-Z0-9]{5,10}$")
        return if (regex.matches(text)) text else null
    }

    private fun isGradeFormat(text: String): Boolean {
        // Ex: "14.50", "14,50", "12", "09,25"
        val normalized = text.replace(',', '.')
        val num = normalized.toDoubleOrNull() ?: return false
        return num in 0.0..20.0
    }

    private fun sanitizeGrade(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isBlank() || trimmed == "-" || trimmed == "/" || trimmed == "N/A") return null
        return trimmed
    }

    private fun extractYear(text: String): String? {
        val match = Regex("""(20\d{2})[-/](20\d{2})""").find(text)
        return match?.value
    }

    private fun cleanProgramTitle(raw: String): String {
        return raw.replace(Regex("""\s*\(20\d{2}[-/]20\d{2}\)"""), "").trim()
    }
}
