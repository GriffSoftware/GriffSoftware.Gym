package com.griffgym.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.Text
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.griffgym.presentation.theme.GriffGymTheme

/**
 * A single training number.
 *
 * Deliberately not a Material `TextField`: no label, no floating placeholder, no rounded
 * container — just a sharp charcoal box with a bottom-heavy stroke that turns amber on
 * focus, matching the rest of the shape language.
 *
 * Input is filtered rather than validated on submit, and both `.` and `,` are accepted
 * because Polish keyboards produce a comma.
 */
@Composable
fun NumericInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    allowDecimal: Boolean = true,
    isError: Boolean = false,
    highlighted: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    textStyle: TextStyle = GriffGymTheme.typography.data,
) {
    val colors = GriffGymTheme.colors
    val shape = GriffGymTheme.shapes.input

    val borderColor = when {
        isError -> colors.error
        highlighted -> colors.primary
        else -> colors.outlineStrong
    }
    val contentColor = when {
        isError -> colors.error
        highlighted -> colors.primary
        enabled -> colors.textPrimary
        else -> colors.textSecondary
    }

    val selectionColors = remember(colors.primary) {
        TextSelectionColors(
            handleColor = colors.primary,
            backgroundColor = colors.primary.copy(alpha = 0.3f),
        )
    }

    Box(
        modifier = modifier
            .height(GriffGymTheme.dimens.inputHeight)
            .clip(shape)
            .background(if (enabled) colors.surfaceLowest else Color.Transparent)
            .border(if (highlighted || isError) 2.dp else 1.dp, borderColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
            BasicTextField(
                value = value,
                onValueChange = { raw -> onValueChange(raw.filterNumeric(allowDecimal)) },
                enabled = enabled,
                singleLine = true,
                textStyle = textStyle.copy(color = contentColor, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number,
                    imeAction = imeAction,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.Center) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(
                                text = placeholder,
                                style = textStyle.copy(
                                    color = colors.textTertiary,
                                    textAlign = TextAlign.Center,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        inner()
                    }
                },
            )
        }
    }
}

/**
 * Keeps only digits and at most one decimal separator. The comma is normalised to a dot
 * on the way in so the domain never has to guess at a locale.
 */
private fun String.filterNumeric(allowDecimal: Boolean): String {
    val normalised = replace(',', '.')
    val builder = StringBuilder()
    var seenSeparator = false
    normalised.forEach { char ->
        when {
            char.isDigit() -> builder.append(char)
            allowDecimal && char == '.' && !seenSeparator && builder.isNotEmpty() -> {
                seenSeparator = true
                builder.append(char)
            }
        }
    }
    return builder.toString()
}
