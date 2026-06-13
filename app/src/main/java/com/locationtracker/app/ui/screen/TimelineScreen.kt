package com.locationtracker.app.ui.screen

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.locationtracker.app.data.model.TimelineItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ── Colour palette ────────────────────────────────────────────────────────────
private val BgDeep      = Color(0xFF0D1B2A)
private val BgCard      = Color(0xFF1A2738)
private val BgSummary   = Color(0xFF162030)
private val Accent      = Color(0xFF4FC3F7)
private val AccentGreen = Color(0xFF4CAF50)
private val TextPrimary = Color.White
private val TextSub     = Color(0xFF8899AA)
private val TextMuted   = Color(0xFF4A6070)
private val Divider     = Color(0xFF1E2F40)
private val TravelBg    = Color(0xFF0F1E2B)

// ── Helpers ───────────────────────────────────────────────────────────────────
private fun offsetDate(offsetDays: Int): String {
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, offsetDays)
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
}

private fun formatDisplayDate(offsetDays: Int): String {
    return when (offsetDays) {
        0    -> "Today"
        -1   -> "Yesterday"
        -2   -> "2 days ago"
        else -> {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, offsetDays)
            SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(cal.time)
        }
    }
}

private fun distanceDisplay(meters: Float): String =
    if (meters >= 1000f) "${"%.1f".format(meters / 1000f)} km"
    else "${meters.toInt()} m"

// ── Screen ────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen() {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    // Day offset: 0 = today, -1 = yesterday, etc.
    var dayOffset by remember { mutableIntStateOf(0) }
    var timelineItems by remember { mutableStateOf<List<TimelineItem>>(emptyList()) }

    DisposableEffect(currentUserId, dayOffset) {
        if (currentUserId.isEmpty()) return@DisposableEffect onDispose {}

        val date = offsetDate(dayOffset)
        val ref = Firebase.database.reference
            .child("user_timelines")
            .child(currentUserId)
            .child(date)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val raw = mutableListOf<TimelineItem>()
                snapshot.children.forEach { child ->
                    val type = child.child("type").getValue(String::class.java) ?: "PLACE"
                    val ts   = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    val start = child.child("startTime").getValue(String::class.java) ?: ""
                    val end   = child.child("endTime").getValue(String::class.java) ?: ""

                    if (type == "TRAVEL") {
                        val emoji    = child.child("activityEmoji").getValue(String::class.java) ?: "\uD83D\uDEB6"
                        val label    = child.child("activityLabel").getValue(String::class.java) ?: "Moving"
                        val actType  = child.child("activityType").getValue(String::class.java) ?: "WALKING"
                        val dist     = child.child("distanceMeters").getValue(Float::class.java) ?: 0f
                        val dur      = child.child("durationMinutes").getValue(Int::class.java) ?: 0
                        val spd      = child.child("speedKmh").getValue(Float::class.java) ?: 0f
                        raw.add(TimelineItem(
                            type = "TRAVEL",
                            activityType = actType, activityEmoji = emoji,
                            activityLabel = label, distanceMeters = dist,
                            durationMinutes = dur, speedKmh = spd,
                            startTime = start, endTime = end, timestamp = ts
                        ))
                    } else {
                        val name    = child.child("locationName").getValue(String::class.java) ?: return@forEach
                        val address = child.child("placeAddress").getValue(String::class.java) ?: ""
                        val icon    = child.child("placeIcon").getValue(String::class.java) ?: "\uD83D\uDCCD"
                        raw.add(TimelineItem(
                            type = "PLACE",
                            locationName = name, placeAddress = address, placeIcon = icon,
                            startTime = start, endTime = end, timestamp = ts
                        ))
                    }
                }
                // Dedup by timestamp + type and sort chronologically newest-first
                val seen = mutableSetOf<Long>()
                timelineItems = raw
                    .filter { seen.add(it.timestamp) }
                    .sortedByDescending { it.timestamp }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("TimelineScreen", error.message)
            }
        }
        ref.addValueEventListener(listener)
        onDispose { ref.removeEventListener(listener) }
    }

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            TopAppBar(
                title = {
                    Text("Timeline", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDeep)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Date navigation bar ───────────────────────────────────────
            DateNavigationBar(
                displayDate = formatDisplayDate(dayOffset),
                canGoForward = dayOffset < 0,
                onBack    = { dayOffset-- },
                onForward = { dayOffset++ }
            )

            // ── Summary bar ───────────────────────────────────────────────
            if (timelineItems.isNotEmpty()) {
                SummaryBar(items = timelineItems)
            }

            // ── Timeline list ─────────────────────────────────────────────
            if (timelineItems.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    items(timelineItems.size) { idx ->
                        val item   = timelineItems[idx]
                        val isLast = idx == timelineItems.size - 1
                        if (item.type == "PLACE") {
                            PlaceCard(item = item, showConnector = !isLast)
                        } else {
                            TravelCard(item = item, showConnector = !isLast)
                        }
                    }
                }
            }
        }
    }
}

// ── Date nav bar ──────────────────────────────────────────────────────────────
@Composable
private fun DateNavigationBar(
    displayDate: String,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgDeep)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous day", tint = Accent)
        }
        Text(
            text = displayDate,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
        IconButton(onClick = onForward, enabled = canGoForward) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "Next day",
                tint = if (canGoForward) Accent else TextMuted
            )
        }
    }
}

// ── Summary bar ───────────────────────────────────────────────────────────────
@Composable
private fun SummaryBar(items: List<TimelineItem>) {
    val travels = items.filter { it.type == "TRAVEL" }
    val places  = items.filter { it.type == "PLACE" }
    val totalDist = travels.sumOf { it.distanceMeters.toDouble() }.toFloat()
    val totalMin  = travels.sumOf { it.durationMinutes }
    val topEmoji  = travels.groupBy { it.activityEmoji }
        .maxByOrNull { it.value.size }?.key ?: "\uD83D\uDEB6"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgSummary)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(topEmoji, fontSize = 26.sp)
        Column {
            Text(
                text = "${distanceDisplay(totalDist)}  •  ${totalMin}min",
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Text(
                text = "${places.size} place${if (places.size != 1) "s" else ""} visited",
                color = TextSub,
                fontSize = 12.sp
            )
        }
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Divider))
}

// ── PLACE card ────────────────────────────────────────────────────────────────
@Composable
private fun PlaceCard(item: TimelineItem, showConnector: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp)
    ) {
        // Left rail
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(36.dp)
        ) {
            Spacer(modifier = Modifier.height(14.dp))
            // Emoji icon circle
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(item.placeIcon, fontSize = 18.sp)
            }
            if (showConnector) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(72.dp)
                        .background(Divider)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = if (showConnector) 0.dp else 8.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = BgCard),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.locationName,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.placeAddress.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.placeAddress,
                            color = TextSub,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = buildTimeRange(item.startTime, item.endTime),
                        color = AccentGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ── TRAVEL card ───────────────────────────────────────────────────────────────
@Composable
private fun TravelCard(item: TimelineItem, showConnector: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp)
    ) {
        // Left rail — thin line, smaller dot for travel
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(36.dp)
        ) {
            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(TravelBg),
                contentAlignment = Alignment.Center
            ) {
                Text(item.activityEmoji, fontSize = 14.sp)
            }
            if (showConnector) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(52.dp)
                        .background(Divider)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Travel pill — lighter background, smaller than place card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = if (showConnector) 0.dp else 8.dp, top = 6.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(TravelBg)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Mode label + dist • dur
                Column {
                    Text(
                        text = item.activityLabel,
                        color = TextSub,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val parts = buildList {
                        if (item.distanceMeters > 0) add(distanceDisplay(item.distanceMeters))
                        if (item.durationMinutes > 0) add("${item.durationMinutes} min")
                    }
                    Text(
                        text = parts.joinToString("  •  "),
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
                // Time range on the right
                Text(
                    text = buildTimeRange(item.startTime, item.endTime),
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────
@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("\uD83D\uDDFA\uFE0F", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(14.dp))
            Text("No activity recorded", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            Text("Move 50m+ to start tracking your day", color = TextSub, fontSize = 13.sp)
        }
    }
}

// ── Util ──────────────────────────────────────────────────────────────────────
private fun buildTimeRange(start: String, end: String): String {
    return if (end.isNullOrEmpty() || end == start) start
    else "$start – $end"
}
