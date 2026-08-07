package com.connectx.app.ui.navigation

sealed class Screen(val route: String) {
    object Config : Screen("config_screen")
    object Auth : Screen("auth_screen")
    object Home : Screen("home_screen")
    object ChatDetail : Screen("chat_detail_screen/{chatId}") {
        fun createRoute(chatId: String) = "chat_detail_screen/$chatId"
    }
    object Call : Screen("call_screen")
    object GroupCreate : Screen("group_create_screen")
    object Ptt : Screen("ptt_screen")
}
