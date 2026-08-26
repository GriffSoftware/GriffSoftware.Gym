package com.griffgym.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.griffgym.presentation.navigation.BottomNavDestinations
import com.griffgym.presentation.theme.GriffGymTheme

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String,
)

/**
 * Four destinations, full-bleed. The active tab is an amber block with dark ink — a
 * deliberate inversion rather than a tint, so it is unmistakable at a glance mid-set.
 */
@Composable
fun GriffGymBottomNavigation(
    items: List<BottomNavItem>,
    selectedRoute: String?,
    onItemClick: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GriffGymTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceLowest),
    ) {
        HairLine(color = colors.surfaceVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(GriffGymTheme.dimens.bottomBarHeight),
        ) {
            items.forEach { item ->
                val selected = item.route == selectedRoute
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (selected) colors.primary else colors.surfaceLowest)
                        .clickable { onItemClick(item) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val tint = if (selected) colors.onPrimary else colors.textSecondary
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = tint,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item.label,
                        style = GriffGymTheme.typography.labelSmall,
                        color = tint,
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 390, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun GriffGymBottomNavigationPreview() {
    GriffGymTheme {
        Box(Modifier.fillMaxWidth()) {
            GriffGymBottomNavigation(
                items = BottomNavDestinations,
                selectedRoute = BottomNavDestinations.first().route,
                onItemClick = {},
            )
        }
    }
}
