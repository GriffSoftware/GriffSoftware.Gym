package com.griffgym.presentation.cycles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.griffgym.presentation.theme.GriffGymTheme

/**
 * "‹ TRAINING / CYCLES" over a strapline.
 *
 * The breadcrumb doubles as the back affordance: the cycles screens hang off the drawer and
 * off Home rather than off a tab, so they carry their own way out instead of relying on the
 * shell's top bar, which belongs to the brand.
 */
@Composable
fun CycleScreenHeader(
    breadcrumb: String,
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GriffGymTheme.colors

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(vertical = 4.dp),
        ) {
            Text(
                text = "‹ $breadcrumb",
                style = GriffGymTheme.typography.labelSmall,
                color = colors.textTertiary,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            style = GriffGymTheme.typography.displayLarge,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = GriffGymTheme.typography.labelSmall,
            color = colors.primary,
        )
    }
}

@Preview(widthDp = 390, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun CycleScreenHeaderPreview() {
    GriffGymTheme {
        CycleScreenHeader(
            breadcrumb = "TRAINING",
            title = "CYCLES",
            subtitle = "BUILD. RECOVER. PROGRESS.",
            onBack = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
