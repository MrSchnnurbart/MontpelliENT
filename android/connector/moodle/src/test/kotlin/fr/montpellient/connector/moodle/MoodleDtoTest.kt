package fr.montpellient.connector.moodle

import fr.montpellient.core.model.ModuleType
import fr.montpellient.core.model.SubmissionStatus
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MoodleDtoTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    @Test
    fun testParseSiteInfo() {
        val raw = """
            {
                "sitename": "Moodle Université de Montpellier",
                "username": "e2024001",
                "firstname": "Alexandre",
                "lastname": "Dupont",
                "fullname": "Alexandre Dupont",
                "lang": "fr",
                "userid": 12345,
                "userpictureurl": "https://moodle.umontpellier.fr/user/pix.php/12345/f1.jpg",
                "functions": [
                    { "name": "core_enrol_get_users_courses", "version": "2023042400" },
                    { "name": "mod_assign_get_assignments", "version": "2023042400" }
                ]
            }
        """.trimIndent()

        val parsed = json.decodeFromString<MoodleSiteInfoResponse>(raw)
        assertEquals("Alexandre Dupont", parsed.fullName)
        assertEquals(12345L, parsed.userId)
        assertEquals(2, parsed.functions.size)
    }

    @Test
    fun testParseEnrolledCourses() {
        val raw = """
            [
                {
                    "id": 101,
                    "shortname": "GL-L3",
                    "fullname": "Génie Logiciel",
                    "category": 12,
                    "progress": 75.5,
                    "lastaccess": 1728639600,
                    "isfavourite": true
                },
                {
                    "id": 102,
                    "shortname": "BDD-L3",
                    "fullname": "Bases de Données Avancées",
                    "category": 12,
                    "progress": 40.0,
                    "lastaccess": 1728553200,
                    "isfavourite": false
                }
            ]
        """.trimIndent()

        val courses = json.decodeFromString<List<MoodleEnrolledCourseDto>>(raw)
        assertEquals(2, courses.size)
        assertEquals(101L, courses[0].id)
        assertEquals("Génie Logiciel", courses[0].fullName)
        assertEquals(75.5, courses[0].progress ?: 0.0, 0.01)
        assertEquals(true, courses[0].isFavourite)
    }

    @Test
    fun testParseAssignments() {
        val raw = """
            {
                "courses": [
                    {
                        "id": 101,
                        "fullname": "Génie Logiciel",
                        "assignments": [
                            {
                                "id": 501,
                                "cmid": 1200,
                                "course": 101,
                                "name": "Livrable 1 - Cahier des charges",
                                "duedate": 1729000000,
                                "intro": "<p>Déposez votre rapport en PDF.</p>"
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        val response = json.decodeFromString<MoodleAssignmentsResponse>(raw)
        assertEquals(1, response.courses.size)
        val assign = response.courses[0].assignments[0]
        assertEquals(501L, assign.id)
        assertEquals("Livrable 1 - Cahier des charges", assign.name)
        assertEquals(1729000000L, assign.dueDate)
    }
}
