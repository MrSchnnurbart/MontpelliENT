package fr.montpellient.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color as ComposeColor
import kotlin.math.abs

/**
 * Couleurs par matière : attribuées de façon stable à partir du nom (même matière = même couleur
 * à chaque lancement), avec des teintes espacées pour rester lisibles en clair comme en sombre.
 */
object SubjectColors {

    private val hues = floatArrayOf(4f, 28f, 45f, 88f, 140f, 168f, 190f, 212f, 236f, 262f, 290f, 322f)

    fun hueFor(subject: String): Float {
        val key = normalize(subject)
        val index = abs(key.hashCode()) % hues.size
        return hues[index]
    }

    /** Couleur d'accent (pastille, bordure). */
    fun accent(subject: String, dark: Boolean): Color =
        ComposeColor.hsl(hueFor(subject), if (dark) 0.55f else 0.62f, if (dark) 0.68f else 0.45f)

    /** Fond de carte doux. */
    fun container(subject: String, dark: Boolean): Color =
        ComposeColor.hsl(hueFor(subject), if (dark) 0.30f else 0.70f, if (dark) 0.22f else 0.92f)

    fun onContainer(subject: String, dark: Boolean): Color =
        ComposeColor.hsl(hueFor(subject), if (dark) 0.50f else 0.60f, if (dark) 0.88f else 0.20f)

    /** « Examen – Réseaux » et « Réseaux » partagent la même couleur. */
    internal fun normalize(subject: String): String =
        subject.substringAfterLast("–").substringAfterLast("-").trim().lowercase()

    fun argb(subject: String, dark: Boolean): Int = accent(subject, dark).toArgb()
}
