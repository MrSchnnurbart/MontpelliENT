package fr.montpellient.core.data.search

import fr.montpellient.core.data.*
import fr.montpellient.core.data.demo.*
import fr.montpellient.core.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GlobalSearchEngineTest {

    private class TestContainer(demo: DemoData = DemoData()) : AppContainer {
        override val settings: SettingsRepository = object : SettingsRepository {
            override val settings: Flow<fr.montpellient.core.model.AppSettings> = flowOf(fr.montpellient.core.model.AppSettings())
            override suspend fun update(transform: (fr.montpellient.core.model.AppSettings) -> fr.montpellient.core.model.AppSettings) = Unit
        }
        override val profile: ProfileRepository = DemoProfileRepository(demo)
        override val timetable: TimetableRepository = DemoTimetableRepository(demo)
        override val courses: CoursesRepository = DemoCoursesRepository(demo)
        override val grades: GradesRepository = DemoGradesRepository(demo)
        override val mail: MailRepository = DemoMailRepository(demo)
        override val crous: CrousRepository = DemoCrousRepository(demo)
        override val campus: CampusRepository = DemoCampusRepository(demo)
        override val search: GlobalSearchEngine by lazy { GlobalSearchEngine(this) }
    }

    @Test
    fun testGlobalSearchEngineMatchesCoursesAndMails() = runBlocking {
        val container = TestContainer()
        val engine = container.search

        // Recherche d'un mot-clé présent dans les cours (ex: "Génie logiciel" ou "Réseaux")
        val courseResults = engine.search("Réseaux")
        assertTrue("Devrait trouver au moins un élément pour 'Réseaux'", courseResults.isNotEmpty())
        assertTrue("Devrait contenir une catégorie COURSE ou EVENT", courseResults.any { it.category == SearchCategory.COURSE || it.category == SearchCategory.EVENT })

        // Recherche d'un mot-clé présent dans les mails
        val mailResults = engine.search("Scolarité")
        assertTrue("Devrait trouver le mail de la Scolarité", mailResults.any { it.category == SearchCategory.MAIL })

        // Recherche vide ou trop courte
        val emptyResults = engine.search("a")
        assertTrue("Moins de 2 caractères renvoie une liste vide", emptyResults.isEmpty())
    }
}
