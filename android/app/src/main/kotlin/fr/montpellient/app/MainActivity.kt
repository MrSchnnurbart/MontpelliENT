package fr.montpellient.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Grade
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import fr.montpellient.core.data.appContainer
import fr.montpellient.core.designsystem.theme.MontpelliENTTheme
import fr.montpellient.core.designsystem.theme.ThemeConfig
import fr.montpellient.core.model.AppSettings
import fr.montpellient.core.model.SessionMode
import fr.montpellient.core.model.TopLevelDestination
import fr.montpellient.feature.campus.CampusRoute
import fr.montpellient.feature.courses.CoursesRoute
import fr.montpellient.feature.crous.CrousRoute
import fr.montpellient.feature.grades.GradesRoute
import fr.montpellient.feature.home.HomeRoute
import fr.montpellient.feature.mail.MailRoute
import fr.montpellient.feature.onboarding.OnboardingScreen
import fr.montpellient.feature.settings.SettingsRoute
import fr.montpellient.feature.timetable.TimetableRoute

enum class MainAppTab {
    HOME, TIMETABLE, COURSES, GRADES, MAIL, CROUS, CAMPUS, SETTINGS
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = appContainer

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
            val themeConfig = remember(settings) { ThemeConfig.from(settings) }

            MontpelliENTTheme(config = themeConfig) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when {
                        settings.session == SessionMode.NONE -> {
                            OnboardingScreen(
                                container = container,
                                onEnterApp = {}
                            )
                        }
                        settings.session == SessionMode.AUTHENTICATED && !settings.initialSyncCompleted -> {
                            val scope = rememberCoroutineScope()
                            fr.montpellient.feature.onboarding.InitialSyncScreen(
                                container = container,
                                onFinished = {
                                    scope.launch {
                                        container.settings.update { it.copy(initialSyncCompleted = true) }
                                    }
                                }
                            )
                        }
                        else -> {
                            MainScaffold(container = container, settings = settings)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MainScaffold(
    container: fr.montpellient.core.data.AppContainer,
    settings: AppSettings
) {
    var currentTab by remember { mutableStateOf(MainAppTab.HOME) }

    // Construit les items de la barre d'onglets dynamique
    val navItems = remember(settings.tabs) {
        val list = mutableListOf(MainAppTab.HOME)
        settings.tabs.forEach { dest ->
            when (dest) {
                TopLevelDestination.TIMETABLE -> list.add(MainAppTab.TIMETABLE)
                TopLevelDestination.COURSES -> list.add(MainAppTab.COURSES)
                TopLevelDestination.GRADES -> list.add(MainAppTab.GRADES)
                TopLevelDestination.MAIL -> list.add(MainAppTab.MAIL)
                TopLevelDestination.CROUS -> list.add(MainAppTab.CROUS)
                TopLevelDestination.CAMPUS -> list.add(MainAppTab.CAMPUS)
            }
        }
        list.add(MainAppTab.SETTINGS)
        list.distinct()
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            navItems.forEach { tab ->
                val (labelRes, icon) = tab.meta()
                item(
                    selected = currentTab == tab,
                    onClick = { currentTab = tab },
                    icon = { Icon(icon, contentDescription = null) },
                    label = { Text(stringResource(labelRes)) }
                )
            }
        }
    ) {
        when (currentTab) {
            MainAppTab.HOME -> HomeRoute(
                container = container,
                onNavigateTo = { dest ->
                    currentTab = when (dest) {
                        TopLevelDestination.TIMETABLE -> MainAppTab.TIMETABLE
                        TopLevelDestination.COURSES -> MainAppTab.COURSES
                        TopLevelDestination.GRADES -> MainAppTab.GRADES
                        TopLevelDestination.MAIL -> MainAppTab.MAIL
                        TopLevelDestination.CROUS -> MainAppTab.CROUS
                        TopLevelDestination.CAMPUS -> MainAppTab.CAMPUS
                    }
                }
            )
            MainAppTab.TIMETABLE -> TimetableRoute(container = container)
            MainAppTab.COURSES -> CoursesRoute(container = container)
            MainAppTab.GRADES -> GradesRoute(container = container)
            MainAppTab.MAIL -> MailRoute(container = container)
            MainAppTab.CROUS -> CrousRoute(container = container)
            MainAppTab.CAMPUS -> CampusRoute(container = container)
            MainAppTab.SETTINGS -> SettingsRoute(container = container)
        }
    }
}

private fun MainAppTab.meta(): Pair<Int, ImageVector> = when (this) {
    MainAppTab.HOME -> Pair(R.string.nav_home, Icons.Outlined.Home)
    MainAppTab.TIMETABLE -> Pair(R.string.nav_timetable, Icons.Outlined.CalendarMonth)
    MainAppTab.COURSES -> Pair(R.string.nav_courses, Icons.Outlined.Folder)
    MainAppTab.GRADES -> Pair(R.string.nav_grades, Icons.Outlined.Grade)
    MainAppTab.MAIL -> Pair(R.string.nav_mail, Icons.Outlined.Mail)
    MainAppTab.CROUS -> Pair(R.string.nav_crous, Icons.Outlined.Restaurant)
    MainAppTab.CAMPUS -> Pair(R.string.nav_campus, Icons.Outlined.LocalLibrary)
    MainAppTab.SETTINGS -> Pair(R.string.nav_settings, Icons.Outlined.Settings)
}
