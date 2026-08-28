package com.griffgym.presentation.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.griffgym.presentation.components.GriffGymPrimaryButton
import com.griffgym.presentation.components.GriffGymSecondaryButton
import com.griffgym.presentation.theme.GriffGymTheme

/**
 * Screen title and supporting line for the account flow.
 *
 * Mirrors `OnboardingHeading` rather than importing it: the two features should not depend
 * on each other for a heading. If a third flow needs the same shape it belongs in
 * `components/` and both should move to it.
 */
@Composable
internal fun AccountHeading(
    title: String,
    description: String?,
    modifier: Modifier = Modifier,
) {
    val colors = GriffGymTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = GriffGymTheme.typography.displayMedium,
            color = colors.textPrimary,
        )
        if (description != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = description,
                style = GriffGymTheme.typography.body,
                color = colors.textSecondary,
            )
        }
    }
}

/**
 * An email or password input in the Griff Gym shape: sunken charcoal well, caps label, hard
 * edges, amber cursor.
 *
 * Deliberately a [BasicTextField] rather than a Material `TextField` — for the look, and
 * because nothing here interferes with the platform's own text behaviour. **Paste works.**
 * Blocking it is a habit that only ever punishes the people using a password manager, who
 * are the ones with a password worth having.
 *
 * [contentType] is what makes a password manager recognise the field; without it autofill
 * falls back to guessing from nearby text and usually gets a "confirm password" field
 * wrong.
 *
 * The label is mirrored into the semantics tree because a [BasicTextField] has no idea the
 * `Text` above it is its label — without this, TalkBack announces an unlabelled edit box.
 */
@Composable
internal fun AuthTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    error: String? = null,
    enabled: Boolean = true,
    contentType: ContentType? = null,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    isPassword: Boolean = false,
    isPasswordVisible: Boolean = false,
    onTogglePasswordVisibility: (() -> Unit)? = null,
) {
    val colors = GriffGymTheme.colors
    val selectionColors = remember(colors.primary) {
        TextSelectionColors(
            handleColor = colors.primary,
            backgroundColor = colors.primary.copy(alpha = 0.3f),
        )
    }
    val borderColor = if (error != null) colors.error else colors.outlineStrong

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label.uppercase(),
            style = GriffGymTheme.typography.labelSmall,
            color = if (error != null) colors.error else colors.textTertiary,
        )
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceLowest)
                .border(
                    width = if (error != null) {
                        GriffGymTheme.dimens.borderWidthStrong
                    } else {
                        GriffGymTheme.dimens.borderWidth
                    },
                    color = borderColor,
                )
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = true,
                    textStyle = GriffGymTheme.typography.body.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    visualTransformation = if (isPassword && !isPasswordVisible) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(GriffGymTheme.dimens.inputHeight)
                        .semantics {
                            this.contentDescription = label
                            if (contentType != null) this.contentType = contentType
                        },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty() && placeholder.isNotEmpty()) {
                                Text(
                                    text = placeholder,
                                    style = GriffGymTheme.typography.body,
                                    color = colors.textTertiary,
                                )
                            }
                            inner()
                        }
                    },
                )
            }

            if (isPassword && onTogglePasswordVisibility != null) {
                Box(
                    modifier = Modifier
                        .size(GriffGymTheme.dimens.touchTarget)
                        .clickable(onClick = onTogglePasswordVisibility),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isPasswordVisible) {
                            Icons.Filled.VisibilityOff
                        } else {
                            Icons.Filled.Visibility
                        },
                        contentDescription = if (isPasswordVisible) {
                            "Hide password"
                        } else {
                            "Show password"
                        },
                        tint = colors.textTertiary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        if (error != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = error,
                style = GriffGymTheme.typography.bodySmall,
                color = colors.error,
            )
        }
    }
}

/**
 * The quiet third option — "already have an account?", "continue locally".
 *
 * Underplayed on purpose: it is always the alternative to the amber button above it, and
 * amber in this app means exactly one thing per screen.
 */
@Composable
internal fun AccountTertiaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = GriffGymTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(GriffGymTheme.dimens.touchTarget)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = GriffGymTheme.typography.label,
            color = if (enabled) colors.textTertiary else colors.outlineStrong,
        )
    }
}

/** Back out of a full-screen auth step. Matches the top bar's icon hit area. */
@Composable
internal fun AccountBackAction(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(GriffGymTheme.dimens.touchTarget)
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = GriffGymTheme.colors.textSecondary,
        )
    }
}

/**
 * The dialog container used across the account flow. Flat, bordered, no elevation.
 *
 * Scrolls when it has to. Most of these dialogs are three lines and a pair of buttons and will
 * never come near the edge of a screen, but the account-deletion one lists seven things it is
 * about to destroy — and on a short device, or at a large font scale, that is enough to push
 * the buttons past the bottom. A dialog whose CONTINUE and CANCEL are off-screen is not a
 * cosmetic problem: it is a lifter trapped in a confirmation they cannot answer either way,
 * on the one screen in the app where being unable to say no matters most.
 */
@Composable
internal fun AccountDialogSurface(content: @Composable ColumnScope.() -> Unit) {
    val colors = GriffGymTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .border(GriffGymTheme.dimens.borderWidth, colors.outlineStrong)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        content = content,
    )
}

/**
 * A determinate bar in the app's own shape — square ends, amber over charcoal.
 *
 * Deliberately not animated: this is only ever driven by real progress reported from the
 * upload, and an easing curve between two honest numbers is a small lie about how far along
 * a lifter's backup actually is.
 */
@Composable
internal fun AccountProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    val colors = GriffGymTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(colors.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(4.dp)
                .background(colors.primary),
        )
    }
}

/**
 * A stacked pair of actions where the safe one is on top.
 *
 * Stacked rather than side by side because every confirmation in this flow has one long
 * label — "I UNDERSTAND — CONTINUE", "REPLACE LOCAL DATA" — and a label that has to shrink
 * to fit is a label somebody skims.
 */
@Composable
internal fun StackedDialogActions(
    primaryText: String,
    onPrimary: () -> Unit,
    secondaryText: String,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /**
     * Separate because the two buttons are not always gated by the same thing.
     *
     * Where the primary is disabled until some condition is met — a phrase typed, a field
     * filled — the way *out* of the dialog must stay open, or a lifter who changes their
     * mind is left looking at two dead buttons. It defaults to [enabled] so the dialogs
     * whose pair genuinely rises and falls together are unaffected.
     */
    secondaryEnabled: Boolean = enabled,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        GriffGymPrimaryButton(
            text = primaryText,
            onClick = onPrimary,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
        )
        GriffGymSecondaryButton(
            text = secondaryText,
            onClick = onSecondary,
            modifier = Modifier.fillMaxWidth(),
            enabled = secondaryEnabled,
        )
    }
}

/** A short, non-technical explanation of a failure, in the app's warning colour. */
@Composable
internal fun AccountErrorText(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message,
        modifier = modifier.fillMaxWidth(),
        style = GriffGymTheme.typography.bodySmall,
        color = GriffGymTheme.colors.error,
    )
}

@Preview(widthDp = 390, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun AuthTextFieldPreview() {
    GriffGymTheme {
        Column(
            Modifier
                .background(GriffGymTheme.colors.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AuthTextField(
                label = "Email",
                value = "lifter@griffgym.app",
                onValueChange = {},
                keyboardOptions = KeyboardOptions.Default,
            )
            AuthTextField(
                label = "Password",
                value = "barbell123",
                onValueChange = {},
                keyboardOptions = KeyboardOptions.Default,
                isPassword = true,
                onTogglePasswordVisibility = {},
            )
            AuthTextField(
                label = "Confirm password",
                value = "barbell1",
                onValueChange = {},
                keyboardOptions = KeyboardOptions.Default,
                error = "Passwords do not match",
                isPassword = true,
                onTogglePasswordVisibility = {},
            )
            Spacer(Modifier.width(0.dp))
            AccountProgressBar(fraction = 0.4f)
        }
    }
}
