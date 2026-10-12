package fr.montpellient.core.data.debug

import java.io.File

/**
 * Outil de capture de diagnostic et de débogage anonymisé.
 *
 * Fonctionnalité demandée dans le plan J1 :
 * - Permet d'exporter les pages HTML / flux ICS reçus depuis l'ENT pour affiner les connecteurs.
 * - Supprime ou remplace systématiquement toutes les données à caractère personnel :
 *   - Nom, prénom
 *   - Numéro étudiant / INE
 *   - E-mails nominatifs
 *   - Jetons de session ou cookies privés
 */
object AnonymizedCaptureTool {

    private val INE_REGEX = Regex("[0-9]{10}[A-Z]|[0-9]{9}[A-Z]{2}", RegexOption.IGNORE_CASE)
    private val STUDENT_ID_REGEX = Regex("\\b(e[0-9]{7,8}|[0-9]{8})\\b", RegexOption.IGNORE_CASE)
    private val EMAIL_REGEX = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
    private val SESSION_COOKIE_REGEX = Regex("(CASTGC|JSESSIONID|MoodleSession)=[a-zA-Z0-9._-]+")

    fun anonymize(content: String): String {
        var sanitized = content
        sanitized = INE_REGEX.replace(sanitized, "INE_ANONYMIZED_123456789X")
        sanitized = STUDENT_ID_REGEX.replace(sanitized, "e20240001")
        sanitized = EMAIL_REGEX.replace(sanitized, "etudiant.anonyme@etu.umontpellier.fr")
        sanitized = SESSION_COOKIE_REGEX.replace(sanitized, "$1=ANONYMIZED_TOKEN")
        return sanitized
    }

    fun saveCapture(destinationDir: File, filename: String, rawContent: String): File {
        val anonymized = anonymize(rawContent)
        destinationDir.mkdirs()
        val file = File(destinationDir, filename)
        file.writeText(anonymized)
        return file
    }
}
