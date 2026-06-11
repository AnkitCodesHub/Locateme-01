package com.locationtracker.app.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class TimelineEntry(
    val icon: ImageVector,
    val place: String,
    val timeRange: String,
    val date: String,
    val iconColor: Color
)

private val sampleTimeline = listOf(
    TimelineEntry(Icons.Filled.Home, "Home", "14:00 – 17:40", "Today", Color(0xFF4FC3F7)),
    TimelineEntry(Icons.Filled.FitnessCenter, "Gym", "17:00 – 19:00", "Today", Color(0xFF66BB6A)),
    TimelineEntry(Icons.Filled.Work, "Office", "09:00 – 13:30", "Yesterday", Color(0xFFFFA726)),
    TimelineEntry(Icons.Filled.ShoppingCart, "Mall", "15:00 – 17:00", "Yesterday", Color(0xFFAB47BC)),
    TimelineEntry(Icons.Filled.Home, "Home", "07:30 – 09:00", "2 days ago", Color(0xFF4FC3F7)),
    TimelineEntry(Icons.Filled.LocationOn, "Park", "18:00 – 20:00", "2 days ago", Color(0xFF26C6DA))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen() {
    Scaffold(
        containerColor = Color(0xFF0D1B2A),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Activity Timeline",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0D1B2A))
            )
        }
    ) { paddingValues ->
        // Pre-compute groups to avoid LazyListScope type-inference issues
        val grouped = sampleTimeline.groupBy { it.date }
        val groupKeys = grouped.keys.toList()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            groupKeys.forEach { date ->
                val entries = grouped[date] ?: return@forEach
                item(key = "header_$date") {
                    DateGroupHeader(date = date)
                }
                items(count = entries.size, key = { idx -> "entry_${date}_$idx" }) { idx ->
                    val entry = entries[idx]
                    val isLast = idx == entries.size - 1
                    TimelineItem(entry = entry, showConnector = !isLast)
                }
            }
            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun DateGroupHeader(date: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .height(1.dp)
                .weight(1f)
                .background(Color(0xFF1E2D3D))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1E2D3D)
        ) {
            Text(
                text = date,
                color = Color(0xFF8899AA),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .height(1.dp)
                .weight(1f)
                .background(Color(0xFF1E2D3D))
        )
    }
}

@Composable
private fun TimelineItem(entry: TimelineEntry, showConnector: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp)
    ) {
        // Left timeline column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(40.dp)
        ) {
            // Icon circle
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(entry.iconColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = entry.icon,
                    contentDescription = null,
                    tint = entry.iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Connector line
            if (showConnector) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(60.dp)
                        .background(Color(0xFF1E2D3D))
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Content
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E2D3D),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = if (showConnector) 0.dp else 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.place,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = entry.timeRange,
                        color = Color(0xFF8899AA),
                        fontSize = 13.sp
                    )
                }
                Icon(
                    Icons.Filled.History,
                    contentDescription = null,
                    tint = Color(0xFF334455),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
