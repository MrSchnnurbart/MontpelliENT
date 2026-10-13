package fr.montpellient.core.model

/** Mode de session de l'appli. */
enum class SessionMode { NONE, DEMO, AUTHENTICATED }

data class StudentProfile(
    val displayName: String,
    val email: String,
    val login: String,
    val program: String?,
    val component: String?,
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ContrastLevel { STANDARD, MEDIUM, HIGH }

/** Palettes prédéfinies (couleur source ARGB). [CUSTOM] utilise [AppSettings.customSeedColor]. */
enum class ThemePalette(val seed: Long) {
    UM(0xFF00467F),
    MEDITERRANEE(0xFF006A6A),
    GARRIGUE(0xFF5B6B2F),
    SOLEIL(0xFFB25A00),
    LAVANDE(0xFF6750A4),
    CORAIL(0xFFC2185B),
    CUSTOM(0xFF00467F),
}

enum class AppLanguage(val tag: String) { SYSTEM(""), FRENCH("fr"), ENGLISH("en") }

/** Destinations principales pouvant figurer dans la barre de navigation. */
enum class TopLevelDestination { TIMETABLE, COURSES, GRADES, MAIL, CROUS, CAMPUS }

/** Cartes du tableau de bord d'accueil. */
enum class HomeCard { NEXT_COURSE, TODAY, ASSIGNMENTS, GRADES, CROUS, MAIL, EVENTS }

data class AppSettings(
    val session: SessionMode = SessionMode.NONE,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val palette: ThemePalette = ThemePalette.UM,
    val customSeedColor: Long = ThemePalette.UM.seed,
    val amoledBlack: Boolean = false,
    val contrast: ContrastLevel = ContrastLevel.STANDARD,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val tabs: List<TopLevelDestination> = DEFAULT_TABS,
    val homeCards: List<HomeCard> = HomeCard.entries.toList(),
    val hiddenHomeCards: Set<HomeCard> = emptySet(),
    val initialSyncCompleted: Boolean = false,
    val notifTimetable: Boolean = true,
    val notifMail: Boolean = true,
    val notifGrades: Boolean = true,
    val notifAssignments: Boolean = true,
) {
    val seedColor: Long get() = if (palette == ThemePalette.CUSTOM) customSeedColor else palette.seed

    companion object {
        const val MAX_TABS = 4
        val DEFAULT_TABS = listOf(
            TopLevelDestination.TIMETABLE,
            TopLevelDestination.COURSES,
            TopLevelDestination.GRADES,
            TopLevelDestination.MAIL,
        )
    }
}
