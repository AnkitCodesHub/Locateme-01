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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.locationtracker.app.ui.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onSignOut: () -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()
    var showSignOutDialog by remember { mutableStateOf(false) }

    val displayName = currentUser?.displayName?.ifBlank { null }
        ?: currentUser?.email?.substringBefore('@') ?: "User"
    val email = currentUser?.email ?: ""
    val initial = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "U"

    Scaffold(
        containerColor = Color(0xFF0D1B2A),
        topBar = {
            TopAppBar(
                title = {
                    Text("Profile", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0D1B2A))
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Avatar Section ────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF4FC3F7), Color(0xFF0288D1))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 40.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = displayName,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )
            Text(
                text = email,
                color = Color(0xFF8899AA),
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── Account Info Card ─────────────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E2D3D),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    ProfileInfoRow(
                        icon = Icons.Filled.Person,
                        label = "Display Name",
                        value = displayName
                    )
                    ProfileDivider()
                    ProfileInfoRow(
                        icon = Icons.Filled.Email,
                        label = "Email",
                        value = email
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Settings Card ─────────────────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E2D3D),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    SettingsRow(icon = Icons.Filled.Notifications, label = "Notifications")
                    ProfileDivider()
                    SettingsRow(icon = Icons.Filled.Shield, label = "Privacy & Permissions")
                    ProfileDivider()
                    SettingsRow(icon = Icons.Filled.Lock, label = "Security")
                    ProfileDivider()
                    SettingsRow(icon = Icons.Filled.Info, label = "About LocateMe")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Sign Out Button ───────────────────────────────────────────────
            Button(
                onClick = { showSignOutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2D1515)
                )
            ) {
                Icon(
                    Icons.Filled.ExitToApp,
                    contentDescription = null,
                    tint = Color(0xFFEF5350),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign Out", color = Color(0xFFEF5350), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            containerColor = Color(0xFF1E2D3D),
            title = { Text("Sign Out?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to sign out? Active location sharing will be stopped.",
                    color = Color(0xFF8899AA)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutDialog = false
                        authViewModel.signOut()
                        onSignOut()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))
                ) {
                    Text("Sign Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancel", color = Color(0xFF8899AA))
                }
            }
        )
    }
}

@Composable
private fun ProfileInfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF4FC3F7), modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(label, color = Color(0xFF8899AA), fontSize = 12.sp)
            Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Color(0xFF8899AA), modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Text(label, color = Color.White, fontSize = 15.sp)
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = Color(0xFF334455),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun ProfileDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(Color(0xFF1A2A3A))
    )
}
