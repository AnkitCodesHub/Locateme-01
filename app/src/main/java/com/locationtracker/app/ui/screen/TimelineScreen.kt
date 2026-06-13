package com.locationtracker.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.locationtracker.app.data.local.TimelineEntry
import com.locationtracker.app.ui.viewmodel.TimelineViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel = viewModel()
) {
    val entries by viewModel.entries.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Tab row: Day / Trips / Insights / Places
        TabRow(
            selectedTabIndex = 0,
            containerColor = Color.Black,
            contentColor = Color(0xFF4FC3F7)
        ) {
            listOf("Day", "Trips", "Insights", "Places")
                .forEachIndexed { index, title ->
                    Tab(
                        selected = index == 0,
                        onClick = {},
                        text = {
                            Text(
                                title,
                                color = if (index == 0)
                                    Color(0xFF4FC3F7)
                                else Color.Gray
                            )
                        }
                    )
                }
        }

        // Date navigation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.previousDay() }) {
                Text("‹", fontSize = 24.sp, color = Color.White)
            }
            Text(
                text = selectedDate,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            IconButton(onClick = { viewModel.nextDay() }) {
                Text("›", fontSize = 24.sp, color = Color.White)
            }
        }

        // Day summary bar
        val travelEntries = entries.filter { it.type == "TRAVEL" }
        val placeEntries = entries.filter { it.type == "PLACE" }
        val totalDist = travelEntries.sumOf { it.distanceMeters.toDouble() }
        val totalMin = travelEntries.sumOf { it.durationMinutes.toDouble() }

        if (entries.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(Color(0xFF111111), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                // Transport summary
                val mainMode = travelEntries
                    .groupBy { it.activityType }
                    .maxByOrNull { it.value.size }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        mainMode?.value?.first()?.activityEmoji ?: "🚶",
                        fontSize = 18.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            "${"%.1f".format(totalDist / 1609.34)} mi",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${totalMin.toInt()} min",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }
                // Divider
                Divider(
                    modifier = Modifier
                        .height(40.dp)
                        .width(1.dp),
                    color = Color(0xFF333333)
                )
                // Visits count
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📍", fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${placeEntries.size} visits",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Timeline list
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(entries) { index, entry ->
                TimelineRow(
                    entry = entry,
                    isLast = index == entries.size - 1
                )
            }
        }
    }
}

@Composable
fun TimelineRow(
    entry: TimelineEntry,
    isLast: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Left: vertical line + icon
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(48.dp)
        ) {
            // Circle icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFF1E2A3A), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (entry.type == "PLACE")
                        entry.placeIcon
                    else entry.activityEmoji,
                    fontSize = 16.sp
                )
            }
            // Vertical connector line
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(60.dp)
                        .background(Color(0xFF2A3A4A))
                )
            }
        }

        // Right: entry content
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, bottom = 16.dp, top = 4.dp)
        ) {
            if (entry.type == "PLACE") {
                // Place entry
                Text(
                    text = entry.placeName,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (entry.placeAddress.isNotEmpty()) {
                    Text(
                        text = entry.placeAddress,
                        color = Color.Gray,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = formatTimeRange(entry.startTime, entry.endTime),
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            } else {
                // Travel entry
                Text(
                    text = entry.activityType
                        .lowercase()
                        .replaceFirstChar { it.uppercase() },
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                val distMi = entry.distanceMeters / 1609.34f
                Text(
                    text = "${"%.1f".format(distMi)}" +
                            " mi · " +
                            "${entry.durationMinutes} min",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Text(
                    text = formatTimeRange(entry.startTime, entry.endTime),
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }

        // Three dot menu
        IconButton(onClick = {}) {
            Text("⋮", color = Color.Gray, fontSize = 18.sp)
        }
    }
}

fun formatTimeRange(start: Long, end: Long?): String {
    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    return if (end != null)
        "${fmt.format(Date(start))} - " + fmt.format(Date(end))
    else
        "Arrived at ${fmt.format(Date(start))}"
}
