package com.griffgym.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.griffgym.presentation.account.AuthNavHost
import com.griffgym.presentation.components.GRIFF_GYM_BRAND
import com.griffgym.presentation.onboarding.OnboardingNavHost
import com.griffgym.presentation.startup.StartupUiState
import com.griffgym.presentation.startup.StartupViewModel
import com.griffgym.presentation.theme.GriffGymTheme

/**
 * The top of the composition: decides whether this launch starts at the data-protection
 * screen, in first-run setup, or in the app itself, and mounts exactly one of them.
 *
 * Swapping the whole subtree rather than branching inside one NavHost is what makes the back
 * stack behave: leaving a flow discards its graph entirely, so Home is the root and back from
 * Home leaves the app rather than returning to a sign-in form.
 *
 * It is also what makes signing out safe. The app graph is torn down wholesale, so no screen
 * survives holding a reference to training data that has just been cleared from the device.
 */
@Composable
fun GriffGymRoot(
    modifier: Modifier = Modifier,
    viewModel: StartupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (state) {
        StartupUiState.Loading -> StartupPlaceholder(modifier)

        StartupUiState.ChoosingDataMode -> AuthNavHost(
            onAuthFlowComplete = viewModel::onAuthFlowComplete,
            modifier = modifier,
        )

        StartupUiState.Onboarding -> OnboardingNavHost(
            onOnboardingComplete = viewModel::onOnboardingCompleted,
            modifier = modifier,
        )

        StartupUiState.Ready -> GriffGymApp(
            onSignedOut = viewModel::onSignedOut,
            modifier = modifier,
        )
    }
}

/**
 * Holds the brand for the frame or two the existence checks take. Deliberately not an
 * animated splash — the app should feel like it was already open.
 */
@Composable
private fun StartupPlaceholder(modifier: Modifier = Modifier) {
    val colors = GriffGymTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = GRIFF_GYM_BRAND,
            style = GriffGymTheme.typography.brand.copy(fontStyle = FontStyle.Italic),
            color = colors.primary,
        )
    }
}

@Preview(widthDp = 390, heightDp = 844, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun StartupPlaceholderPreview() {
    GriffGymTheme {
        StartupPlaceholder()
    }
}
