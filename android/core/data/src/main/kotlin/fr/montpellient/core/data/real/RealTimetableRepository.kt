package fr.montpellient.core.data.real

import fr.montpellient.connector.ade.AdeClient
import fr.montpellient.connector.ade.AdeFetchResult
import fr.montpellient.connector.ade.TimetableDiffEngine
import fr.montpellient.core.data.TimetableRepository
import fr.montpellient.core.database.TimetableLocalDataSource
import fr.montpellient.core.model.TimetableEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Dépôt réel pour l'emploi du temps ADE Campus UM.
 * - Observe le cache local persistant ([TimetableLocalDataSource]) pour un affichage instantané et hors-ligne.
 * - [refresh] interroge le serveur ADE, applique le calcul de diff et met à jour la base locale.
 */
class RealTimetableRepository(
    private val adeClient: AdeClient,
    private val localDataSource: TimetableLocalDataSource
) : TimetableRepository {

    override fun events(from: LocalDate, toInclusive: LocalDate): Flow<List<TimetableEvent>> {
        return localDataSource.eventsFlow.map { list ->
            list.filter { event ->
                val date = event.start.toLocalDate()
                !date.isBefore(from) && !date.isAfter(toInclusive)
            }
        }
    }

    override suspend fun refresh() {
        val result = adeClient.fetchTimetable()
        if (result is AdeFetchResult.Success) {
            val previous = runCatching { localDataSource.eventsFlow.first() }.getOrDefault(emptyList())
            val diff = TimetableDiffEngine.computeDiff(previous, result.events)
            localDataSource.saveEvents(diff.updatedEvents)
        }
    }

    override suspend fun setCustomIcalUrl(url: String): Result<Unit> {
        val result = adeClient.fetchTimetable(customIcalUrl = url.trim())
        return if (result is AdeFetchResult.Success) {
            val previous = runCatching { localDataSource.eventsFlow.first() }.getOrDefault(emptyList())
            val diff = TimetableDiffEngine.computeDiff(previous, result.events)
            localDataSource.saveEvents(diff.updatedEvents)
            Result.success(Unit)
        } else {
            val msg = (result as? AdeFetchResult.Error)?.message ?: "Impossible de récupérer l'emploi du temps depuis cette URL"
            Result.failure(Exception(msg))
        }
    }

    override suspend fun getCustomIcalUrl(): String? {
        return adeClient.getSavedIcalUrl()
    }
}

