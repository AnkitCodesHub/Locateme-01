package com.locationtracker.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1520))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 10.dp)
        ) {
            Text(
                "Activity Timeline",
                color = Color(0xFFE8EFF6),
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()),
                color = Color(0xFF3D6080),
                fontSize = 12.sp
            )
        }

        // Summary bar
        if (entries.isNotEmpty()) {
            val totalDistKm = entries
                .filter { it.type == "TRAVEL" }
                .sumOf { it.distanceMeters.toDouble() } / 1000.0
            val placeCount = entries
                .count { it.type == "PLACE" }
            val totalMin = entries
                .filter { it.type == "TRAVEL" }
                .sumOf { e ->
                    val end = e.endTime ?: System.currentTimeMillis()
                    (end - e.startTime) / 60_000L
                }.toInt()
            
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 14.dp)
                    .fillMaxWidth()
                    .background(
                        Color(0xFF0F1E2E),
                        RoundedCornerShape(12.dp)
                    )
                    .border(
                        0.5.dp,
                        Color(0xFF1A3050),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stat 1 — total distance
                SumItem(
                    icon = "🚶",
                    value = "${"%.1f".format(totalDistKm)} km",
                    label = "distance"
                )
                Box(modifier = Modifier
                    .width(0.5.dp).height(32.dp)
                    .background(Color(0xFF1A3050)))
                // Stat 2 — places visited
                SumItem(
                    icon = "📍",
                    value = "$placeCount places",
                    label = "visited"
                )
                Box(modifier = Modifier
                    .width(0.5.dp).height(32.dp)
                    .background(Color(0xFF1A3050)))
                // Stat 3 — active minutes
                SumItem(
                    icon = "⏱",
                    value = "$totalMin min",
                    label = "active"
                )
            }
        }

        // Timeline list
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = entries,
                key = { it.id }
            ) { entry ->
                if (entry.type == "PLACE") {
                    PlaceCard(
                        entry = entry,
                        isLast = entries.last().id == entry.id
                    )
                } else {
                    TravelStrip(entry = entry)
                }
            }
        }
    }
}

@Composable
fun SumItem(
    icon: String,
    value: String,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    Color(0xFF0D2640),
                    RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, fontSize = 14.sp)
        }
        Column {
            Text(
                value,
                color = Color(0xFFD0DDE8),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                label,
                color = Color(0xFF3D6080),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun PlaceCard(
    entry: TimelineEntry,
    isLast: Boolean
) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
    ) {
        // Left: circle dot + connector line below
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(36.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        Color(0xFF0D2437),
                        CircleShape)
                    .border(1.dp,
                        Color(0xFF1A4060),
                        CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("📍", fontSize = 15.sp)
            }
            if (!isLast) {
                Box(modifier = Modifier
                    .width(1.5.dp)
                    .height(54.dp)
                    .background(Color(0xFF1A3050)))
            }
        }

        Spacer(Modifier.width(10.dp))

        // Right: place card
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 0.dp)
                .background(
                    Color(0xFF0D1E2E),
                    RoundedCornerShape(12.dp))
                .border(0.5.dp,
                    Color(0xFF1A3050),
                    RoundedCornerShape(12.dp))
                .padding(horizontal = 13.dp, vertical = 11.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.placeName.ifEmpty { "Unknown place" },
                    color = Color(0xFFD8E8F4),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Duration pill
                val endT = entry.endTime
                if (endT != null) {
                    val mins = ((endT - entry.startTime) / 60_000L).toInt()
                    if (mins > 0) {
                        val durLabel = if (mins >= 60) "${mins/60}h ${mins%60}m" else "${mins}m"
                        Text(
                            durLabel,
                            fontSize = 10.sp,
                            color = Color(0xFF4A90D9),
                            modifier = Modifier
                                .background(Color(0xFF0D2640), RoundedCornerShape(20.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Text("now",
                        fontSize = 10.sp,
                        color = Color(0xFF4A90D9),
                        modifier = Modifier
                            .background(Color(0xFF0D2640), RoundedCornerShape(20.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
                Text("⋮",
                    color = Color(0xFF2E5070),
                    fontSize = 18.sp,
                    modifier = Modifier.padding(start = 6.dp))
            }

            if (entry.placeAddress.isNotEmpty()) {
                Text(
                    entry.placeAddress,
                    color = Color(0xFF3D6080),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp, bottom = 7.dp)
                )
            }

            Box(modifier = Modifier
                .fillMaxWidth().height(0.5.dp)
                .background(Color(0xFF162A3E))
                .padding(bottom = 7.dp))
            Spacer(Modifier.height(7.dp))

            val endLabel = if (entry.endTime == null) "now" else formatTime(entry.endTime)
            Text(
                formatTime(entry.startTime) + "  –  " + endLabel,
                color = Color(0xFF2E5070),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun TravelStrip(
    entry: TimelineEntry
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: thin line + small dot + thin line
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(36.dp)
        ) {
            Box(modifier = Modifier
                .width(1.5.dp).height(10.dp)
                .background(Color(0xFF1A3050)))
            Box(modifier = Modifier
                .size(8.dp)
                .background(Color(0xFF1A3050), CircleShape))
            Box(modifier = Modifier
                .width(1.5.dp).height(10.dp)
                .background(Color(0xFF1A3050)))
        }

        Spacer(Modifier.width(10.dp))

        // Travel strip — slim, no card chrome
        Row(
            modifier = Modifier
                .weight(1f)
                .background(Color(0xFF0A1520), RoundedCornerShape(8.dp))
                .border(0.5.dp, Color(0xFF152535), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                entry.activityEmoji,
                fontSize = 13.sp)

            Column(modifier = Modifier.weight(1f)) {
                val mi = entry.distanceMeters / 1609.34f
                Text(
                    entry.activityLabel + "  ·  " + "${"%.1f".format(mi)} mi",
                    color = Color(0xFF5A7A96),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    formatTime(entry.startTime) + "  –  " +
                        (entry.endTime?.let { formatTime(it) } ?: "ongoing"),
                    color = Color(0xFF2E5070),
                    fontSize = 10.sp
                )
            }

            if (entry.speedKmh > 0f) {
                Text(
                    "${entry.speedKmh.toInt()} km/h",
                    fontSize = 10.sp,
                    color = Color(0xFF2EA06A),
                    modifier = Modifier
                        .background(Color(0xFF0A2216), RoundedCornerShape(20.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }
    }
}

fun formatTime(ts: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
