package fr.montpellient.core.data.real

import fr.montpellient.core.data.ProfileRepository
import fr.montpellient.core.model.StudentProfile
import fr.montpellient.core.security.SecureStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class RealProfileRepository(
    private val secureStorage: SecureStorage
) : ProfileRepository {

    private val _profile = MutableStateFlow<StudentProfile?>(null)
    override val profile: Flow<StudentProfile?> = _profile.asStateFlow()

    init {
        reloadProfile()
    }

    override fun reloadProfile() {
        val creds = secureStorage.getCredentials()
        if (creds != null) {
            val login = creds.first
            val formattedName = formatStudentName(login)
            _profile.value = StudentProfile(
                displayName = formattedName,
                email = if (login.contains("@")) login else "$login@etu.umontpellier.fr",
                login = login,
                program = "Étudiant UM",
                component = "Université de Montpellier"
            )
        } else {
            _profile.value = null
        }
    }

    private fun formatStudentName(login: String): String {
        val clean = login.substringBefore("@")
        if (clean.contains(".")) {
            val parts = clean.split(".")
            return parts.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        }
        return clean.replaceFirstChar { it.uppercase() }
    }

    fun updateProfile(profile: StudentProfile) {
        _profile.value = profile
    }
}
