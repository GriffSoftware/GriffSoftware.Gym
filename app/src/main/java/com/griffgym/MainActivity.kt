package com.griffgym

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.griffgym.presentation.navigation.GriffGymRoot
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
                // The root decides between first-run setup and the app shell; it is not the
                // shell itself, so a lifter who has not set up never sees the navigation bar.
                GriffGymRoot(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(GriffGymTheme.colors.background),
                )
            }
        }
    }
}
