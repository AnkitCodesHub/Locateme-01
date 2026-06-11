package com.locationtracker.app.ui.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import coil.Coil
import coil.request.ImageRequest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerInfoWindow
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.locationtracker.app.data.model.Friend
import com.locationtracker.app.data.model.SharingDuration
import com.locationtracker.app.ui.viewmodel.MapViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    mapViewModel: MapViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val friendLocations by mapViewModel.friendLocations.collectAsStateWithLifecycle()
    val myLocation by mapViewModel.myLocation.collectAsStateWithLifecycle()
    val friends by mapViewModel.friends.collectAsStateWithLifecycle()
    val activeSessions by mapViewModel.activeSessions.collectAsStateWithLifecycle()
    val countdownLabels by mapViewModel.countdownLabels.collectAsStateWithLifecycle()
    val errorMessage by mapViewModel.errorMessage.collectAsStateWithLifecycle()

    var showBottomSheet by remember { mutableStateOf(false) }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Duration picker state
    var durationPickerFriend by remember { mutableStateOf<Friend?>(null) }
    var showDurationPicker by remember { mutableStateOf(false) }

    // Dynamic map markers
    val markerIcons = remember { mutableStateMapOf<String, BitmapDescriptor>() }

    // Camera
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(20.5937, 78.9629), 4f)
    }

    // Permission state
    var locationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (locationGranted) {
            mapViewModel.fetchMyLocation()
        }
    }

    // Request permissions and fetch location on launch
    LaunchedEffect(Unit) {
        if (!locationGranted) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            mapViewModel.fetchMyLocation()
        }
    }

    // Move camera to user's location when first fetched
    LaunchedEffect(myLocation) {
        myLocation?.let {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 14f),
                durationMs = 1200
            )
        }
    }

    // Show errors
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            mapViewModel.clearError()
        }
    }

    // Compose the active countdown label for the FAB
    val totalActiveSessions = activeSessions.size
    val firstSessionLabel = countdownLabels.values.firstOrNull()

    Box(modifier = Modifier.fillMaxSize()) {

        // --- Full-screen Google Map ---
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = locationGranted
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                compassEnabled = true
            )
        ) {
            // Blue marker for current user
            myLocation?.let { loc ->
                Marker(
                    state = MarkerState(position = LatLng(loc.latitude, loc.longitude)),
                    title = "You",
                    snippet = "Your current location",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                )
            }

            // Green markers for friends sharing live
            friendLocations.filter { it.isSharing }.forEach { loc ->
                if (!markerIcons.containsKey(loc.userId)) {
                    markerIcons[loc.userId] = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                    if (loc.profilePictureUrl.isNotEmpty()) {
                        getCircularMarkerIcon(context, loc.profilePictureUrl) { descriptor ->
                            markerIcons[loc.userId] = descriptor
                        }
                    }
                }

                MarkerInfoWindow(
                    state = MarkerState(position = LatLng(loc.latitude, loc.longitude)),
                    icon = markerIcons[loc.userId]
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E2D3D),
                        tonalElevation = 8.dp
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = loc.displayName,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "🟢 Live location",
                                color = Color(0xFF4CAF50),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // --- FAB — opens bottom sheet, shows countdown if active ---
        ExtendedFloatingActionButton(
            onClick = { showBottomSheet = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 100.dp),
            containerColor = Color(0xFF0288D1),
            contentColor = Color.White,
            icon = {
                Icon(
                    imageVector = if (totalActiveSessions > 0) Icons.Filled.Timer else Icons.Filled.People,
                    contentDescription = "Sharing"
                )
            },
            text = {
                Text(
                    text = when {
                        totalActiveSessions == 0 -> "Share Location"
                        totalActiveSessions == 1 && firstSessionLabel != null ->
                            "$firstSessionLabel · 1 friend"
                        else -> "$totalActiveSessions friends"
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp)
        )
    }

    // --- Bottom Sheet: Friend sharing list ---
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = bottomSheetState,
            containerColor = Color(0xFF0D1B2A)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Share with Friends",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(onClick = {
                        scope.launch { bottomSheetState.hide() }
                            .invokeOnCompletion { showBottomSheet = false }
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color(0xFF8899AA))
                    }
                }

                Text(
                    text = "Toggle friends to start or stop sharing your real-time location",
                    fontSize = 13.sp,
                    color = Color(0xFF8899AA),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (friends.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No friends yet.\nGo to Friends tab to add some!",
                            color = Color(0xFF8899AA),
                            fontSize = 14.sp,
                            lineHeight = 22.sp
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(friends, key = { it.uid }) { friend ->
                            val isSharing = activeSessions.containsKey(friend.uid)
                            val label = countdownLabels[friend.uid]

                            FriendSharingRow(
                                friend = friend,
                                isSharing = isSharing,
                                countdownLabel = label,
                                onToggle = {
                                    if (isSharing) {
                                        mapViewModel.stopSharing(friend.uid)
                                    } else {
                                        durationPickerFriend = friend
                                        showDurationPicker = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // --- Duration Picker Dialog ---
    if (showDurationPicker && durationPickerFriend != null) {
        val friend = durationPickerFriend!!
        DurationPickerDialog(
            friendName = friend.displayName,
            onDurationSelected = { duration ->
                mapViewModel.toggleSharing(friend, duration)
                showDurationPicker = false
                durationPickerFriend = null
            },
            onDismiss = {
                showDurationPicker = false
                durationPickerFriend = null
            }
        )
    }
}

@Composable
private fun FriendSharingRow(
    friend: Friend,
    isSharing: Boolean,
    countdownLabel: String?,
    onToggle: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E2D3D),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0288D1)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = friend.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = friend.displayName,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp
                )
                if (isSharing && countdownLabel != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.AccessTime,
                            contentDescription = null,
                            tint = Color(0xFF4FC3F7),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (countdownLabel == "∞") "Sharing indefinitely"
                            else "$countdownLabel remaining",
                            color = Color(0xFF4FC3F7),
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Text(
                        text = friend.email,
                        color = Color(0xFF8899AA),
                        fontSize = 12.sp
                    )
                }
            }

            Switch(
                checked = isSharing,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF0288D1),
                    uncheckedThumbColor = Color(0xFF8899AA),
                    uncheckedTrackColor = Color(0xFF334455)
                )
            )
        }
    }
}

@Composable
private fun DurationPickerDialog(
    friendName: String,
    onDurationSelected: (SharingDuration) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E2D3D),
        title = {
            Text(
                text = "Share with $friendName",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "How long do you want to share your location?",
                    color = Color(0xFF8899AA),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                SharingDuration.entries.forEach { duration ->
                    FilledTonalButton(
                        onClick = { onDurationSelected(duration) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF0D2840),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = if (duration == SharingDuration.INDEFINITE)
                                Icons.Filled.Map else Icons.Filled.AccessTime,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(duration.label, fontSize = 15.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF8899AA))
            }
        }
    )
}

private fun createCircularBitmapWithBorder(bitmap: Bitmap): Bitmap {
    val size = 120
    val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    
    val paint = Paint()
    val rect = Rect(0, 0, size, size)

    paint.isAntiAlias = true
    canvas.drawARGB(0, 0, 0, 0)
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4, paint)

    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, size, size, false)
    canvas.drawBitmap(scaledBitmap, rect, rect, paint)

    // Draw white border
    paint.xfermode = null
    paint.style = Paint.Style.STROKE
    paint.color = android.graphics.Color.WHITE
    paint.strokeWidth = 8f
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4, paint)

    return output
}

fun getCircularMarkerIcon(context: Context, url: String, callback: (BitmapDescriptor) -> Unit) {
    if (url.isEmpty()) {
        callback(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
        return
    }
    
    val request = ImageRequest.Builder(context)
        .data(url)
        .allowHardware(false) // Required to convert to software Bitmap cleanly
        .target { result ->
            val bitmap = (result as BitmapDrawable).bitmap
            val circularBitmap = createCircularBitmapWithBorder(bitmap)
            callback(BitmapDescriptorFactory.fromBitmap(circularBitmap))
        }
        .build()
    Coil.imageLoader(context).enqueue(request)
}
