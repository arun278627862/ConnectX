package com.connectx.app.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.connectx.app.data.repository.ConnectXRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthMode {
    EMAIL, PHONE, GOOGLE, FORGOT_PASSWORD
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: ConnectXRepository
) : ViewModel() {
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _authSuccess = MutableStateFlow(false)
    val authSuccess: StateFlow<Boolean> = _authSuccess

    fun loginWithEmail(email: String, pass: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.login(email, pass)
            _isLoading.value = false
            if (result.isSuccess) {
                _authSuccess.value = true
            }
        }
    }

    fun loginWithPhone(phone: String, otp: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.login(phone, otp)
            _isLoading.value = false
            if (result.isSuccess) {
                _authSuccess.value = true
            }
        }
    }

    fun googleSignIn(idToken: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.login("google_user@connectx.io", idToken)
            _isLoading.value = false
            if (result.isSuccess) {
                _authSuccess.value = true
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    onNavigateConfig: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val authSuccess by viewModel.authSuccess.collectAsState()

    var authMode by remember { mutableStateOf(AuthMode.EMAIL) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(true) }

    LaunchedEffect(authSuccess) {
        if (authSuccess) {
            onAuthSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ConnectX Sign In") },
                actions = {
                    IconButton(onClick = onNavigateConfig) {
                        Icon(Icons.Default.Settings, contentDescription = "Server Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.padding(bottom = 32.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    "ConnectX",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
            }

            TabRow(selectedTabIndex = authMode.ordinal) {
                Tab(selected = authMode == AuthMode.EMAIL, onClick = { authMode = AuthMode.EMAIL }) {
                    Text("Email", modifier = Modifier.padding(12.dp))
                }
                Tab(selected = authMode == AuthMode.PHONE, onClick = { authMode = AuthMode.PHONE }) {
                    Text("Phone OTP", modifier = Modifier.padding(12.dp))
                }
                Tab(selected = authMode == AuthMode.GOOGLE, onClick = { authMode = AuthMode.GOOGLE }) {
                    Text("Google", modifier = Modifier.padding(12.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            when (authMode) {
                AuthMode.EMAIL -> {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email Address") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = rememberMe, onCheckedChange = { rememberMe = it })
                            Text("Remember Me")
                        }
                        TextButton(onClick = { authMode = AuthMode.FORGOT_PASSWORD }) {
                            Text("Forgot Password?")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.loginWithEmail(email, password) },
                        enabled = email.isNotEmpty() && password.isNotEmpty() && !isLoading,
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        if (isLoading) CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                        else Text("Sign In with Email")
                    }
                }
                AuthMode.PHONE -> {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Phone Number") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = otp,
                        onValueChange = { otp = it },
                        label = { Text("OTP Code") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.loginWithPhone(phone, otp) },
                        enabled = phone.isNotEmpty() && !isLoading,
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        if (isLoading) CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                        else Text("Verify & Login")
                    }
                }
                AuthMode.GOOGLE -> {
                    Button(
                        onClick = { viewModel.googleSignIn("google_sample_token_99") },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text("Continue with Google Account")
                    }
                }
                AuthMode.FORGOT_PASSWORD -> {
                    Text("Enter your email to receive a password reset code.")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { authMode = AuthMode.EMAIL },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text("Send Password Reset Link")
                    }
                }
            }
        }
    }
}
