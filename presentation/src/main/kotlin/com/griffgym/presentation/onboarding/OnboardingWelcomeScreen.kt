package com.griffgym.presentation.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.griffgym.presentation.components.GRIFF_GYM_BRAND
import com.griffgym.presentation.components.GriffGymCard
import com.griffgym.presentation.components.GriffGymPrimaryButton
import com.griffgym.presentation.components.HairLine
import com.griffgym.presentation.theme.GriffGymTheme

/**
 * The first thing a new lifter sees. It sets the expectation for the three questions that
 * follow rather than dropping them straight into a form.
 */
@Composable
fun OnboardingWelcomeScreen(
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GriffGymTheme.colors
    val margin = GriffGymTheme.dimens.screenMargin

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = margin, vertical = 24.dp),
    ) {
        Text(
            text = GRIFF_GYM_BRAND,
            style = GriffGymTheme.typography.brand.copy(fontStyle = FontStyle.Italic),
            color = colors.primary,
        )

        Spacer(Modifier.height(32.dp))
        Text(
            text = "BUILD YOUR\nFIRST BLOCK",
            style = GriffGymTheme.typography.displayLarge,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Six weeks of squat, bench and deadlift, written around the numbers you " +
                "lift today. Three questions and you are training.",
            style = GriffGymTheme.typography.body,
            color = colors.textSecondary,
        )

        Spacer(Modifier.height(28.dp))
        GriffGymCard(contentPadding = 0.dp) {
            SetupStepRow(number = "01", text = "Give us your squat, bench and deadlift max")
            HairLine()
            SetupStepRow(number = "02", text = "Every set of the block is calculated from them")
            HairLine()
            SetupStepRow(number = "03", text = "Start week 1, day I")
        }

        Spacer(Modifier.weight(1f))
        GriffGymPrimaryButton(
            text = "START SETUP",
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SetupStepRow(number: String, text: String) {
    val colors = GriffGymTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = number,
            modifier = Modifier.width(34.dp),
            style = GriffGymTheme.typography.dataSmall,
            color = colors.primary,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            style = GriffGymTheme.typography.body,
            color = colors.textSecondary,
        )
    }
}

@Preview(widthDp = 390, heightDp = 844, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun OnboardingWelcomeScreenPreview() {
    GriffGymTheme {
        OnboardingWelcomeScreen(onStart = {})
    }
}
