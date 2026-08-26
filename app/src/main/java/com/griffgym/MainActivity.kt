package com.griffgym

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.griffgym.presentation.navigation.GriffGymApp
import com.griffgym.presentation.theme.GriffGymTheme
import dagger.hilt.android.AndroidEntryPoint

/** The app's only Activity: everything above it is Compose. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            GriffGymTheme {
                GriffGymApp(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(GriffGymTheme.colors.background),
                )
            }
        }
    }
}
