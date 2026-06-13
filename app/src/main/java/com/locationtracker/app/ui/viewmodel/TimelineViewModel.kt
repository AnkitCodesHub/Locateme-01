package com.locationtracker.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.locationtracker.app.data.local.LocationDatabase
import com.locationtracker.app.data.local.TimelineEntry
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
 * Uses [AndroidViewModel] so it can access the application context for Room.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = LocationDatabase.getInstance(application).timelineDao()

    private val _selectedDate = MutableStateFlow(getTodayLabel())
    val selectedDate = _selectedDate.asStateFlow()

    private val _currentDateKey = MutableStateFlow(getTodayKey())

    val entries: StateFlow<List<TimelineEntry>> = _currentDateKey
        .flatMapLatest { date -> dao.getEntriesForDate(date) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun previousDay() {
        shiftDay(-1)
    }

    fun nextDay() {
        shiftDay(1)
    }

    private fun shiftDay(delta: Int) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        cal.time = sdf.parse(_currentDateKey.value) ?: Date()
        cal.add(Calendar.DAY_OF_YEAR, delta)
        _currentDateKey.value = sdf.format(cal.time)
        _selectedDate.value = getLabel(cal.time)
    }

    private fun getTodayKey() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private fun getTodayLabel() = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date())

    private fun getLabel(date: Date): String {
        val cal = Calendar.getInstance()
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance()
        yesterday.add(Calendar.DAY_OF_YEAR, -1)
        cal.time = date
        return when {
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "Today"
            cal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) -> "Yesterday"
            else -> SimpleDateFormat("MMMM d", Locale.getDefault()).format(date)
        }
    }
}
