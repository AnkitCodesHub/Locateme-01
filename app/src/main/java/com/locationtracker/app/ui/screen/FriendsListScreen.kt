package com.locationtracker.app.ui.screen

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.locationtracker.app.data.model.Friend
import com.locationtracker.app.data.model.FriendRequest
import com.locationtracker.app.ui.viewmodel.FriendViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsListScreen(
    onNavigateToProfile: (friendId: String) -> Unit,
    friendViewModel: FriendViewModel = viewModel()
) {
    val friends by friendViewModel.friends.collectAsStateWithLifecycle()
    val incoming by friendViewModel.incomingRequests.collectAsStateWithLifecycle()
    val isLoading by friendViewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by friendViewModel.errorMessage.collectAsStateWithLifecycle()
    val successMessage by friendViewModel.successMessage.collectAsStateWithLifecycle()
    
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddFriendDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            friendViewModel.clearError()
        }
    }

    LaunchedEffect(successMessage) {
        successMessage?.let {
            snackbarHostState.showSnackbar(it)
            friendViewModel.clearSuccess()
            showAddFriendDialog = false
        }
    }

    Scaffold(
        containerColor = Color(0xFF0D1B2A),
        topBar = {
            TopAppBar(
                title = {
                    Text("Friends", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0D1B2A))
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddFriendDialog = true },
                containerColor = Color(0xFF0288D1),
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Filled.PersonAdd, contentDescription = "Add Friend")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF0D1B2A),
                contentColor = Color(0xFF4FC3F7)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Filled.Group, null) },
                    text = { Text("Friends (${friends.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        BadgedBox(badge = {
                            if (incoming.isNotEmpty()) Badge { Text("${incoming.size}") }
                        }) {
                            Icon(Icons.Filled.Notifications, null)
                        }
                    },
                    text = { Text("Requests") }
                )
            }

            when (selectedTab) {
                0 -> {
                    if (isLoading && friends.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFF4FC3F7))
                        }
                    } else if (friends.isEmpty()) {
                        EmptyState(
                            icon = { Icon(Icons.Filled.PersonAdd, null, tint = Color(0xFF334455), modifier = Modifier.size(64.dp)) },
                            title = "No friends yet",
                            subtitle = "Tap + to add a friend by email"
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(friends, key = { it.uid }) { friend ->
                                FriendListItem(
                                    friend = friend,
                                    onClick = { onNavigateToProfile(friend.uid) }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    if (incoming.isEmpty()) {
                        EmptyState(
                            icon = { Icon(Icons.Filled.PersonSearch, null, tint = Color(0xFF334455), modifier = Modifier.size(64.dp)) },
                            title = "No pending requests",
                            subtitle = "Incoming friend requests will appear here"
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(incoming, key = { it.senderId }) { request ->
                                RequestCard(
                                    request = request,
                                    onAccept = { friendViewModel.acceptRequest(request.senderId) },
                                    onReject = { friendViewModel.rejectRequest(request.senderId) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddFriendDialog) {
        AddFriendDialog(
            isLoading = isLoading,
            onAdd = { email -> friendViewModel.sendFriendRequest(email) },
            onDismiss = { showAddFriendDialog = false }
        )
    }
}

@Composable
private fun FriendListItem(friend: Friend, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E2D3D),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0288D1)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = friend.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(friend.displayName, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                if (friend.email.isNotBlank()) {
                    Text(friend.email, color = Color(0xFF8899AA), fontSize = 12.sp)
                }
            }
            Icon(Icons.Filled.ChevronRight, "View Profile", tint = Color(0xFF334455))
        }
    }
}

@Composable
private fun RequestCard(
    request: FriendRequest,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E2D3D),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1B4332)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    request.senderName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(request.senderName.ifBlank { request.senderId }, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                if (request.senderEmail.isNotBlank()) {
                    Text(request.senderEmail, color = Color(0xFF8899AA), fontSize = 12.sp)
                }
            }
            IconButton(onClick = onAccept) {
                Icon(Icons.Filled.Check, "Accept", tint = Color(0xFF4CAF50))
            }
            IconButton(onClick = onReject) {
                Icon(Icons.Filled.Close, "Reject", tint = Color(0xFFEF5350))
            }
        }
    }
}

@Composable
private fun AddFriendDialog(
    isLoading: Boolean,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var emailInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E2D3D),
        icon = { Icon(Icons.Filled.PersonAdd, null, tint = Color(0xFF4FC3F7)) },
        title = { Text("Add Friend", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Enter your friend's email address to send a friend request.", color = Color(0xFF8899AA), fontSize = 14.sp)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    label = { Text("Friend's email", color = Color(0xFF8899AA)) },
                    leadingIcon = { Icon(Icons.Filled.Email, null, tint = Color(0xFF4FC3F7)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF4FC3F7),
                        unfocusedBorderColor = Color(0xFF334455),
                        focusedContainerColor = Color(0xFF0D1B2A),
                        unfocusedContainerColor = Color(0xFF0D1B2A)
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (emailInput.isNotBlank()) onAdd(emailInput.trim()) },
                enabled = emailInput.isNotBlank() && !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1))
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Send Request")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF8899AA)) }
        }
    )
}

@Composable
private fun EmptyState(icon: @Composable () -> Unit, title: String, subtitle: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            icon()
            Spacer(Modifier.height(16.dp))
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Color(0xFF8899AA), fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
