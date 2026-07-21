package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.game.ui.GameScreen
import com.example.game.ui.GameViewModel
import com.example.game.ui.screens.GamePlayScreen
import com.example.game.ui.screens.LobbyScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        val viewModel: GameViewModel = viewModel()
        val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()

        Surface(modifier = Modifier.fillMaxSize()) {
          when (currentScreen) {
            GameScreen.MENU, GameScreen.LOBBY, GameScreen.STATS -> {
              LobbyScreen(viewModel = viewModel)
            }
            GameScreen.PLAYING, GameScreen.MATCHMAKING, GameScreen.MATCH_OVER -> {
              GamePlayScreen(viewModel = viewModel)
            }
          }
        }
      }
    }
  }
}
