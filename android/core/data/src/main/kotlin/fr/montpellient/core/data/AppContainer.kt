package fr.montpellient.core.data

import android.content.Context
import fr.montpellient.core.data.demo.DemoCampusRepository
import fr.montpellient.core.data.demo.DemoCoursesRepository
import fr.montpellient.core.data.demo.DemoCrousRepository
import fr.montpellient.core.data.demo.DemoData
import fr.montpellient.core.data.demo.DemoGradesRepository
import fr.montpellient.core.data.demo.DemoMailRepository
import fr.montpellient.core.data.demo.DemoProfileRepository
import fr.montpellient.core.data.demo.DemoTimetableRepository
import fr.montpellient.core.data.settings.DataStoreSettingsRepository
import fr.montpellient.core.data.settings.SettingsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Conteneur d'injection de dépendances manuel (léger, sans traitement d'annotations).
 * Au J1, les implémentations « démo » seront remplacées par les implémentations réelles
 * (Room + connecteurs) quand la session est authentifiée.
 */
interface AppContainer {
    val settings: SettingsRepository
    val profile: ProfileRepository
    val timetable: TimetableRepository
    val courses: CoursesRepository
    val grades: GradesRepository
    val mail: MailRepository
    val crous: CrousRepository
    val campus: CampusRepository
    val search: fr.montpellient.core.data.search.GlobalSearchEngine

    /** Rafraîchit toutes les sources en parallèle. */
    suspend fun refreshAll() = coroutineScope {
        listOf(timetable, courses, grades, mail, crous, campus)
            .map { async { runCatching { it.refresh() } } }
            .awaitAll()
        Unit
    }
}

/** Implémenté par l'`Application` pour exposer le conteneur aux écrans. */
interface AppContainerProvider {
    val container: AppContainer
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val demo by lazy { DemoData() }

    val secureStorage: fr.montpellient.core.security.SecureStorage by lazy {
        fr.montpellient.core.security.AndroidSecureStorage(context)
    }

    val casAuthClient: fr.montpellient.connector.cas.CasAuthClient by lazy {
        fr.montpellient.connector.cas.CasAuthClient(secureStorage)
    }

    val adeClient: fr.montpellient.connector.ade.AdeClient by lazy {
        fr.montpellient.connector.ade.AdeClient(casAuthClient, secureStorage)
    }

    val timetableLocalDataSource: fr.montpellient.core.database.TimetableLocalDataSource by lazy {
        fr.montpellient.core.database.EncryptedTimetableStore(context, secureStorage)
    }

    private val realProfileRepository by lazy {
        fr.montpellient.core.data.real.RealProfileRepository(secureStorage)
    }

    private val demoTimetableRepository by lazy {
        DemoTimetableRepository(demo)
    }

    private val realTimetableRepository by lazy {
        fr.montpellient.core.data.real.RealTimetableRepository(adeClient, timetableLocalDataSource)
    }

    override val settings: SettingsRepository = DataStoreSettingsRepository(context)

    override val profile: ProfileRepository by lazy {
        object : ProfileRepository {
            override val profile: kotlinx.coroutines.flow.Flow<fr.montpellient.core.model.StudentProfile?> =
                kotlinx.coroutines.flow.combine(
                    realProfileRepository.profile,
                    settings.settings
                ) { real, set ->
                    if (set.session == fr.montpellient.core.model.SessionMode.DEMO) {
                        demo.profile
                    } else {
                        real
                    }
                }

            override fun reloadProfile() {
                realProfileRepository.reloadProfile()
            }
        }
    }
    override val timetable: TimetableRepository by lazy {
        object : TimetableRepository {
            override fun events(from: java.time.LocalDate, toInclusive: java.time.LocalDate): kotlinx.coroutines.flow.Flow<List<fr.montpellient.core.model.TimetableEvent>> {
                return kotlinx.coroutines.flow.combine(
                    realTimetableRepository.events(from, toInclusive),
                    demoTimetableRepository.events(from, toInclusive),
                    settings.settings
                ) { real, demo, set ->
                    if (set.session == fr.montpellient.core.model.SessionMode.DEMO) {
                        demo
                    } else {
                        real
                    }
                }
            }

            override suspend fun refresh() {
                realTimetableRepository.refresh()
            }

            override suspend fun setCustomIcalUrl(url: String): Result<Unit> {
                return realTimetableRepository.setCustomIcalUrl(url)
            }

            override suspend fun getCustomIcalUrl(): String? {
                return realTimetableRepository.getCustomIcalUrl()
            }
        }
    }

    val moodleClient: fr.montpellient.connector.moodle.MoodleClient by lazy {
        fr.montpellient.connector.moodle.MoodleClient(casAuthClient, secureStorage)
    }

    val coursesLocalDataSource: fr.montpellient.core.database.CoursesLocalDataSource by lazy {
        fr.montpellient.core.database.EncryptedCoursesStore(context)
    }

    private val realCoursesRepository by lazy {
        fr.montpellient.core.data.real.RealCoursesRepository(moodleClient, coursesLocalDataSource, timetableLocalDataSource)
    }

    private val demoCoursesRepository by lazy {
        DemoCoursesRepository(demo)
    }

    override val courses: CoursesRepository by lazy {
        object : CoursesRepository {
            override val courses: kotlinx.coroutines.flow.Flow<List<fr.montpellient.core.model.MoodleCourse>> =
                kotlinx.coroutines.flow.combine(
                    realCoursesRepository.courses,
                    demoCoursesRepository.courses,
                    settings.settings
                ) { real, demo, set ->
                    if (set.session == fr.montpellient.core.model.SessionMode.DEMO) demo else real
                }

            override val assignments: kotlinx.coroutines.flow.Flow<List<fr.montpellient.core.model.Assignment>> =
                kotlinx.coroutines.flow.combine(
                    realCoursesRepository.assignments,
                    demoCoursesRepository.assignments,
                    settings.settings
                ) { real, demo, set ->
                    if (set.session == fr.montpellient.core.model.SessionMode.DEMO) demo else real
                }

            override val announcements: kotlinx.coroutines.flow.Flow<List<fr.montpellient.core.model.Announcement>> =
                kotlinx.coroutines.flow.combine(
                    demoCoursesRepository.announcements,
                    settings.settings
                ) { demo, set ->
                    if (set.session == fr.montpellient.core.model.SessionMode.DEMO) demo else emptyList()
                }

            override fun course(courseId: Long): kotlinx.coroutines.flow.Flow<fr.montpellient.core.model.MoodleCourse?> =
                realCoursesRepository.course(courseId)

            override fun sections(courseId: Long): kotlinx.coroutines.flow.Flow<List<fr.montpellient.core.model.CourseSection>> =
                kotlinx.coroutines.flow.combine(
                    realCoursesRepository.sections(courseId),
                    demoCoursesRepository.sections(courseId),
                    settings.settings
                ) { real, demo, set ->
                    if (set.session == fr.montpellient.core.model.SessionMode.DEMO) demo else real
                }

            override suspend fun refresh() {
                realCoursesRepository.refresh()
            }
        }
    }
    private val realMailRepository by lazy {
        fr.montpellient.core.data.real.RealMailRepository(secureStorage)
    }

    private val demoMailRepository by lazy {
        DemoMailRepository(demo)
    }

    override val mail: MailRepository by lazy {
        object : MailRepository {
            override val folders: kotlinx.coroutines.flow.Flow<List<fr.montpellient.core.model.MailFolder>> =
                kotlinx.coroutines.flow.combine(
                    realMailRepository.folders,
                    demoMailRepository.folders,
                    settings.settings
                ) { real, demo, set ->
                    if (set.session == fr.montpellient.core.model.SessionMode.DEMO) demo else real
                }

            override fun messages(folderId: String): kotlinx.coroutines.flow.Flow<List<fr.montpellient.core.model.MailMessage>> =
                kotlinx.coroutines.flow.combine(
                    realMailRepository.messages(folderId),
                    demoMailRepository.messages(folderId),
                    settings.settings
                ) { real, demo, set ->
                    if (set.session == fr.montpellient.core.model.SessionMode.DEMO) demo else real
                }

            override fun message(id: String): kotlinx.coroutines.flow.Flow<fr.montpellient.core.model.MailMessage?> =
                kotlinx.coroutines.flow.combine(
                    realMailRepository.message(id),
                    demoMailRepository.message(id),
                    settings.settings
                ) { real, demo, set ->
                    if (set.session == fr.montpellient.core.model.SessionMode.DEMO) demo else real
                }

            override val hasMore: kotlinx.coroutines.flow.Flow<Boolean> =
                realMailRepository.hasMore

            override suspend fun loadMore(): Boolean {
                return realMailRepository.loadMore()
            }

            override suspend fun setRead(id: String, read: Boolean) {
                realMailRepository.setRead(id, read)
            }

            override suspend fun setFlagged(id: String, flagged: Boolean) {
                realMailRepository.setFlagged(id, flagged)
            }

            override suspend fun loadFullBody(id: String): String? {
                return realMailRepository.loadFullBody(id)
            }

            override suspend fun refresh() {
                realMailRepository.refresh()
            }
        }
    }
    val gradesClient: fr.montpellient.connector.grades.GradesClient by lazy {
        fr.montpellient.connector.grades.GradesClient(casAuthClient, secureStorage)
    }

    val gradesLocalDataSource: fr.montpellient.core.database.GradesLocalDataSource by lazy {
        fr.montpellient.core.database.EncryptedGradesStore(context)
    }

    private val realGradesRepository by lazy {
        fr.montpellient.core.data.real.RealGradesRepository(gradesClient, gradesLocalDataSource, demo)
    }

    private val demoGradesRepository by lazy {
        DemoGradesRepository(demo)
    }

    override val grades: GradesRepository by lazy {
        object : GradesRepository {
            override val reports: kotlinx.coroutines.flow.Flow<List<fr.montpellient.core.model.GradeReport>> =
                kotlinx.coroutines.flow.combine(
                    realGradesRepository.reports,
                    demoGradesRepository.reports,
                    settings.settings
                ) { real, demo, set ->
                    if (set.session == fr.montpellient.core.model.SessionMode.DEMO) demo else real
                }

            override val moodleGrades: kotlinx.coroutines.flow.Flow<List<fr.montpellient.core.model.MoodleGrade>> =
                kotlinx.coroutines.flow.combine(
                    demoGradesRepository.moodleGrades,
                    settings.settings
                ) { demo, set ->
                    if (set.session == fr.montpellient.core.model.SessionMode.DEMO) demo else emptyList()
                }

            override suspend fun markAllSeen() {
                realGradesRepository.markAllSeen()
            }

            override suspend fun refresh() {
                realGradesRepository.refresh()
            }

            override suspend fun clear() {
                realGradesRepository.clear()
            }
        }
    }

    val realCrousRepository by lazy {
        fr.montpellient.core.data.real.RealCrousRepository(casAuthClient.okHttpClient, demo)
    }


    override val crous: CrousRepository by lazy { realCrousRepository }
    override val campus: CampusRepository by lazy { DemoCampusRepository(demo) }
    override val search: fr.montpellient.core.data.search.GlobalSearchEngine by lazy {
        fr.montpellient.core.data.search.GlobalSearchEngine(this)
    }
}


/** Récupère le conteneur depuis n'importe quel `Context`. */
val Context.appContainer: AppContainer
    get() = (applicationContext as AppContainerProvider).container
