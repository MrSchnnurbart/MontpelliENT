package fr.montpellient.connector.ade

import fr.montpellient.core.model.EventStatus
import fr.montpellient.core.model.TimetableEvent

sealed class TimetableDiffItem {
    data class Added(val event: TimetableEvent) : TimetableDiffItem()
    data class Cancelled(val event: TimetableEvent) : TimetableDiffItem()
    data class Moved(
        val oldEvent: TimetableEvent,
        val newEvent: TimetableEvent,
        val isRoomChanged: Boolean,
        val isTimeChanged: Boolean
    ) : TimetableDiffItem()
}

data class TimetableDiffResult(
    val updatedEvents: List<TimetableEvent>,
    val changes: List<TimetableDiffItem>
)

/**
 * Moteur de calcul différentiel des séances de l'emploi du temps.
 * Permet de marquer visuellement les séances modifiées, déplacées ou annulées,
 * et de générer les notifications locales à destination de l'étudiant.
 */
object TimetableDiffEngine {

    fun computeDiff(
        previousEvents: List<TimetableEvent>,
        newEvents: List<TimetableEvent>
    ): TimetableDiffResult {
        val prevMap = previousEvents.associateBy { it.id }
        val newMap = newEvents.associateBy { it.id }

        val changes = mutableListOf<TimetableDiffItem>()
        val resultList = mutableListOf<TimetableEvent>()

        for (newEvent in newEvents) {
            val old = prevMap[newEvent.id]
            if (old == null) {
                // Nouveau cours ajouté
                changes.add(TimetableDiffItem.Added(newEvent))
                resultList.add(newEvent.copy(status = EventStatus.ADDED))
            } else {
                val roomChanged = old.rooms != newEvent.rooms
                val timeChanged = old.start != newEvent.start || old.end != newEvent.end
                val wasCancelled = old.isCancelled
                val nowCancelled = newEvent.isCancelled

                if (!wasCancelled && nowCancelled) {
                    changes.add(TimetableDiffItem.Cancelled(newEvent))
                    resultList.add(newEvent.copy(status = EventStatus.CANCELLED))
                } else if (roomChanged || timeChanged) {
                    changes.add(
                        TimetableDiffItem.Moved(
                            oldEvent = old,
                            newEvent = newEvent,
                            isRoomChanged = roomChanged,
                            isTimeChanged = timeChanged
                        )
                    )
                    resultList.add(newEvent.copy(status = EventStatus.MODIFIED))
                } else {
                    resultList.add(newEvent.copy(status = old.status))
                }
            }
        }

        // Cours supprimés du nouvel export ADE (considérés annulés si dans le futur)
        val now = java.time.LocalDateTime.now()
        for (old in previousEvents) {
            if (!newMap.containsKey(old.id) && old.end.isAfter(now) && !old.isCancelled) {
                val cancelledEvent = old.copy(status = EventStatus.CANCELLED)
                changes.add(TimetableDiffItem.Cancelled(cancelledEvent))
                resultList.add(cancelledEvent)
            }
        }

        return TimetableDiffResult(
            updatedEvents = resultList.sortedBy { it.start },
            changes = changes
        )
    }
}
