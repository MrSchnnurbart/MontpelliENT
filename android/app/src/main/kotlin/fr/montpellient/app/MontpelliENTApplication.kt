package fr.montpellient.app

import android.app.Application
import fr.montpellient.core.data.AppContainer
import fr.montpellient.core.data.AppContainerProvider
import fr.montpellient.core.data.DefaultAppContainer

class MontpelliENTApplication : Application(), AppContainerProvider {
    override val container: AppContainer by lazy {
        DefaultAppContainer(this)
    }
}
