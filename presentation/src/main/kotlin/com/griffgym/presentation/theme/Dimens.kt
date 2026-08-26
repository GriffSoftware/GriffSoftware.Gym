package com.griffgym.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A 4 dp rhythm, with 48 dp touch targets for chalked hands. */
@Immutable
data class GriffGymDimens(
    val unit: Dp = 4.dp,
    val gutter: Dp = 12.dp,
    val screenMargin: Dp = 16.dp,
    val sectionSpacing: Dp = 16.dp,
    val cardPadding: Dp = 16.dp,
    val touchTarget: Dp = 48.dp,
    val inputHeight: Dp = 46.dp,
    val topBarHeight: Dp = 56.dp,
    val bottomBarHeight: Dp = 68.dp,
    val borderWidth: Dp = 1.dp,
    val borderWidthStrong: Dp = 2.dp,
)
