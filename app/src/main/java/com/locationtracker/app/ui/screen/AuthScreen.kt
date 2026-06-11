package com.locationtracker.app.ui.screen

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.locationtracker.app.ui.viewmodel.AuthState
import com.locationtracker.app.ui.viewmodel.AuthViewModel

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isGoogleLoading by remember { mutableStateOf(false) }

    // Handle auth state changes
    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthState.Success -> {
                val currentUser = FirebaseAuth.getInstance().currentUser
                if (currentUser != null) {
                    val userId = currentUser.uid
                    val userEmail = currentUser.email?.trim()?.lowercase() ?: ""
                    val userName = currentUser.displayName ?: "User"
                
                    Log.d("LocateMeAuth", "Forcing database write for UID: \$userId, Email: \$userEmail")
                
                    val userRef = Firebase.database.reference.child("users").child(userId)
                    val userMap = hashMapOf(
                        "uid" to userId,
                        "email" to userEmail,
                        "displayName" to userName,
                        "profilePictureUrl" to (currentUser.photoUrl?.toString() ?: "")
                    )
                
                    userRef.setValue(userMap)
                        .addOnSuccessListener {
                            Log.d("LocateMeAuth", "Database write SUCCESS for \$userEmail")
                            // ONLY navigate to Map screen AFTER this success callback fires
                            onAuthSuccess()
                        }
                        .addOnFailureListener { e: java.lang.Exception ->
                            Log.e("LocateMeAuth", "Database write FAILED", e)
                            onAuthSuccess()
                        }
                } else {
                    onAuthSuccess()
                }
            }
            is AuthState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                authViewModel.resetState()
            }
            else -> {}
        }
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val googleIdToken = account?.idToken
                if (googleIdToken != null) {
                    val credential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    FirebaseAuth.getInstance().signInWithCredential(credential)
                        .addOnCompleteListener { authTask ->
                            if (authTask.isSuccessful) {
                                val currentUser = FirebaseAuth.getInstance().currentUser
                                if (currentUser != null) {
                                    val userId = currentUser.uid
                                    val userEmail = currentUser.email?.trim()?.lowercase() ?: ""
                                    val userName = currentUser.displayName ?: "User"
                                
                                    Log.d("LocateMeAuth", "Forcing database write for UID: \$userId, Email: \$userEmail")
                                
                                    val userRef = Firebase.database.reference.child("users").child(userId)
                                    val userMap = hashMapOf(
                                        "uid" to userId,
                                        "email" to userEmail,
                                        "displayName" to userName,
                                        "profilePictureUrl" to (currentUser.photoUrl?.toString() ?: "")
                                    )
                                
                                    userRef.setValue(userMap)
                                        .addOnSuccessListener {
                                            Log.d("LocateMeAuth", "Database write SUCCESS for \$userEmail")
                                            // ONLY navigate to Map screen AFTER this success callback fires
                                            isGoogleLoading = false
                                            onAuthSuccess()
                                        }
                                        .addOnFailureListener { e: java.lang.Exception ->
                                            Log.e("LocateMeAuth", "Database write FAILED", e)
                                            isGoogleLoading = false
                                            onAuthSuccess()
                                        }
                                } else {
                                    isGoogleLoading = false
                                    onAuthSuccess()
                                }
                            } else {
                                isGoogleLoading = false
                            }
                        }
                } else {
                    isGoogleLoading = false
                }
            } catch (e: ApiException) {
                isGoogleLoading = false
                Log.e("AuthScreen", "Google sign in failed", e)
            }
        } else {
            isGoogleLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D1B2A),
                        Color(0xFF1B263B),
                        Color(0xFF0A0E1A)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(60.dp))

            // App Icon / Logo
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF4FC3F7), Color(0xFF0288D1))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.MyLocation,
                    contentDescription = "LocateMe Logo",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "LocateMe",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Share your location with the people you trust",
                fontSize = 14.sp,
                color = Color(0xFF8899AA),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 36.dp)
            )

            // Card container
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF1E2D3D),
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isSignUp) "Create Account" else "Welcome Back",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = if (isSignUp) "Sign up to start sharing" else "Sign in to continue",
                        fontSize = 13.sp,
                        color = Color(0xFF8899AA),
                        modifier = Modifier.padding(bottom = 24.dp, top = 4.dp)
                    )

                    // Display Name (sign-up only)
                    AnimatedVisibility(
                        visible = isSignUp,
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut()
                    ) {
                        Column {
                            AuthTextField(
                                value = displayName,
                                onValueChange = { displayName = it },
                                label = "Display Name",
                                leadingIcon = {
                                    Icon(Icons.Filled.Person, null, tint = Color(0xFF4FC3F7))
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(
                                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                                )
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    // Email
                    AuthTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = "Email Address",
                        leadingIcon = {
                            Icon(Icons.Filled.Email, null, tint = Color(0xFF4FC3F7))
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Password
                    AuthTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Password",
                        leadingIcon = {
                            Icon(Icons.Filled.Lock, null, tint = Color(0xFF4FC3F7))
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Filled.VisibilityOff
                                    else Icons.Filled.Visibility,
                                    contentDescription = "Toggle password",
                                    tint = Color(0xFF8899AA)
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                if (isSignUp) authViewModel.signUp(email, password, displayName)
                                else authViewModel.signIn(email, password)
                            }
                        )
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Submit button
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            if (isSignUp) authViewModel.signUp(email, password, displayName)
                            else authViewModel.signIn(email, password)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0288D1)
                        ),
                        enabled = authState !is AuthState.Loading && !isGoogleLoading
                    ) {
                        if (authState is AuthState.Loading && !isGoogleLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = if (isSignUp) "Create Account" else "Sign In",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Toggle sign-in / sign-up
                    TextButton(onClick = {
                        isSignUp = !isSignUp
                        authViewModel.resetState()
                    }) {
                        Text(
                            text = if (isSignUp) "Already have an account? Sign In"
                            else "Don't have an account? Sign Up",
                            color = Color(0xFF4FC3F7),
                            fontSize = 14.sp
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF334455))
                        Text(
                            text = "OR",
                            color = Color(0xFF8899AA),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF334455))
                    }

                    Spacer(Modifier.height(20.dp))

                    OutlinedButton(
                        onClick = {
                            isGoogleLoading = true
                            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                .requestIdToken("71071742358-3fnao9lfmpqkhtn3hebahrpuujg5gbpv.apps.googleusercontent.com")
                                .requestEmail()
                                .build()
                            val googleSignInClient = GoogleSignIn.getClient(context, gso)
                            googleSignInLauncher.launch(googleSignInClient.signInIntent)
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        enabled = authState !is AuthState.Loading && !isGoogleLoading
                    ) {
                        if (isGoogleLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Sign in with Google", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(60.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Color(0xFF8899AA)) },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = Color(0xFF4FC3F7),
            unfocusedBorderColor = Color(0xFF334455),
            cursorColor = Color(0xFF4FC3F7),
            focusedContainerColor = Color(0xFF0D1B2A),
            unfocusedContainerColor = Color(0xFF0D1B2A)
        )
    )
}
