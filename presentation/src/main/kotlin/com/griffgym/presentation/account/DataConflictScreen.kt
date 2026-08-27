package com.griffgym.presentation.account

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.griffgym.presentation.components.GriffGymCard
import com.griffgym.presentation.components.GriffGymPrimaryButton
import com.griffgym.presentation.components.GriffGymSecondaryButton
import com.griffgym.presentation.components.HairLine
import com.griffgym.presentation.theme.GriffGymTheme

@Composable
internal fun DataConflictRoute(
    onUseCloudData: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DataConflictViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    DataConflictScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onUseCloudData = onUseCloudData,
        onCancel = onCancel,
        modifier = modifier,
    )
}

/**
 * Two training histories, and an app that refuses to guess which one is real.
 *
 * There is no merge button, and there never will be. Merging two independent logs means
 * inventing sets that were never performed and reference maxes nobody ever hit, and the
 * app cannot tell an old backup apart from somebody else's account with any confidence
 * worth betting a training history on.
 *
 * So the screen states the situation, offers the one reversible-by-nobody action behind a
 * second explicit confirmation, and otherwise leaves both copies exactly where they are.
 */
@Composable
fun DataConflictScreen(
    state: DataConflictUiState,
    onEvent: (DataConflictUiEvent) -> Unit,
    onUseCloudData: () -> Unit,
    onCancel: () -> Unit,
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
        AccountHeading(
            title = DataConflictCopy.TITLE,
            description = DataConflictCopy.BODY,
        )

        Spacer(Modifier.height(16.dp))
        GriffGymCard(accentBar = colors.primary) {
            Text(
                text = DataConflictCopy.POLICY,
                style = GriffGymTheme.typography.body,
                color = colors.textPrimary,
            )
        }

        Spacer(Modifier.height(16.dp))
        GriffGymCard(contentPadding = 0.dp) {
            SideRow(
                icon = Icons.Filled.PhoneAndroid,
                title = "ON THIS DEVICE",
                detail = "The training you have logged on this phone.",
            )
            HairLine()
            SideRow(
                icon = Icons.Filled.CloudDownload,
                title = "IN YOUR ACCOUNT",
                detail = "The backup already stored under this email.",
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = DataConflictCopy.CHOICE,
            style = GriffGymTheme.typography.bodySmall,
            color = colors.textTertiary,
        )

        Spacer(Modifier.height(12.dp))
        GriffGymPrimaryButton(
            text = "USE CLOUD DATA",
            onClick = { onEvent(DataConflictUiEvent.UseCloudRequested) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        GriffGymSecondaryButton(
            text = "CANCEL",
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (state.isConfirmingUseCloud) {
        UseCloudDataDialog(
            onConfirm = {
                onEvent(DataConflictUiEvent.ConfirmUseCloud)
                onUseCloudData()
            },
            onDismiss = { onEvent(DataConflictUiEvent.DismissConfirmation) },
        )
    }
}

/**
 * The second question, and the only place the word "replaced" appears.
 *
 * The first tap says which copy the lifter prefers. This one says what that costs, because
 * the local history is gone the moment the restore transaction commits and no screen after
 * this point can offer it back.
 */
@Composable
private fun UseCloudDataDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = GriffGymTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        AccountDialogSurface {
            Text(
                text = DataConflictCopy.CONFIRM_TITLE,
                style = GriffGymTheme.typography.headline,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = DataConflictCopy.CONFIRM_BODY,
                style = GriffGymTheme.typography.body,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = DataConflictCopy.CONFIRM_CONSEQUENCE,
                style = GriffGymTheme.typography.body,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                GriffGymSecondaryButton(
                    text = "KEEP BOTH FOR NOW",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                GriffGymPrimaryButton(
                    text = "REPLACE LOCAL DATA",
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    contentPaddingHorizontal = 12.dp,
                )
            }
        }
    }
}

@Composable
private fun SideRow(icon: ImageVector, title: String, detail: String) {
    val colors = GriffGymTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = GriffGymTheme.typography.label,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = detail,
                style = GriffGymTheme.typography.bodySmall,
                color = colors.textTertiary,
            )
        }
    }
}

/** Asserted verbatim: this wording is the safety guarantee, not decoration. */
internal object DataConflictCopy {

    const val TITLE = "TRAINING DATA FOUND IN TWO PLACES"

    const val BODY = "This device contains local training data, and your account already " +
        "contains a cloud backup."

    const val POLICY = "For safety, Griff Gym will not merge or overwrite either history " +
        "automatically."

    const val CHOICE = "Using the cloud data replaces the training history on this device. " +
        "Cancelling leaves both copies exactly as they are."

    const val CONFIRM_TITLE = "REPLACE THIS DEVICE'S DATA?"

    const val CONFIRM_BODY = "The training history on this phone will be replaced by the " +
        "backup in your account."

    const val CONFIRM_CONSEQUENCE = "This cannot be undone."
}

@Preview(widthDp = 390, heightDp = 844, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun DataConflictScreenPreview() {
    GriffGymTheme {
        DataConflictScreen(
            state = DataConflictUiState(),
            onEvent = {},
            onUseCloudData = {},
            onCancel = {},
        )
    }
}
