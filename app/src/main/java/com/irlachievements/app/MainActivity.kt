package com.irlachievements.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.irlachievements.app.ui.AppViewModel
import com.irlachievements.app.ui.IrlAchievementsApp
import com.irlachievements.app.ui.theme.IrlAchievementsTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels {
        AppViewModel.Factory((application as IrlAchievementsApplication).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IrlAchievementsTheme {
                IrlAchievementsApp(viewModel)
            }
        }
    }
}
