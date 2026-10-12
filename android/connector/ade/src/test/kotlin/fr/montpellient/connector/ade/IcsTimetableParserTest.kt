package fr.montpellient.connector.ade

import fr.montpellient.core.model.CourseType
import fr.montpellient.core.model.EventStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class IcsTimetableParserTest {

    @Test
    fun parseSampleAdeIcs() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//ADE Campus//FR
            BEGIN:VEVENT
            UID:ADE601245@umontpellier.fr
            DTSTART:20261012T080000Z
            DTEND:20261012T100000Z
            SUMMARY:CM - Algorithmique Avancée
            LOCATION:Amphi Dumontet\, Bât 7
            DESCRIPTION:Matière : Algorithmique Avancée\nIntervenant : M. Martin\nGroupe : L3 Info G1
            END:VEVENT
            BEGIN:VEVENT
            UID:ADE601246@umontpellier.fr
            DTSTART:20261012T101500Z
            DTEND:20261012T121500Z
            SUMMARY:TD - Bases de Données (ANNULÉ)
            LOCATION:TD 16.02
            DESCRIPTION:Matière : Bases de Données\nIntervenant : Mme Dupuis\nGroupe : L3 Info G1
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val events = IcsTimetableParser.parse(ics)
        assertEquals(2, events.size)

        val first = events[0]
        assertEquals("ADE601245@umontpellier.fr", first.id)
        assertEquals("Algorithmique Avancée", first.subject)
        assertEquals(CourseType.CM, first.type)
        assertEquals(listOf("Amphi Dumontet", "Bât 7"), first.rooms)
        assertEquals(EventStatus.NORMAL, first.status)

        val second = events[1]
        assertEquals("Bases de Données (ANNULÉ)", second.subject)
        assertEquals(CourseType.TD, second.type)
        assertEquals(EventStatus.CANCELLED, second.status)
        assertTrue(second.isCancelled)
    }

    @Test
    fun testDiffEngineDetection() {
        val now = LocalDateTime.of(2026, 10, 12, 8, 0)
        val initialEvents = listOf(
            fr.montpellient.core.model.TimetableEvent(
                id = "1",
                subject = "Physique",
                type = CourseType.CM,
                start = now,
                end = now.plusHours(2),
                rooms = listOf("Amphi A")
            )
        )

        // Salle modifiée pour la séance 1
        val updatedEvents = listOf(
            fr.montpellient.core.model.TimetableEvent(
                id = "1",
                subject = "Physique",
                type = CourseType.CM,
                start = now,
                end = now.plusHours(2),
                rooms = listOf("Amphi B") // Changement de salle
            ),
            fr.montpellient.core.model.TimetableEvent(
                id = "2",
                subject = "Chimie",
                type = CourseType.TD,
                start = now.plusHours(3),
                end = now.plusHours(5),
                rooms = listOf("Salle 12") // Nouveau cours ajouté
            )
        )

        val diff = TimetableDiffEngine.computeDiff(initialEvents, updatedEvents)
        assertEquals(2, diff.changes.size)

        val moved = diff.changes.filterIsInstance<TimetableDiffItem.Moved>().firstOrNull()
        assertTrue(moved != null)
        assertTrue(moved?.isRoomChanged == true)

        val added = diff.changes.filterIsInstance<TimetableDiffItem.Added>().firstOrNull()
        assertTrue(added != null)
        assertEquals("Chimie", added?.event?.subject)
    }
}
