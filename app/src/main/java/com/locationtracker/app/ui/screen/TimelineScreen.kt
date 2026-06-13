package com.locationtracker.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.locationtracker.app.data.local.TimelineEntryEntity
import com.locationtracker.app.ui.viewmodel.TimelineViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Palette ───────────────────────────────────────────────────────────────────
private val BgScreen    = Color(0xFF0A1520)
private val BgCard      = Color(0xFF14202E)
private val BgTravel    = Color(0xFF0D1925)
private val BgSummary   = Color(0xFF111D28)
private val Accent      = Color(0xFF4FC3F7)
private val AccentGreen = Color(0xFF66BB6A)
private val White       = Color.White
private val SubText     = Color(0xFF8899AA)
private val MutedText   = Color(0xFF445566)
private val LineColor   = Color(0xFF1A2A3A)
private val TabSelected = Color(0xFF4FC3F7)
private val TabIdle     = Color(0xFF4A5E70)

// ── Screen ────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel = viewModel()
) {
    val entries   by viewModel.entries.collectAsState()
    val dayOffset by viewModel.dayOffset.collectAsState()
    val displayDate by viewModel.displayDate.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Day", "Trips", "Insights", "Places")

    Scaffold(
        containerColor = BgScreen,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Timeline",
                        color = White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgScreen)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            // ── Tabs: Day / Trips / Insights / Places ────────────────────
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = BgScreen,
                contentColor = TabSelected,
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        height = 2.dp,
                        color = TabSelected
                    )
                },
                divider = {
                    Divider(thickness = 1.dp, color = LineColor)
                }
            ) {
                tabs.forEachIndexed { idx, title ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        selectedContentColor = TabSelected,
                        unselectedContentColor = TabIdle
                    ) {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == idx) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
            }

            // ── Date navigation bar ──────────────────────────────────────
            DateNavBar(
                displayDate = displayDate,
                canGoForward = dayOffset < 0,
                onBack    = { viewModel.previousDay() },
                onForward = { viewModel.nextDay() }
            )

            // Content for selected tab
            when (selectedTab) {
                0 -> DayView(entries = entries)
                1 -> PlaceholderTab("Trips")
                2 -> PlaceholderTab("Insights")
                3 -> PlaceholderTab("Places")
            }
        }
    }
}

// ── Day view ──────────────────────────────────────────────────────────────────
@Composable
private fun DayView(entries: List<TimelineEntryEntity>) {
    if (entries.isEmpty()) {
        EmptyState()
        return
    }

    val travelEntries = entries.filter { it.type == "TRAVEL" }
    val placeEntries  = entries.filter { it.type == "PLACE" }
    val totalDistM    = travelEntries.sumOf { it.distanceMeters.toDouble() }.toFloat()
    val totalMin      = travelEntries.sumOf { it.durationMinutes }
    val topEmoji      = travelEntries.groupBy { it.activityEmoji }
        .maxByOrNull { it.value.size }?.key ?: "\uD83D\uDEB6"

    Column {
        // ── Summary bar ───────────────────────────────────────────────
        SummaryBar(
            emoji = topEmoji,
            totalDistM = totalDistM,
            totalMin = totalMin,
            placeCount = placeEntries.size
        )

        // ── Timeline list ─────────────────────────────────────────────
        LazyColumn(
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            itemsIndexed(entries) { idx, entry ->
                TimelineRow(entry = entry, isLast = idx == entries.size - 1)
            }
        }
    }
}

// ── Summary bar ───────────────────────────────────────────────────────────────
@Composable
private fun SummaryBar(
    emoji: String,
    totalDistM: Float,
    totalMin: Int,
    placeCount: Int
) {
    val distStr = if (totalDistM >= 1000f)
        "${"%.1f".format(totalDistM / 1000f)} km"
    else "${totalDistM.toInt()} m"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgSummary)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Transport icon
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 22.sp)
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Distance & time
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = distStr,
                color = White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Text(
                text = "${totalMin} min",
                color = SubText,
                fontSize = 12.sp
            )
        }

        // Vertical divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(36.dp)
                .background(LineColor)
        )

        Spacer(modifier = Modifier.width(14.dp))

        // Visits
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("\uD83D\uDCCD", fontSize = 18.sp)
            Text(
                text = "$placeCount visit${if (placeCount != 1) "s" else ""}",
                color = SubText,
                fontSize = 11.sp
            )
        }
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(LineColor))
}

// ── Single timeline row (PLACE or TRAVEL) ─────────────────────────────────────
@Composable
private fun TimelineRow(entry: TimelineEntryEntity, isLast: Boolean) {
    val isPlace  = entry.type == "PLACE"
    val iconText = if (isPlace) entry.placeIcon else entry.activityEmoji

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp)
    ) {
        // Left: circle + connector line
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(44.dp)
        ) {
            Spacer(modifier = Modifier.height(if (isPlace) 12.dp else 8.dp))

            Box(
                modifier = Modifier
                    .size(if (isPlace) 38.dp else 28.dp)
                    .clip(CircleShape)
                    .background(
                        if (isPlace) Accent.copy(alpha = 0.14f) else BgTravel
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(iconText, fontSize = if (isPlace) 18.sp else 14.sp)
            }

            if (!isLast) {
                val lineHeight = if (isPlace) 68.dp else 48.dp
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(lineHeight)
                        .background(LineColor)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Content
        if (isPlace) {
            PlaceContent(entry = entry, modifier = Modifier.weight(1f))
        } else {
            TravelContent(entry = entry, modifier = Modifier.weight(1f))
        }

        // ⋮ menu
        OverflowMenu()
    }
}

// ── PLACE content ─────────────────────────────────────────────────────────────
@Composable
private fun PlaceContent(entry: TimelineEntryEntity, modifier: Modifier) {
    Column(
        modifier = modifier.padding(top = 10.dp, bottom = 16.dp, end = 4.dp)
    ) {
        Text(
            text = entry.placeName.ifEmpty { "Unknown Place" },
            color = White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (entry.placeAddress.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = entry.placeAddress,
                color = SubText,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = formatTimeRange(entry.startTime, entry.endTime),
            color = AccentGreen,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ── TRAVEL content ────────────────────────────────────────────────────────────
@Composable
private fun TravelContent(entry: TimelineEntryEntity, modifier: Modifier) {
    val distStr = when {
        entry.distanceMeters >= 1000f -> "${"%.1f".format(entry.distanceMeters / 1000f)} km"
        entry.distanceMeters > 0f     -> "${entry.distanceMeters.toInt()} m"
        else                          -> ""
    }
    val parts = buildList {
        if (distStr.isNotEmpty()) add(distStr)
        if (entry.durationMinutes > 0) add("${entry.durationMinutes} min")
    }

    Row(
        modifier = modifier
            .padding(top = 6.dp, bottom = 12.dp, end = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(BgTravel)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.activityType
                    .lowercase()
                    .replaceFirstChar { it.uppercase() }
                    .ifEmpty { "Moving" },
                color = SubText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            if (parts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = parts.joinToString("  •  "),
                    color = MutedText,
                    fontSize = 12.sp
                )
            }
        }
        Text(
            text = formatTimeRange(entry.startTime, entry.endTime),
            color = MutedText,
            fontSize = 11.sp
        )
    }
}

// ── Three-dot overflow menu ───────────────────────────────────────────────────
@Composable
private fun OverflowMenu() {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = MutedText)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Edit") },
                onClick = { expanded = false }
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = { expanded = false }
            )
        }
    }
}

// ── Date nav bar ──────────────────────────────────────────────────────────────
@Composable
private fun DateNavBar(
    displayDate: String,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgScreen)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous day", tint = Accent)
        }
        Text(
            text = displayDate,
            color = White,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp
        )
        IconButton(onClick = onForward, enabled = canGoForward) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "Next day",
                tint = if (canGoForward) Accent else MutedText
            )
        }
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(LineColor))
}

// ── Placeholder tabs ──────────────────────────────────────────────────────────
@Composable
private fun PlaceholderTab(name: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("\uD83D\uDDFA\uFE0F", fontSize = 40.sp)
            Spacer(Modifier.height(12.dp))
            Text("$name coming soon", color = White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("Track your day first", color = SubText, fontSize = 13.sp)
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────
@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("\uD83D\uDDFA\uFE0F", fontSize = 48.sp)
            Spacer(Modifier.height(14.dp))
            Text("No activity recorded", color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("Move 50m+ to start your timeline", color = SubText, fontSize = 13.sp)
        }
    }
}

// ── Time helpers ──────────────────────────────────────────────────────────────
private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

private fun formatTimeRange(startMs: Long, endMs: Long?): String {
    val start = timeFmt.format(Date(startMs))
    return if (endMs != null && endMs != startMs)
        "$start – ${timeFmt.format(Date(endMs))}"
    else
        "From $start"
}
