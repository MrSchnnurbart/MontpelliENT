package fr.montpellient.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FakeSecureStorage : SecureStorage {
    private val memory = mutableMapOf<String, String>()

    override fun saveCredentials(login: String, password: String) {
        memory["cas_login"] = login
        memory["cas_password"] = password
    }

    override fun getCredentials(): Pair<String, String>? {
        val l = memory["cas_login"] ?: return null
        val p = memory["cas_password"] ?: return null
        return Pair(l, p)
    }

    override fun clearCredentials() {
        memory.remove("cas_login")
        memory.remove("cas_password")
    }

    override fun saveSecret(key: String, value: String) {
        memory[key] = value
    }

    override fun getSecret(key: String): String? = memory[key]

    override fun removeSecret(key: String) {
        memory.remove(key)
    }

    override fun clearAll() {
        memory.clear()
    }
}

class FakeSecureStorageTest {
    @Test
    fun saveAndRetrieveCredentials() {
        val storage = FakeSecureStorage()
        assertNull(storage.getCredentials())

        storage.saveCredentials("e2024001", "SecretPass123!")
        val creds = storage.getCredentials()
        assertEquals("e2024001", creds?.first)
        assertEquals("SecretPass123!", creds?.second)

        storage.clearCredentials()
        assertNull(storage.getCredentials())
    }

    @Test
    fun saveAndRetrieveSecrets() {
        val storage = FakeSecureStorage()
        storage.saveSecret("ade_url", "https://edt.umontpellier.fr/export.ics")
        assertEquals("https://edt.umontpellier.fr/export.ics", storage.getSecret("ade_url"))

        storage.removeSecret("ade_url")
        assertNull(storage.getSecret("ade_url"))
    }
}
