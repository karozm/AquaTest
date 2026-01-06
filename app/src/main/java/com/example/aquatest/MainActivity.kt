package com.example.aquatest

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.aquatest.api.AuthRequest
import com.example.aquatest.api.RetrofitClient
import com.example.aquatest.bluetooth.BLEManager
import com.example.aquatest.screens.DashboardScreen
import com.example.aquatest.screens.LoginScreen
import com.example.aquatest.screens.RegisterScreen
import com.example.aquatest.ui.theme.AquaTestTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var bleManager: BLEManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bleManager = BLEManager(this)
        enableEdgeToEdge()
        setContent {
            AquaTestTheme {
                // Request permissions on startup
                RequestPermissions()
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AuthApp(bleManager)
                }
            }
        }
    }
}

@Composable
fun RequestPermissions() {
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        launcher.launch(permissions)
    }
}

@Composable
fun AuthApp(bleManager: BLEManager) {
    var currentScreen by remember { mutableStateOf("login") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    when (currentScreen) {
        "login" -> LoginScreen(
            onNavigateToRegister = { currentScreen = "register" },
            onLoginClick = { email, pass ->
                scope.launch {
                    try {
                        val response = RetrofitClient.instance.login(AuthRequest(email, pass))
                        if (response.isSuccessful) {
                            Toast.makeText(context, "Zalogowano!", Toast.LENGTH_SHORT).show()
                            currentScreen = "dashboard"
                        } else {
                            Toast.makeText(context, "Błąd logowania", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Błąd sieci", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
        "register" -> RegisterScreen(
            onNavigateToLogin = { currentScreen = "login" },
            onRegisterClick = { email, pass ->
                scope.launch {
                    try {
                        val response = RetrofitClient.instance.register(AuthRequest(email, pass))
                        if (response.isSuccessful) {
                            Toast.makeText(context, "Zarejestrowano!", Toast.LENGTH_SHORT).show()
                            currentScreen = "login"
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Błąd sieci", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
        "dashboard" -> DashboardScreen(bleManager = bleManager)
    }
}
