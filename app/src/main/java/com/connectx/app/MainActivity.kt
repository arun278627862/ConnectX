package com.connectx.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.connectx.app.data.local.preferences.AppPreferencesManager
import com.connectx.app.ui.navigation.Screen
import com.connectx.app.ui.screens.auth.AuthScreen
import com.connectx.app.ui.screens.call.CallScreen
import com.connectx.app.ui.screens.chat.ChatDetailScreen
import com.connectx.app.ui.screens.config.ConfigScreen
import com.connectx.app.ui.screens.group.GroupCreateScreen
import com.connectx.app.ui.screens.home.HomeScreen
import com.connectx.app.ui.screens.ptt.PttScreen
import com.connectx.app.ui.theme.ConnectXTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val prefsManager: AppPreferencesManager
) : ViewModel() {
    val authTokens = prefsManager.authTokensFlow
    val themeMode = prefsManager.themeModeFlow
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val authTokens by mainViewModel.authTokens.collectAsState(initial = com.connectx.app.data.local.preferences.AuthTokens())
            val themeMode by mainViewModel.themeMode.collectAsState(initial = "SYSTEM")

            ConnectXTheme(themeMode = themeMode) {
                val navController = rememberNavController()
                val startDestination = if (authTokens.accessToken != null) Screen.Home.route else Screen.Auth.route

                NavHost(navController = navController, startDestination = startDestination) {
                    composable(Screen.Config.route) {
                        ConfigScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable(Screen.Auth.route) {
                        AuthScreen(
                            onAuthSuccess = {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Auth.route) { inclusive = true }
                                }
                            },
                            onNavigateConfig = { navController.navigate(Screen.Config.route) }
                        )
                    }
                    composable(Screen.Home.route) {
                        HomeScreen(
                            onNavigateChat = { chatId -> navController.navigate(Screen.ChatDetail.createRoute(chatId)) },
                            onNavigateCallScreen = { navController.navigate(Screen.Call.route) },
                            onNavigateGroupCreate = { navController.navigate(Screen.GroupCreate.route) },
                            onNavigatePtt = { navController.navigate(Screen.Ptt.route) },
                            onNavigateConfig = { navController.navigate(Screen.Config.route) },
                            onLogout = {
                                navController.navigate(Screen.Auth.route) {
                                    popUpTo(Screen.Home.route) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable(Screen.ChatDetail.route) {
                        ChatDetailScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateCallScreen = { navController.navigate(Screen.Call.route) }
                        )
                    }
                    composable(Screen.Call.route) {
                        CallScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable(Screen.GroupCreate.route) {
                        GroupCreateScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable(Screen.Ptt.route) {
                        PttScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
