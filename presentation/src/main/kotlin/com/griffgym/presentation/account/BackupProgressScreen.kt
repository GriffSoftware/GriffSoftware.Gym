package com.griffgym.presentation.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.griffgym.domain.model.BackupStage
import com.griffgym.presentation.components.GriffGymBadge
import com.griffgym.presentation.components.GriffGymCard
import com.griffgym.presentation.components.GriffGymPrimaryButton
import com.griffgym.presentation.components.HairLine
import com.griffgym.presentation.theme.GriffGymTheme

@Composable
internal fun BackupProgressRoute(
    onCompleted: (AuthFlowResult) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackupProgressViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.completion.collect(onCompleted)
    }

    BackupProgressScreen(
        state = state,
        onEvent = viewModel::onEvent,
        modifier = modifier,
    )
}

/**
 * The one screen in the app that a lifter watches without being able to do anything.
 *
 * It is built around not looking frozen and not lying. Named stages instead of a spinner,
 * a bar driven by real reported progress, and a failure state that leads with the fact the
 * data is still on the phone — because "backup failed" is otherwise read as "data lost",
 * which is precisely the fear that brought them to this screen.
 */
@Composable
fun BackupProgressScreen(
    state: BackupProgressUiState,
    onEvent: (BackupProgressUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GriffGymTheme.colors
    val margin = GriffGymTheme.dimens.screenMargin
    val failed = state.status == TransferStatus.FAILED

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = margin, vertical = 24.dp),
    ) {
        GriffGymBadge(
            text = if (failed) "NOT BACKED UP" else "BACKING UP",
            filled = !failed,
            color = colors.primary,
        )

        Spacer(Modifier.height(16.dp))
        AccountHeading(
            title = if (failed) BackupCopy.FAILED_TITLE else BackupCopy.TITLE,
            description = if (failed) null else BackupCopy.DESCRIPTION,
        )

        Spacer(Modifier.height(24.dp))
        if (failed) {
            GriffGymCard(accentBar = colors.error) {
                Text(
                    text = BackupCopy.FAILED_BODY,
                    style = GriffGymTheme.typography.body,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = BackupCopy.FAILED_REASSURANCE,
                    style = GriffGymTheme.typography.body,
                    color = colors.textSecondary,
                )
                if (state.error != null) {
                    Spacer(Modifier.height(12.dp))
                    HairLine()
                    Spacer(Modifier.height(12.dp))
                    AccountErrorText(state.error)
                }
            }
        } else {
            GriffGymCard(contentPadding = 0.dp) {
                AccountProgressBar(fraction = state.fraction)
                Column(Modifier.padding(16.dp)) {
                    state.steps.forEachIndexed { index, step ->
                        if (index > 0) Spacer(Modifier.height(14.dp))
                        TransferStepRow(step)
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        GriffGymPrimaryButton(
            text = if (failed) "TRY AGAIN" else "BACKING UP…",
            onClick = { onEvent(BackupProgressUiEvent.Retry) },
            modifier = Modifier.fillMaxWidth(),
            // Nothing to press while the upload is running: the only honest action during a
            // transfer of somebody's whole training history is to let it finish.
            enabled = failed,
        )

        if (failed) {
            Spacer(Modifier.height(4.dp))
            AccountTertiaryAction(
                text = "CONTINUE WITHOUT BACKUP FOR NOW",
                onClick = { onEvent(BackupProgressUiEvent.ContinueWithoutBackup) },
            )
        }
    }
}

/** One stage of the upload: what it is, and whether it has happened. */
@Composable
private fun TransferStepRow(step: TransferStepUi, modifier: Modifier = Modifier) {
    val colors = GriffGymTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (step.state) {
                StepState.DONE -> Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(16.dp),
                )

                StepState.ACTIVE -> Box(
                    Modifier
                        .size(10.dp)
                        .background(colors.primary),
                )

                StepState.PENDING -> Box(
                    Modifier
                        .size(10.dp)
                        .border(GriffGymTheme.dimens.borderWidth, colors.outlineStrong),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = step.label,
            style = GriffGymTheme.typography.body,
            color = when (step.state) {
                StepState.ACTIVE -> colors.textPrimary
                StepState.DONE -> colors.textSecondary
                StepState.PENDING -> colors.textTertiary
            },
        )
    }
}

/** Asserted verbatim by the tests: this is the promise the feature is making. */
internal object BackupCopy {

    const val TITLE = "BACKING UP YOUR TRAINING DATA"

    const val DESCRIPTION = "Your training stays on this phone. Griff Gym is putting a copy " +
        "in your account."

    const val FAILED_TITLE = "ACCOUNT CREATED"

    const val FAILED_BODY = "Your local training data has not been fully backed up yet."

    const val FAILED_REASSURANCE = "Your data is still safe on this device."
}

@Preview(widthDp = 390, heightDp = 844, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun BackupProgressScreenPreview() {
    GriffGymTheme {
        BackupProgressScreen(
            state = BackupProgressUiState(
                steps = BackupSteps.at(BackupStage.UPLOADING_CYCLES),
                fraction = 0.5f,
            ),
            onEvent = {},
        )
    }
}

@Preview(widthDp = 390, heightDp = 844, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun BackupProgressScreenFailedPreview() {
    GriffGymTheme {
        BackupProgressScreen(
            state = BackupProgressUiState(
                status = TransferStatus.FAILED,
                error = AccountMessages.NO_CONNECTION,
            ),
            onEvent = {},
        )
    }
}
