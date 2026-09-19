package com.linetrans.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onOpenDoc = { id, start -> nav.navigate("translate/" + id + "?start=" + start) },
                onOpenSettings = { nav.navigate("settings") }
            )
        }
        composable(
            route = "translate/{docId}?start={start}",
            arguments = listOf(
                navArgument("docId") { type = NavType.StringType },
                navArgument("start") {
                    type = NavType.IntType
                    defaultValue = 0
                }
            )
        ) { entry ->
            val docId = entry.arguments?.getString("docId") ?: ""
            val start = entry.arguments?.getInt("start") ?: 0
            TranslationScreen(docId = docId, startIndex = start, onBack = { nav.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
