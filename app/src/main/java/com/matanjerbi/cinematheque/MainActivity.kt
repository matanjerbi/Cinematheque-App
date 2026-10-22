package com.matanjerbi.cinematheque

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.matanjerbi.cinematheque.ui.navigation.CinemathequeNavHost
import com.matanjerbi.cinematheque.ui.theme.CinemathequeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CinemathequeTheme {
                CinemathequeNavHost()
            }
        }
    }
}
