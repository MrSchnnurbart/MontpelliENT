package fr.montpellient.feature.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.montpellient.core.data.AppContainer
import fr.montpellient.core.model.TimetableEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class TimetableViewModel(
    private val container: AppContainer,
    val today: LocalDate = LocalDate.now(),
) : ViewModel() {

    /** Fenêtre chargée : de 8 semaines en arrière à 20 semaines en avant. */
    val windowStart: LocalDate = today.minusWeeks(8)
    val windowEnd: LocalDate = today.plusWeeks(20)

    val eventsByDay: StateFlow<Map<LocalDate, List<TimetableEvent>>> =
        container.timetable.events(windowStart, windowEnd)
            .map { events -> events.groupBy { it.start.toLocalDate() } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            runCatching { container.timetable.refresh() }
            _isRefreshing.value = false
        }
    }

    suspend fun importCustomIcalUrl(url: String): Result<Unit> {
        _isRefreshing.value = true
        val res = container.timetable.setCustomIcalUrl(url)
        _isRefreshing.value = false
        return res
    }
}

