package fr.montpellient.core.data.demo

import fr.montpellient.core.model.Affluence
import fr.montpellient.core.model.AffluenceLevel
import fr.montpellient.core.model.Announcement
import fr.montpellient.core.model.Assignment
import fr.montpellient.core.model.CampusEvent
import fr.montpellient.core.model.CourseModule
import fr.montpellient.core.model.CourseSection
import fr.montpellient.core.model.CourseType
import fr.montpellient.core.model.DailyMenu
import fr.montpellient.core.model.EventCategory
import fr.montpellient.core.model.EventStatus
import fr.montpellient.core.model.FolderRole
import fr.montpellient.core.model.GradeEntry
import fr.montpellient.core.model.GradeReport
import fr.montpellient.core.model.Library
import fr.montpellient.core.model.MailAddress
import fr.montpellient.core.model.MailAttachment
import fr.montpellient.core.model.MailFolder
import fr.montpellient.core.model.MailMessage
import fr.montpellient.core.model.Meal
import fr.montpellient.core.model.MenuStation
import fr.montpellient.core.model.ModuleType
import fr.montpellient.core.model.MoodleCourse
import fr.montpellient.core.model.MoodleGrade
import fr.montpellient.core.model.NewsItem
import fr.montpellient.core.model.Restaurant
import fr.montpellient.core.model.RestaurantType
import fr.montpellient.core.model.StudentProfile
import fr.montpellient.core.model.SubmissionStatus
import fr.montpellient.core.model.TimetableEvent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Données fictives mais réalistes pour le mode démo. Tout est calculé par rapport à [today]
 * pour que l'EDT, les devoirs et les menus soient toujours « d'actualité ».
 * Les noms de personnes sont inventés.
 */
class DemoData(private val today: LocalDate = LocalDate.now()) {

    private val now: LocalDateTime = LocalDateTime.of(today, LocalTime.now())
    private val monday: LocalDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    val profile = StudentProfile(
        displayName = "Camille Durand",
        email = "camille.durand@etu.umontpellier.fr",
        login = "cdurand",
        program = "Licence 3 Informatique",
        component = "Faculté des Sciences",
    )

    // ---------------------------------------------------------------- Emploi du temps

    private data class Slot(
        val day: DayOfWeek,
        val start: LocalTime,
        val minutes: Long,
        val subject: String,
        val type: CourseType,
        val room: String,
        val teacher: String,
        val group: String,
    )

    private val weeklyTemplate = listOf(
        Slot(DayOfWeek.MONDAY, LocalTime.of(8, 0), 90, "Génie logiciel", CourseType.CM, "Amphi 5.01", "M. Lefèvre", "L3 INFO"),
        Slot(DayOfWeek.MONDAY, LocalTime.of(9, 45), 90, "Bases de données avancées", CourseType.TD, "TD 36.201", "Mme Garcia", "L3 INFO G2"),
        Slot(DayOfWeek.MONDAY, LocalTime.of(13, 15), 180, "Projet tutoré", CourseType.TP, "TP 16.03", "M. Martin", "L3 INFO G2"),
        Slot(DayOfWeek.TUESDAY, LocalTime.of(8, 0), 90, "Réseaux", CourseType.CM, "Amphi 6.02", "Mme Bernard", "L3 INFO"),
        Slot(DayOfWeek.TUESDAY, LocalTime.of(9, 45), 90, "Réseaux", CourseType.TP, "TP 36.105", "M. Petit", "L3 INFO G2"),
        Slot(DayOfWeek.TUESDAY, LocalTime.of(14, 0), 120, "Anglais", CourseType.TD, "TD 2.12", "Ms. Smith", "L3 INFO G2"),
        Slot(DayOfWeek.WEDNESDAY, LocalTime.of(8, 0), 90, "Algorithmique avancée", CourseType.CM, "Amphi 5.01", "M. Roux", "L3 INFO"),
        Slot(DayOfWeek.WEDNESDAY, LocalTime.of(9, 45), 90, "Algorithmique avancée", CourseType.TD, "TD 36.203", "M. Roux", "L3 INFO G2"),
        Slot(DayOfWeek.THURSDAY, LocalTime.of(8, 0), 90, "Bases de données avancées", CourseType.CM, "Amphi 6.02", "Mme Garcia", "L3 INFO"),
        Slot(DayOfWeek.THURSDAY, LocalTime.of(10, 0), 120, "Systèmes d'exploitation", CourseType.TP, "TP 16.05", "M. Moreau", "L3 INFO G2"),
        Slot(DayOfWeek.THURSDAY, LocalTime.of(15, 0), 90, "Génie logiciel", CourseType.TD, "TD 36.201", "M. Lefèvre", "L3 INFO G2"),
        Slot(DayOfWeek.FRIDAY, LocalTime.of(9, 0), 90, "Systèmes d'exploitation", CourseType.CM, "Amphi 5.01", "M. Moreau", "L3 INFO"),
        Slot(DayOfWeek.FRIDAY, LocalTime.of(10, 45), 90, "Insertion professionnelle", CourseType.TD, "TD 2.08", "Mme Fontaine", "L3 INFO G2"),
    )

    /** Séances entre [from] et [toInclusive]. Une séance annulée et une modifiée cette semaine. */
    fun events(from: LocalDate, toInclusive: LocalDate): List<TimetableEvent> {
        val result = mutableListOf<TimetableEvent>()
        var day = from
        while (!day.isAfter(toInclusive)) {
            val weekIndex = ChronoUnit.WEEKS.between(monday, day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
            weeklyTemplate.filter { it.day == day.dayOfWeek }.forEachIndexed { index, slot ->
                val start = LocalDateTime.of(day, slot.start)
                var room = slot.room
                var status = EventStatus.NORMAL
                if (weekIndex == 0L && slot.day == DayOfWeek.TUESDAY && slot.type == CourseType.TP) {
                    status = EventStatus.CANCELLED
                }
                if (weekIndex == 0L && slot.day == DayOfWeek.THURSDAY && slot.type == CourseType.CM) {
                    room = "Amphi 2.01"
                    status = EventStatus.MODIFIED
                }
                result += TimetableEvent(
                    id = "ade-$day-$index",
                    subject = slot.subject,
                    type = slot.type,
                    start = start,
                    end = start.plusMinutes(slot.minutes),
                    rooms = listOf(room),
                    teachers = listOf(slot.teacher),
                    groups = listOf(slot.group),
                    description = if (status == EventStatus.MODIFIED) "Changement de salle (ancienne salle : ${slot.room})" else null,
                    status = status,
                )
            }
            // Partiel ajouté dans deux semaines
            if (ChronoUnit.DAYS.between(monday, day) == 16L) {
                val start = LocalDateTime.of(day, LocalTime.of(13, 30))
                result += TimetableEvent(
                    id = "ade-$day-exam",
                    subject = "Examen – Réseaux",
                    type = CourseType.EXAM,
                    start = start,
                    end = start.plusHours(2),
                    rooms = listOf("Bât. 36 – Salle d'examen 1"),
                    teachers = listOf("Mme Bernard"),
                    groups = listOf("L3 INFO"),
                    status = EventStatus.ADDED,
                )
            }
            day = day.plusDays(1)
        }
        return result.sortedBy { it.start }
    }

    // ---------------------------------------------------------------- Moodle

    val courses = listOf(
        MoodleCourse(101, "HAI501I – Génie logiciel", "HAI501I", "L3 Informatique", 62, now.minusHours(5), isFavourite = true),
        MoodleCourse(102, "HAI502I – Bases de données avancées", "HAI502I", "L3 Informatique", 48, now.minusDays(1)),
        MoodleCourse(103, "HAI503I – Réseaux", "HAI503I", "L3 Informatique", 71, now.minusDays(2), isFavourite = true),
        MoodleCourse(104, "HAI504I – Algorithmique avancée", "HAI504I", "L3 Informatique", 35, now.minusDays(3)),
        MoodleCourse(105, "HAI505I – Systèmes d'exploitation", "HAI505I", "L3 Informatique", 54, now.minusDays(1)),
        MoodleCourse(106, "HAI506I – Projet tutoré", "HAI506I", "L3 Informatique", 20, now.minusHours(20)),
        MoodleCourse(107, "HAL5A – Anglais scientifique B2", "HAL5A", "Langues", 80, now.minusDays(6)),
    )

    fun sections(courseId: Long): List<CourseSection> {
        val course = courses.firstOrNull { it.id == courseId } ?: return emptyList()
        val short = course.shortName
        return listOf(
            CourseSection(
                id = courseId * 10, name = "Informations générales",
                summary = "Modalités de contrôle des connaissances, planning et contacts.",
                modules = listOf(
                    CourseModule(courseId * 100 + 1, "Annonces", ModuleType.FORUM),
                    CourseModule(courseId * 100 + 2, "Syllabus $short", ModuleType.FILE, fileName = "syllabus_$short.pdf", fileSizeBytes = 182_000, isDownloaded = true),
                    CourseModule(courseId * 100 + 3, "Planning du semestre", ModuleType.PAGE),
                ),
            ),
            CourseSection(
                id = courseId * 10 + 1, name = "Chapitre 1 – Fondamentaux",
                modules = listOf(
                    CourseModule(courseId * 100 + 4, "Cours – Chapitre 1", ModuleType.FILE, fileName = "cm1.pdf", fileSizeBytes = 2_450_000, isDownloaded = true),
                    CourseModule(courseId * 100 + 5, "TD 1 – Énoncé", ModuleType.FILE, fileName = "td1.pdf", fileSizeBytes = 340_000),
                    CourseModule(courseId * 100 + 6, "Quiz d'auto-évaluation", ModuleType.QUIZ),
                ),
            ),
            CourseSection(
                id = courseId * 10 + 2, name = "Chapitre 2 – Approfondissement",
                modules = listOf(
                    CourseModule(courseId * 100 + 7, "Cours – Chapitre 2", ModuleType.FILE, fileName = "cm2.pdf", fileSizeBytes = 3_100_000),
                    CourseModule(courseId * 100 + 8, "Ressources complémentaires", ModuleType.URL, url = "https://moodle.umontpellier.fr"),
                    CourseModule(courseId * 100 + 9, "Dépôt – TP noté", ModuleType.ASSIGN),
                ),
            ),
        )
    }

    val assignments = listOf(
        Assignment(1, 106, "Projet tutoré", "Livrable 1 – Cahier des charges", now.plusDays(2).withHour(23).withMinute(59), SubmissionStatus.DRAFT),
        Assignment(2, 103, "Réseaux", "TP 3 – Analyse de trames Wireshark", now.plusDays(4).withHour(18).withMinute(0), SubmissionStatus.NOT_SUBMITTED),
        Assignment(3, 101, "Génie logiciel", "Diagrammes UML – étude de cas", now.plusDays(9).withHour(12).withMinute(0), SubmissionStatus.NOT_SUBMITTED),
        Assignment(4, 102, "Bases de données avancées", "Mini-projet PL/SQL", now.plusDays(15).withHour(23).withMinute(59), SubmissionStatus.NOT_SUBMITTED),
        Assignment(5, 105, "Systèmes d'exploitation", "TP 2 – Ordonnancement", now.minusDays(3), SubmissionStatus.SUBMITTED),
        Assignment(6, 104, "Algorithmique avancée", "DM 1 – Programmation dynamique", now.minusDays(10), SubmissionStatus.GRADED, grade = "16,5 / 20"),
    )

    val announcements = listOf(
        Announcement(1, 103, "Réseaux", "Salle du TP de mardi", "Mme Bernard", now.minusHours(3), "Le TP de mardi est annulé, il sera rattrapé la semaine prochaine."),
        Announcement(2, 106, "Projet tutoré", "Constitution des groupes", "M. Martin", now.minusDays(1), "Merci de renseigner vos groupes avant vendredi."),
        Announcement(3, 101, "Génie logiciel", "Corrigé du TD 2 en ligne", "M. Lefèvre", now.minusDays(2), "Le corrigé du TD 2 est disponible dans la section Chapitre 1."),
    )

    // ---------------------------------------------------------------- Notes

    val gradeReports = listOf(
        GradeReport(
            id = "2026-L3INFO", academicYear = "${today.year}-${today.year + 1}", program = "Licence 3 Informatique",
            entries = listOf(
                GradeEntry("s5", "HAS5I", "Semestre 5", 0, grade = null, result = null),
                GradeEntry("ue1", "HAI501I", "Génie logiciel", 1, grade = "14,25", credits = "6", isNew = true, publishedAt = now.minusHours(6)),
                GradeEntry("ue2", "HAI502I", "Bases de données avancées", 1, grade = null, credits = "6"),
                GradeEntry("ue3", "HAI503I", "Réseaux", 1, grade = null, credits = "6"),
            ),
        ),
        GradeReport(
            id = "2025-L2INFO", academicYear = "${today.year - 1}-${today.year}", program = "Licence 2 Informatique",
            entries = listOf(
                GradeEntry("y", "HLINF2", "Année L2 Informatique", 0, grade = "13,48", result = "ADM", session = "Session 1", credits = "60"),
                GradeEntry("s4", "HAS4I", "Semestre 4", 0, grade = "13,92", result = "ADM", credits = "30"),
                GradeEntry("s4ue1", "HAI401I", "Programmation orientée objet", 1, grade = "15,10", result = "ADM", credits = "6"),
                GradeEntry("s4ue1a", "HAI401IA", "Examen terminal", 2, grade = "14,50"),
                GradeEntry("s4ue1b", "HAI401IB", "Contrôle continu", 2, grade = "16,00"),
                GradeEntry("s4ue2", "HAI402I", "Théorie des langages", 1, grade = "11,75", result = "ADM", credits = "6"),
                GradeEntry("s4ue3", "HAI403I", "Architecture des ordinateurs", 1, grade = "12,40", result = "ADM", credits = "6"),
                GradeEntry("s4ue4", "HAI404I", "Probabilités et statistiques", 1, grade = "13,00", result = "ADM", credits = "6"),
                GradeEntry("s4ue5", "HAL4A", "Anglais B1+", 1, grade = "17,25", result = "ADM", credits = "6"),
                GradeEntry("s3", "HAS3I", "Semestre 3", 0, grade = "13,04", result = "ADM", credits = "30"),
                GradeEntry("s3ue1", "HAI301I", "Algorithmique et structures de données", 1, grade = "12,80", result = "ADM", credits = "6"),
                GradeEntry("s3ue2", "HAI302I", "Systèmes et réseaux – introduction", 1, grade = "9,60", result = "AJ", session = "Session 1", credits = "6"),
                GradeEntry("s3ue2r", "HAI302I", "Systèmes et réseaux – introduction", 2, grade = "11,20", result = "ADM", session = "Session 2"),
                GradeEntry("s3ue3", "HAI303I", "Bases de données", 1, grade = "14,30", result = "ADM", credits = "6"),
            ),
        ),
    )

    val moodleGrades = listOf(
        MoodleGrade(1, 104, "Algorithmique avancée", "DM 1 – Programmation dynamique", "16,50", "0–20", "Très bon travail, attention à la complexité.", now.minusDays(4)),
        MoodleGrade(2, 101, "Génie logiciel", "Quiz chapitre 1", "8,00", "0–10", null, now.minusDays(8)),
        MoodleGrade(3, 107, "Anglais scientifique B2", "Oral presentation", "15,00", "0–20", "Clear and well structured.", now.minusDays(12)),
    )

    // ---------------------------------------------------------------- Mails

    val folders = listOf(
        MailFolder("INBOX", "Boîte de réception", FolderRole.INBOX),
        MailFolder("Sent", "Envoyés", FolderRole.SENT),
        MailFolder("Drafts", "Brouillons", FolderRole.DRAFTS),
        MailFolder("Junk", "Indésirables", FolderRole.SPAM),
        MailFolder("Trash", "Corbeille", FolderRole.TRASH),
    )

    private val me = MailAddress("Camille Durand", profile.email)

    val messages: List<MailMessage> = listOf(
        mail("1", "Scolarité FdS", "scolarite-fds@umontpellier.fr", "Publication des résultats – Génie logiciel",
            "Bonjour,\n\nLes résultats de l'UE HAI501I sont désormais consultables dans votre ENT, rubrique « Mon dossier ».\n\nCordialement,\nLe service scolarité", now.minusHours(6), read = false),
        mail("2", "Thomas Martin", "thomas.martin@umontpellier.fr", "Projet tutoré – réunion jeudi",
            "Bonjour à tous,\n\nJe vous propose de faire un point d'avancement jeudi à 16h30 en salle 16.03. Pensez à apporter votre cahier des charges.\n\nT. Martin", now.minusHours(20), read = false, attachment = "planning_projet.pdf"),
        mail("3", "BU Sciences", "bu-sciences@umontpellier.fr", "Rappel : retour de document",
            "Le document « Réseaux informatiques, 6e éd. » est à rendre avant le ${today.plusDays(3)}.", now.minusDays(1), read = true),
        mail("4", "Service des sports (SUAPS)", "suaps@umontpellier.fr", "Inscriptions au tournoi de badminton",
            "Les inscriptions au tournoi inter-composantes sont ouvertes jusqu'à vendredi.", now.minusDays(2), read = true),
        mail("5", "Léa Robert", "lea.robert@etu.umontpellier.fr", "Notes du CM de réseaux",
            "Salut ! Je t'envoie mes notes du dernier CM, dis-moi s'il manque quelque chose.", now.minusDays(3), read = true, attachment = "cm_reseaux_notes.pdf"),
        mail("6", "DSIN – Université de Montpellier", "noreply-dsin@umontpellier.fr", "Maintenance programmée de l'ENT",
            "Une maintenance de l'ENT aura lieu samedi de 7h à 9h. Les services pourront être indisponibles.", now.minusDays(5), read = true),
    )

    private fun mail(
        id: String, name: String, email: String, subject: String, body: String,
        date: LocalDateTime, read: Boolean, attachment: String? = null,
    ) = MailMessage(
        id = id, folderId = "INBOX", from = MailAddress(name, email), to = listOf(me),
        subject = subject, preview = body.replace('\n', ' ').take(120), bodyText = body,
        date = date, isRead = read,
        attachments = attachment?.let { listOf(MailAttachment("$id-a", it, "application/pdf", 420_000)) } ?: emptyList(),
    )

    // ---------------------------------------------------------------- CROUS

    val restaurants = listOf(
        Restaurant("ru-triolet", "RU Triolet", RestaurantType.RU, "Campus Triolet", "Place Eugène Bataillon, 34090 Montpellier", 43.6318, 3.8616, "11h30 – 13h45", isOpenNow(11, 30, 13, 45), isFavourite = true),
        Restaurant("ru-richter", "RU Richter", RestaurantType.RU, "Campus Richter", "Rue Vendémiaire, 34000 Montpellier", 43.6037, 3.8961, "11h30 – 14h00", isOpenNow(11, 30, 14, 0)),
        Restaurant("ru-boutonnet", "RU Boutonnet", RestaurantType.RU, "Boutonnet", "2 rue Emile Duployé, 34090 Montpellier", 43.6197, 3.8687, "11h30 – 13h30 · 18h30 – 20h00", isOpenNow(11, 30, 13, 30)),
        Restaurant("ru-vert-bois", "RU Vert-Bois", RestaurantType.RU, "Vert-Bois", "Avenue du Val de Montferrand, 34090 Montpellier", 43.6369, 3.8586, "11h30 – 13h45", isOpenNow(11, 30, 13, 45)),
        Restaurant("cafet-triolet", "Cafétéria Triolet", RestaurantType.CAFETERIA, "Campus Triolet", "Bâtiment 5, Campus Triolet", 43.6325, 3.8640, "7h45 – 16h00", isOpenNow(7, 45, 16, 0)),
        Restaurant("brasserie-st-priest", "Brasserie Saint-Priest", RestaurantType.BRASSERIE, "Saint-Priest", "860 rue Saint-Priest, 34090 Montpellier", 43.6355, 3.8420, "11h30 – 14h00", isOpenNow(11, 30, 14, 0)),
    )

    private fun isOpenNow(h1: Int, m1: Int, h2: Int, m2: Int): Boolean {
        if (today.dayOfWeek == DayOfWeek.SATURDAY || today.dayOfWeek == DayOfWeek.SUNDAY) return false
        val t = now.toLocalTime()
        return !t.isBefore(LocalTime.of(h1, m1)) && t.isBefore(LocalTime.of(h2, m2))
    }

    private val mains = listOf(
        listOf("Poulet basquaise", "Riz pilaf", "Haricots verts"),
        listOf("Lasagnes végétariennes", "Salade verte"),
        listOf("Filet de colin, sauce citron", "Purée de patate douce"),
        listOf("Chili sin carne", "Riz complet"),
        listOf("Sauté de porc au caramel", "Nouilles sautées"),
        listOf("Couscous merguez", "Semoule", "Légumes du couscous"),
        listOf("Gratin de pâtes au chèvre", "Brocolis"),
    )

    fun menus(restaurantId: String): List<DailyMenu> {
        val offset = restaurants.indexOfFirst { it.id == restaurantId }.coerceAtLeast(0)
        return (0 until 5).map { d ->
            val date = today.plusDays(d.toLong())
            val i = (date.dayOfYear + offset) % mains.size
            val lunch = Meal(
                name = "Déjeuner",
                stations = listOf(
                    MenuStation("Entrées", listOf("Carottes râpées", "Taboulé", "Œuf mayonnaise")),
                    MenuStation("Plat du jour", mains[i]),
                    MenuStation("Végétarien", mains[(i + 3) % mains.size].take(1) + "Légumes de saison"),
                    MenuStation("Grill", listOf("Steak haché", "Frites")),
                    MenuStation("Desserts", listOf("Yaourt aux fruits", "Tarte aux pommes", "Fruit de saison")),
                ),
            )
            val meals = if (restaurantId == "ru-boutonnet") {
                listOf(lunch, Meal("Dîner", listOf(MenuStation("Plat du soir", mains[(i + 1) % mains.size]))))
            } else {
                listOf(lunch)
            }
            DailyMenu(restaurantId, date, meals)
        }
    }

    fun affluence(restaurantId: String): Affluence {
        val level = when (restaurantId) {
            "ru-triolet" -> AffluenceLevel.HIGH
            "ru-richter" -> AffluenceLevel.MEDIUM
            "cafet-triolet", "ru-vert-bois" -> AffluenceLevel.LOW
            else -> AffluenceLevel.UNKNOWN
        }
        val reports = if (level == AffluenceLevel.UNKNOWN) 0 else 3 + restaurantId.length % 9
        return Affluence(restaurantId, level, reports, if (reports > 0) now.minusMinutes(7) else null)
    }

    // ---------------------------------------------------------------- Campus

    val news = listOf(
        NewsItem("n1", "Semaine de l'engagement étudiant", "Rencontrez les associations de l'université et découvrez comment vous engager.", "umontpellier.fr", "https://www.umontpellier.fr", now.minusHours(4)),
        NewsItem("n2", "Nouveaux horaires étendus des BU en période d'examens", "Les bibliothèques universitaires ouvrent plus tard pendant les révisions.", "BU UM", "https://www.umontpellier.fr", now.minusDays(1)),
        NewsItem("n3", "Appel à projets : initiatives étudiantes", "Le FSDIE soutient vos projets associatifs, culturels et sportifs.", "Vie étudiante", "https://www.umontpellier.fr", now.minusDays(3)),
        NewsItem("n4", "Forum entreprises de la Faculté des Sciences", "Stages, alternances et premiers emplois : plus de 60 entreprises présentes.", "FdS", "https://sciences.edu.umontpellier.fr", now.minusDays(4)),
    )

    val events = listOf(
        CampusEvent("e1", "Soirée d'intégration", "BDE Sciences", "Soirée d'intégration des L1 à L3, navettes prévues depuis le campus.", now.plusDays(3).withHour(20).withMinute(0), now.plusDays(4).withHour(2).withMinute(0), "Le Rockstore, Montpellier", EventCategory.PARTY),
        CampusEvent("e2", "Hackathon 24 h", "Association Info'Sphère", "Équipes de 3 à 5, thème dévoilé le jour J. Repas offerts.", now.plusDays(8).withHour(9).withMinute(0), now.plusDays(9).withHour(9).withMinute(0), "Bâtiment 36, Campus Triolet", EventCategory.ASSOCIATION),
        CampusEvent("e3", "Conférence : l'IA et le climat", "Université de Montpellier", "Conférence grand public suivie d'un débat.", now.plusDays(5).withHour(18).withMinute(30), now.plusDays(5).withHour(20).withMinute(0), "Amphi 5.01, Campus Triolet", EventCategory.CONFERENCE),
        CampusEvent("e4", "Tournoi de badminton", "SUAPS", "Tournoi inter-composantes en double mixte.", now.plusDays(11).withHour(14).withMinute(0), null, "Gymnase universitaire Triolet", EventCategory.SPORT),
    )

    val libraries = listOf(
        Library("bu-sciences", "BU Sciences", "Campus Triolet", "8h00 – 22h00", true, 64, week("8h00 – 22h00", "9h00 – 18h00")),
        Library("bu-richter", "BU Richter (Droit-Éco-Gestion)", "Campus Richter", "8h00 – 21h00", true, 82, week("8h00 – 21h00", "9h00 – 17h00")),
        Library("bu-medecine", "BU Médecine Nord", "Arnaud de Villeneuve", "8h30 – 20h00", true, null, week("8h30 – 20h00", "Fermée")),
        Library("bu-staps", "BU STAPS", "Campus Veyrassi", "9h00 – 18h00", false, 15, week("9h00 – 18h00", "Fermée")),
    )

    private fun week(weekdays: String, saturday: String) = listOf(
        "Lundi" to weekdays, "Mardi" to weekdays, "Mercredi" to weekdays, "Jeudi" to weekdays,
        "Vendredi" to weekdays, "Samedi" to saturday, "Dimanche" to "Fermée",
    )
}
