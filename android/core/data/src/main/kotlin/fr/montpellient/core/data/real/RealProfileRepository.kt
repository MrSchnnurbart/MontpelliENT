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
        loadProfile()
    }

    private fun loadProfile() {
        val creds = secureStorage.getCredentials()
        if (creds != null) {
            val login = creds.first
            _profile.value = StudentProfile(
                displayName = login,
                email = "$login@etu.umontpellier.fr",
                login = login,
                program = "Étudiant UM",
                component = "Université de Montpellier"
            )
        } else {
            _profile.value = null
        }
    }

    fun updateProfile(profile: StudentProfile) {
        _profile.value = profile
    }
}
