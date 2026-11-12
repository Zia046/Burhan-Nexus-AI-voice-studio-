package com.burhannexus.voicestudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.burhannexus.voicestudio.ui.AboutScreen
import com.burhannexus.voicestudio.ui.HomeScreen
import com.burhannexus.voicestudio.ui.ModelsScreen
import com.burhannexus.voicestudio.ui.PendingScreen
import com.burhannexus.voicestudio.ui.ProjectsScreen
import com.burhannexus.voicestudio.ui.SettingsScreen
import com.burhannexus.voicestudio.ui.StudioScreen
import com.burhannexus.voicestudio.ui.VoiceLibraryScreen
import com.burhannexus.voicestudio.ui.VoiceStudioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = App.instance
        setContent {
            val dark = when (app.prefs.theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            VoiceStudioTheme(dark) {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = "home") {
                    composable("home") { HomeScreen(nav) }
                    composable(
                        "studio/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.LongType })
                    ) { entry -> StudioScreen(nav, entry.arguments?.getLong("id") ?: 0L) }
                    composable("voices") { VoiceLibraryScreen(nav) }
                    composable("models") { ModelsScreen(nav) }
                    composable("projects") { ProjectsScreen(nav) }
                    composable("settings") { SettingsScreen(nav) }
                    composable("about") { AboutScreen(nav) }
                    composable("pending/{feature}") { entry ->
                        PendingScreen(nav, entry.arguments?.getString("feature") ?: "")
                    }
                }
            }
        }
    }
}
