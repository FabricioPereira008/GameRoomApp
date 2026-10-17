package com.gameroom.portatil

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gameroom.portatil.ui.AppViewModel
import com.gameroom.portatil.ui.GameRoomTheme
import com.gameroom.portatil.ui.GameScreen
import com.gameroom.portatil.ui.HomeScreen

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GameRoomTheme {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(vm) { id -> nav.navigate("game/$id") }
                    }
                    composable("game/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                        GameScreen(vm, entry.arguments!!.getLong("id")) { nav.popBackStack() }
                    }
                }
            }
        }
    }
}
