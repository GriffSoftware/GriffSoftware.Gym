package com.griffgym.presentation.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.griffgym.presentation.components.GriffGymCard
import com.griffgym.presentation.components.GriffGymPrimaryButton
import com.griffgym.presentation.theme.GriffGymTheme

@Composable
internal fun LoginRoute(
    onBack: () -> Unit,
    onCreateAccountInstead: () -> Unit,
    onStep: (AuthFlowStep) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.steps.collect(onStep)
    }

    LoginScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onCreateAccountInstead = onCreateAccountInstead,
        modifier = modifier,
    )
}

/**
 * Two fields and the way back into an existing training history.
 *
 * This is the screen a lifter reaches on a new phone, so it says what signing in will do
 * before they do it — nothing about restoring data happens here, but it is the reason they
 * are typing.
 */
@Composable
fun LoginScreen(
    state: LoginUiState,
    onEvent: (LoginUiEvent) -> Unit,
    onBack: () -> Unit,
    onCreateAccountInstead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GriffGymTheme.colors
    val margin = GriffGymTheme.dimens.screenMargin
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    val submit = {
        keyboard?.hide()
        onEvent(LoginUiEvent.Submit)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = margin, vertical = 12.dp),
    ) {
        AccountBackAction(onBack = onBack)

        Spacer(Modifier.height(12.dp))
        AccountHeading(
            title = "WELCOME BACK",
            description = "Sign in and Griff Gym brings your cycles, workouts and reference " +
                "maxes back to this device.",
        )

        Spacer(Modifier.height(24.dp))
        GriffGymCard {
            AuthTextField(
                label = "Email",
                value = state.email,
                onValueChange = { onEvent(LoginUiEvent.EmailChanged(it)) },
                placeholder = "you@example.com",
                error = state.errorFor(AuthField.EMAIL),
                enabled = !state.isSubmitting,
                contentType = ContentType.Username,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                ),
            )

            Spacer(Modifier.height(16.dp))
            AuthTextField(
                label = "Password",
                value = state.password,
                onValueChange = { onEvent(LoginUiEvent.PasswordChanged(it)) },
                error = state.errorFor(AuthField.PASSWORD),
                enabled = !state.isSubmitting,
                contentType = ContentType.Password,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                isPassword = true,
                isPasswordVisible = state.isPasswordVisible,
                onTogglePasswordVisibility = { onEvent(LoginUiEvent.TogglePasswordVisibility) },
            )
        }

        if (state.formError != null) {
            Spacer(Modifier.height(16.dp))
            AccountErrorText(state.formError)
        }

        Spacer(Modifier.height(24.dp))
        GriffGymPrimaryButton(
            text = if (state.isSubmitting) "SIGNING IN…" else "SIGN IN",
            onClick = submit,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canSubmit,
        )

        Spacer(Modifier.height(4.dp))
        AccountTertiaryAction(
            text = "DON'T HAVE AN ACCOUNT? CREATE ACCOUNT",
            onClick = onCreateAccountInstead,
            enabled = !state.isSubmitting,
        )
        AccountTertiaryAction(
            text = "CONTINUE LOCALLY",
            onClick = { onEvent(LoginUiEvent.ContinueLocallyRequested) },
            enabled = !state.isSubmitting,
        )
    }

    if (state.isConfirmingLocalOnly) {
        ContinueWithoutBackupDialog(
            isWorking = false,
            onCreateAccount = {
                onEvent(LoginUiEvent.DismissConfirmation)
                onCreateAccountInstead()
            },
            onConfirm = { onEvent(LoginUiEvent.ConfirmContinueLocally) },
            onDismiss = { onEvent(LoginUiEvent.DismissConfirmation) },
        )
    }
}

@Preview(widthDp = 390, heightDp = 844, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun LoginScreenPreview() {
    GriffGymTheme {
        LoginScreen(
            state = LoginUiState(email = "lifter@griffgym.app", password = "barbell123"),
            onEvent = {},
            onBack = {},
            onCreateAccountInstead = {},
        )
    }
}

@Preview(widthDp = 390, heightDp = 844, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun LoginScreenErrorPreview() {
    GriffGymTheme {
        LoginScreen(
            state = LoginUiState(
                email = "lifter@griffgym.app",
                password = "wrongpassword",
                formError = AccountMessages.WRONG_CREDENTIALS,
            ),
            onEvent = {},
            onBack = {},
            onCreateAccountInstead = {},
        )
    }
}
