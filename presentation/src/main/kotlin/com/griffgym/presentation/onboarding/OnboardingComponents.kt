package com.griffgym.presentation.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.griffgym.presentation.theme.GriffGymTheme

/**
 * How far into setup the lifter is: one filled bar per completed step, amber for what is
 * done, charcoal for what is left. Same signalling language as the rest of the app — amber
 * means "this one matters right now".
 */
@Composable
fun OnboardingStepIndicator(
    stepNumber: Int,
    stepCount: Int,
    modifier: Modifier = Modifier,
    trailingLabel: String? = null,
) {
    val colors = GriffGymTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(stepCount) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .background(
                            if (index < stepNumber) colors.primary else colors.surfaceVariant,
                        ),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = trailingLabel ?: "STEP $stepNumber / $stepCount",
            style = GriffGymTheme.typography.labelSmall,
            color = colors.textTertiary,
        )
    }
}

/** Screen title and supporting line, in the type hierarchy the rest of the app uses. */
@Composable
fun OnboardingHeading(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val colors = GriffGymTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = GriffGymTheme.typography.displayMedium,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = description,
            style = GriffGymTheme.typography.body,
            color = colors.textSecondary,
        )
    }
}

@Preview(widthDp = 390, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun OnboardingStepIndicatorPreview() {
    GriffGymTheme {
        Column(Modifier.fillMaxWidth().background(GriffGymTheme.colors.background)) {
            OnboardingStepIndicator(stepNumber = 2, stepCount = 3)
        }
    }
}
