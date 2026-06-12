package com.locationtracker.app.ui.screen

import android.util.Log
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen() {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    var timelineItems by remember { mutableStateOf<List<TimelineItem>>(emptyList()) }

    DisposableEffect(currentUserId) {
        if (currentUserId.isEmpty()) return@DisposableEffect onDispose {}

        val ref = Firebase.database.reference
            .child("user_timelines")
            .child(currentUserId)
            .child(currentDate)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val items = mutableListOf<TimelineItem>()
                snapshot.children.forEach { child ->
                    val name = child.child("locationName").getValue(String::class.java) ?: return@forEach
                    val start = child.child("startTime").getValue(String::class.java) ?: ""
                    val end = child.child("endTime").getValue(String::class.java) ?: ""
                    val ts = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    
                    items.add(
                        TimelineItem(
                            locationName = name,
                            startTime = start,
                            endTime = end,
                            timestamp = ts
                        )
                    )
                }
                timelineItems = items.sortedByDescending { it.timestamp }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("TimelineScreen", "${error.message}")
            }
        }
        ref.addValueEventListener(listener)
        onDispose {
            ref.removeEventListener(listener)
        }
    }

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
        if (timelineItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "No activity yet",
                        color = Color.Gray,
                        fontSize = 16.sp
                    )
                    Text(
                        "Your locations will appear here",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(timelineItems.size) { idx ->
                    val item = timelineItems[idx]
                    val isLast = idx == timelineItems.size - 1
                    TimelineItemCard(item = item, showConnector = !isLast)
                }
            }
        }
    }
}

@Composable
private fun TimelineItemCard(item: TimelineItem, showConnector: Boolean) {
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
                    .background(Color(0xFF4FC3F7).copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = Color(0xFF4FC3F7),
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
                        text = item.locationName,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "${item.startTime} - ${item.endTime}",
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
