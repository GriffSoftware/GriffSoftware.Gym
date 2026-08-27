package com.griffgym.presentation.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.griffgym.presentation.components.GRIFF_GYM_BRAND
import com.griffgym.presentation.components.GriffGymCard
import com.griffgym.presentation.components.GriffGymPrimaryButton
import com.griffgym.presentation.components.GriffGymSecondaryButton
import com.griffgym.presentation.components.HairLine
import com.griffgym.presentation.theme.GriffGymTheme

@Composable
internal fun DataProtectionRoute(
    onCreateAccount: () -> Unit,
    onSignIn: () -> Unit,
    onContinuedLocally: (AuthFlowResult) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DataProtectionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.completion.collect(onContinuedLocally)
    }

    DataProtectionScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onCreateAccount = onCreateAccount,
        onSignIn = onSignIn,
        modifier = modifier,
    )
}

/**
 * The first thing a lifter sees, and the only screen in the app whose job is to tell them
 * something they would rather not hear.
 *
 * It leads with the loss, not with the feature. "Create an account to sync across devices"
 * is a sales pitch nobody reads; "your training history cannot be recovered" is the actual
 * situation, and it is the reason this screen exists at all.
 *
 * All three ways out are on screen at once. Local-only is a legitimate answer — the app is
 * complete without an account — so it is offered plainly rather than buried.
 */
@Composable
fun DataProtectionScreen(
    state: DataProtectionUiState,
    onEvent: (DataProtectionUiEvent) -> Unit,
    onCreateAccount: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GriffGymTheme.colors
    val margin = GriffGymTheme.dimens.screenMargin

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = margin, vertical = 24.dp),
    ) {
        Text(
            text = GRIFF_GYM_BRAND,
            style = GriffGymTheme.typography.brand.copy(fontStyle = FontStyle.Italic),
            color = colors.primary,
        )

        Spacer(Modifier.height(32.dp))
        Text(
            text = DataProtectionCopy.HEADLINE,
            style = GriffGymTheme.typography.displayMedium,
            color = colors.textPrimary,
        )

        Spacer(Modifier.height(20.dp))
        GriffGymCard(accentBar = colors.primary) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "NOT BACKED UP",
                    style = GriffGymTheme.typography.label,
                    color = colors.primary,
                )
            }
            Spacer(Modifier.height(12.dp))
            HairLine()
            Spacer(Modifier.height(12.dp))
            Text(
                text = DataProtectionCopy.CONSEQUENCE,
                style = GriffGymTheme.typography.body,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = DataProtectionCopy.REMEDY,
                style = GriffGymTheme.typography.body,
                color = colors.textPrimary,
            )
        }

        Spacer(Modifier.height(32.dp))
        GriffGymPrimaryButton(
            text = "CREATE ACCOUNT",
            onClick = onCreateAccount,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        GriffGymSecondaryButton(
            text = "SIGN IN",
            onClick = onSignIn,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        AccountTertiaryAction(
            text = "CONTINUE LOCALLY",
            onClick = { onEvent(DataProtectionUiEvent.ContinueLocallyRequested) },
            enabled = !state.isWorking,
        )
    }

    if (state.isConfirmingLocalOnly) {
        ContinueWithoutBackupDialog(
            isWorking = state.isWorking,
            onCreateAccount = {
                onEvent(DataProtectionUiEvent.DismissConfirmation)
                onCreateAccount()
            },
            onConfirm = { onEvent(DataProtectionUiEvent.ConfirmContinueLocally) },
            onDismiss = { onEvent(DataProtectionUiEvent.DismissConfirmation) },
        )
    }
}

/**
 * The single confirmation on the way to local-only.
 *
 * The safe action keeps the amber, because the dialog is not neutral: one of these two
 * choices ends with somebody's six months of training on a phone that will eventually be
 * dropped in a toilet. The other choice is still one tap away and not hidden behind a
 * second warning.
 */
@Composable
internal fun ContinueWithoutBackupDialog(
    isWorking: Boolean,
    onCreateAccount: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = GriffGymTheme.colors
    Dialog(onDismissRequest = { if (!isWorking) onDismiss() }) {
        AccountDialogSurface {
            Text(
                text = DataProtectionCopy.CONFIRM_TITLE,
                style = GriffGymTheme.typography.headline,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = DataProtectionCopy.CONFIRM_BODY,
                style = GriffGymTheme.typography.body,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = DataProtectionCopy.CONFIRM_CONSEQUENCE,
                style = GriffGymTheme.typography.body,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(20.dp))
            StackedDialogActions(
                primaryText = "CREATE ACCOUNT",
                onPrimary = onCreateAccount,
                secondaryText = "I UNDERSTAND — CONTINUE",
                onSecondary = onConfirm,
                enabled = !isWorking,
            )
        }
    }
}

/**
 * Kept as constants because this is the wording the whole feature exists to deliver, and
 * the tests assert on it verbatim. Rewording it should be a deliberate act, not a typo
 * somebody makes while adjusting a `Spacer`.
 */
internal object DataProtectionCopy {

    const val HEADLINE = "YOUR DATA IS STORED ONLY ON THIS DEVICE"

    const val CONSEQUENCE = "If you uninstall Griff Gym, clear the app data, or lose this " +
        "device, your training history cannot be recovered."

    const val REMEDY = "Create an account to securely back up your data."

    const val CONFIRM_TITLE = "CONTINUE WITHOUT BACKUP?"

    const val CONFIRM_BODY = "Your training data will exist only on this phone."

    const val CONFIRM_CONSEQUENCE = "Uninstalling Griff Gym or clearing its data will " +
        "permanently remove your training history."
}

@Preview(widthDp = 390, heightDp = 844, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun DataProtectionScreenPreview() {
    GriffGymTheme {
        DataProtectionScreen(
            state = DataProtectionUiState(),
            onEvent = {},
            onCreateAccount = {},
            onSignIn = {},
        )
    }
}

@Preview(widthDp = 390, heightDp = 400, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun ContinueWithoutBackupDialogPreview() {
    GriffGymTheme {
        Column(
            Modifier
                .fillMaxSize()
                .background(GriffGymTheme.colors.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            AccountDialogSurface {
                Text(
                    text = DataProtectionCopy.CONFIRM_TITLE,
                    style = GriffGymTheme.typography.headline,
                    color = GriffGymTheme.colors.textPrimary,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = DataProtectionCopy.CONFIRM_BODY,
                    style = GriffGymTheme.typography.body,
                    color = GriffGymTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = DataProtectionCopy.CONFIRM_CONSEQUENCE,
                    style = GriffGymTheme.typography.body,
                    color = GriffGymTheme.colors.textPrimary,
                )
                Spacer(Modifier.height(20.dp))
                StackedDialogActions(
                    primaryText = "CREATE ACCOUNT",
                    onPrimary = {},
                    secondaryText = "I UNDERSTAND — CONTINUE",
                    onSecondary = {},
                )
            }
        }
    }
}
