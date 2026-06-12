package com.locationtracker.app.ui.screen

import android.location.Geocoder
import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import com.locationtracker.app.data.model.TimelineItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.locationtracker.app.ui.viewmodel.FriendViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendProfileScreen(
    friendId: String,
    onNavigateBack: () -> Unit,
    friendViewModel: FriendViewModel = viewModel()
) {
    val selectedFriend by friendViewModel.selectedFriend.collectAsStateWithLifecycle()
    val isFriendLive by friendViewModel.isFriendLive.collectAsStateWithLifecycle()
    val isLoading by friendViewModel.isLoading.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var liveLocationCoords by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var liveAddress by remember { mutableStateOf<String?>(null) }
    var liveTimestamp by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(friendId) {
        friendViewModel.loadFriendProfile(friendId)
    }

    DisposableEffect(friendId) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId == null) return@DisposableEffect onDispose {}

        val ref = Firebase.database.reference.child("active_shares").child(currentUserId).child(friendId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists() && snapshot.child("isSharing").getValue(Boolean::class.java) == true) {
                    val lat = snapshot.child("latitude").getValue(Double::class.java) ?: 0.0
                    val lng = snapshot.child("longitude").getValue(Double::class.java) ?: 0.0
                    liveLocationCoords = Pair(lat, lng)
                    liveTimestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()
                } else {
                    liveLocationCoords = null
                    liveAddress = null
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        onDispose { ref.removeEventListener(listener) }
    }

    LaunchedEffect(liveLocationCoords) {
        val coords = liveLocationCoords
        if (coords != null) {
            withContext(Dispatchers.IO) {
                try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val addresses = geocoder.getFromLocation(coords.first, coords.second, 1)
                    val addr = addresses?.firstOrNull()?.let { 
                        it.featureName ?: it.thoroughfare ?: it.subLocality ?: "Current Location"
                    } ?: "Navigating..."
                    liveAddress = addr
                } catch (e: Exception) {
                    liveAddress = "Navigating..."
                }
            }
        }
    }

    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    var recentActivity by remember { mutableStateOf<List<TimelineItem>>(emptyList()) }
    var isLoadingActivity by remember { mutableStateOf(true) }

    DisposableEffect(friendId) {
        if (currentUserId.isEmpty() || friendId.isEmpty()) {
            isLoadingActivity = false
            return@DisposableEffect onDispose {}
        }
        
        val ref = Firebase.database.reference
            .child("friend_timelines")
            .child(currentUserId)
            .child(friendId)
            .child(currentDate)
        
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                isLoadingActivity = false
                val items = mutableListOf<TimelineItem>()
                snapshot.children.forEach { child ->
                    val name = child.child("locationName").getValue(String::class.java) ?: return@forEach
                    val start = child.child("startTime").getValue(String::class.java) ?: ""
                    val end = child.child("endTime").getValue(String::class.java) ?: ""
                    val ts = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    items.add(TimelineItem(
                        locationName = name,
                        startTime = start,
                        endTime = end,
                        timestamp = ts
                    ))
                }
                recentActivity = items.sortedByDescending { it.timestamp }
                Log.d("FriendProfile", "Loaded ${items.size} activities for $friendId")
            }
            override fun onCancelled(error: DatabaseError) {
                isLoadingActivity = false
                Log.e("FriendProfile", "Failed: ${error.message}")
            }
        }
        ref.addValueEventListener(listener)
        onDispose { ref.removeEventListener(listener) }
    }

    Scaffold(
        containerColor = Color(0xFF0D1B2A),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Friend Profile",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0D1B2A)
                )
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF4FC3F7))
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // ── Profile Section ──────────────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E2D3D),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Circular avatar with live ring
                    LiveAvatarCircle(
                        initial = selectedFriend?.displayName?.firstOrNull()?.uppercaseChar()
                            ?.toString() ?: "?",
                        isLive = isFriendLive
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedFriend?.displayName ?: "Unknown",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = selectedFriend?.email ?: "",
                            color = Color(0xFF8899AA),
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // Live Sharing Badge
                        LiveSharingBadge(isLive = isFriendLive)
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Recent Activity Section ───────────────────────────────────────
            Text(
                text = "Recent Activity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            when {
                isLoadingActivity -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(0xFF4FC3F7))
                    }
                }
                recentActivity.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "No recent activity",
                                color = Color.Gray,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Activity will appear when your friend is active",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                    ) {
                        items(recentActivity) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                // Timeline dot
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF4FC3F7).copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = Color(0xFF4FC3F7),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                // Activity details
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(top = 4.dp)
                                ) {
                                    Text(
                                        text = item.locationName,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${item.startTime} - ${item.endTime}",
                                        fontSize = 13.sp,
                                        color = Color(0xFF8899AA)
                                    )
                                }
                            }
                            HorizontalDivider(
                                color = Color(0xFF1E2D3D),
                                modifier = Modifier.padding(start = 52.dp, top = 8.dp, bottom = 8.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

/** Circular avatar with an animated pulsing ring when the friend is live. */
@Composable
private fun LiveAvatarCircle(initial: String, isLive: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "liveRing")
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringAlpha"
    )

    Box(contentAlignment = Alignment.Center) {
        // Animated ring
        Canvas(modifier = Modifier.size(72.dp)) {
            val strokeWidth = 3.dp.toPx()
            val ringColor = if (isLive) Color(0xFF4CAF50) else Color(0xFF334455)
            drawCircle(
                color = ringColor.copy(alpha = if (isLive) ringAlpha else 1f),
                radius = size.minDimension / 2f - strokeWidth / 2f,
                style = Stroke(width = strokeWidth)
            )
        }

        // Avatar circle
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(Color(0xFF0288D1)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )
        }
    }
}

/** Badge showing "Live" in green or "Offline" in grey. */
@Composable
private fun LiveSharingBadge(isLive: Boolean) {
    val bgColor by animateColorAsState(
        targetValue = if (isLive) Color(0x2247B97D) else Color(0x22334455),
        label = "badgeBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isLive) Color(0xFF4CAF50) else Color(0xFF8899AA),
        label = "badgeText"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(textColor)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = if (isLive) "Live Sharing" else "Not Sharing",
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ActivityTimelineLive(locationName: String, timeString: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        // Left: dots column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(36.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4FC3F7))
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Right: activity card
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            val icon = if (locationName.contains("Home", ignoreCase = true)) Icons.Filled.Home 
                       else if (locationName.contains("Gym", ignoreCase = true)) Icons.Filled.FitnessCenter 
                       else Icons.Filled.LocationOn
            
            ActivityCard(
                icon = { Icon(icon, null, tint = Color(0xFF4FC3F7), modifier = Modifier.size(20.dp)) },
                title = locationName,
                timeBlock = timeString
            )
        }
    }
}

@Composable
private fun ActivityCard(
    icon: @Composable () -> Unit,
    title: String,
    timeBlock: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E2D3D),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0D2840)),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF8899AA),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = timeBlock,
                        color = Color(0xFF8899AA),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
