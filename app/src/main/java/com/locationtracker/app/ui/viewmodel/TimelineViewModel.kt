package com.locationtracker.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.locationtracker.app.data.local.LocationDatabase
import com.locationtracker.app.data.local.TimelineEntryEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * ViewModel for the Timeline screen.
 *
 * Uses [AndroidViewModel] so it can access the application context for Room —
 * no Hilt required. The ViewModel exposes a reactive [entries] StateFlow that
 * re-emits automatically whenever the Room table changes (via DAO Flow query).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = LocationDatabase.getInstance(application).timelineDao()

    // ── Date navigation ───────────────────────────────────────────────────────

    /** Offset from today: 0 = today, -1 = yesterday, etc. */
    private val _dayOffset = MutableStateFlow(0)
    val dayOffset: StateFlow<Int> = _dayOffset.asStateFlow()

    /** "yyyy-MM-dd" key used to query Room — derived from _dayOffset. */
    val currentDateKey: StateFlow<String> = _dayOffset
        .map { offset -> dateKey(offset) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, dateKey(0))

    /** Human-readable label for the date nav bar. */
    val displayDate: StateFlow<String> = _dayOffset
        .map { offset ->
            when (offset) {
                0    -> "Today"
                -1   -> "Yesterday"
                else -> {
                    val cal = Calendar.getInstance()
                    cal.add(Calendar.DAY_OF_YEAR, offset)
                    SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(cal.time)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "Today")

    // ── Entries ───────────────────────────────────────────────────────────────

    /** Live list of timeline entries for the selected day. Emits on every DB change. */
    val entries: StateFlow<List<TimelineEntryEntity>> = currentDateKey
        .flatMapLatest { key -> dao.getEntriesForDate(key) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ── Navigation ────────────────────────────────────────────────────────────

    fun previousDay() { _dayOffset.value-- }

    fun nextDay() {
        if (_dayOffset.value < 0) _dayOffset.value++
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    companion object {
        fun dateKey(offsetDays: Int): String {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, offsetDays)
            return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
        }
    }
}
