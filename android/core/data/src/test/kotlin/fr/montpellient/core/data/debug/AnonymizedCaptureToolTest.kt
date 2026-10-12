package fr.montpellient.core.data.debug

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnonymizedCaptureToolTest {

    @Test
    fun testAnonymizeRemovesPersonalIdentifiers() {
        val rawHtml = """
            <div class="user-info">
                <span>Jean Dupont (e20234567)</span>
                <span>INE: 0123456789A</span>
                <span>Email: jean.dupont@etu.umontpellier.fr</span>
                <span>Cookie: CASTGC=TGT-12345-abcdef-cas</span>
            </div>
        """.trimIndent()

        val sanitized = AnonymizedCaptureTool.anonymize(rawHtml)

        assertFalse(sanitized.contains("e20234567"))
        assertFalse(sanitized.contains("0123456789A"))
        assertFalse(sanitized.contains("jean.dupont@etu.umontpellier.fr"))
        assertFalse(sanitized.contains("TGT-12345-abcdef-cas"))

        assertTrue(sanitized.contains("e20240001"))
        assertTrue(sanitized.contains("etudiant.anonyme@etu.umontpellier.fr"))
        assertTrue(sanitized.contains("CASTGC=ANONYMIZED_TOKEN"))
    }
}
