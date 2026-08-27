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
import androidx.compose.material3.Text
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
internal fun RegisterRoute(
    onBack: () -> Unit,
    onSignInInstead: () -> Unit,
    onStep: (AuthFlowStep) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.steps.collect(onStep)
    }

    RegisterScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onSignInInstead = onSignInInstead,
        modifier = modifier,
    )
}

/**
 * Three fields, in the order a password manager expects them.
 *
 * The IME chain matters more here than anywhere else in the app: this is the one form a
 * lifter fills in with two thumbs, once, and every extra tap on a field is a chance to give
 * up and choose local-only instead.
 */
@Composable
fun RegisterScreen(
    state: RegisterUiState,
    onEvent: (RegisterUiEvent) -> Unit,
    onBack: () -> Unit,
    onSignInInstead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GriffGymTheme.colors
    val margin = GriffGymTheme.dimens.screenMargin
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    val submit = {
        keyboard?.hide()
        onEvent(RegisterUiEvent.Submit)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = margin, vertical = 12.dp),
    ) {
        AccountBackAction(onBack = onBack, modifier = Modifier.padding(start = 0.dp))

        Spacer(Modifier.height(12.dp))
        AccountHeading(
            title = "CREATE ACCOUNT",
            description = "Your training history is backed up as you log it, and comes " +
                "back on any device you sign in on.",
        )

        Spacer(Modifier.height(24.dp))
        GriffGymCard {
            AuthTextField(
                label = "Email",
                value = state.email,
                onValueChange = { onEvent(RegisterUiEvent.EmailChanged(it)) },
                placeholder = "you@example.com",
                error = state.errorFor(AuthField.EMAIL),
                enabled = !state.isSubmitting,
                contentType = ContentType.NewUsername,
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
                onValueChange = { onEvent(RegisterUiEvent.PasswordChanged(it)) },
                placeholder = "At least ${Credentials.MIN_PASSWORD_LENGTH} characters",
                error = state.errorFor(AuthField.PASSWORD),
                enabled = !state.isSubmitting,
                contentType = ContentType.NewPassword,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                ),
                isPassword = true,
                isPasswordVisible = state.isPasswordVisible,
                onTogglePasswordVisibility = {
                    onEvent(RegisterUiEvent.TogglePasswordVisibility)
                },
            )

            Spacer(Modifier.height(16.dp))
            AuthTextField(
                label = "Confirm password",
                value = state.confirmPassword,
                onValueChange = { onEvent(RegisterUiEvent.ConfirmPasswordChanged(it)) },
                error = state.errorFor(AuthField.CONFIRM_PASSWORD),
                enabled = !state.isSubmitting,
                contentType = ContentType.NewPassword,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                isPassword = true,
                isPasswordVisible = state.isPasswordVisible,
                onTogglePasswordVisibility = {
                    onEvent(RegisterUiEvent.TogglePasswordVisibility)
                },
            )
        }

        if (state.formError != null) {
            Spacer(Modifier.height(16.dp))
            AccountErrorText(state.formError)
        }

        Spacer(Modifier.height(24.dp))
        GriffGymPrimaryButton(
            text = if (state.isSubmitting) "CREATING ACCOUNT…" else "CREATE ACCOUNT",
            onClick = submit,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canSubmit,
        )

        Spacer(Modifier.height(4.dp))
        AccountTertiaryAction(
            text = "ALREADY HAVE AN ACCOUNT? SIGN IN",
            onClick = onSignInInstead,
            enabled = !state.isSubmitting,
        )

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Your training stays on this phone either way. The account is a copy of " +
                "it, not a replacement.",
            modifier = Modifier.fillMaxWidth(),
            style = GriffGymTheme.typography.bodySmall,
            color = colors.textTertiary,
        )
    }
}

@Preview(widthDp = 390, heightDp = 844, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun RegisterScreenPreview() {
    GriffGymTheme {
        RegisterScreen(
            state = RegisterUiState(
                email = "lifter@griffgym.app",
                password = "barbell123",
                confirmPassword = "barbell123",
            ),
            onEvent = {},
            onBack = {},
            onSignInInstead = {},
        )
    }
}

@Preview(widthDp = 390, heightDp = 844, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun RegisterScreenErrorPreview() {
    GriffGymTheme {
        RegisterScreen(
            state = RegisterUiState(
                email = "lifter@griffgym",
                password = "short",
                confirmPassword = "shorter",
                fieldErrors = mapOf(
                    AuthField.EMAIL to "Enter a valid email address",
                    AuthField.PASSWORD to "Use at least 8 characters",
                    AuthField.CONFIRM_PASSWORD to "Passwords do not match",
                ),
                formError = AccountMessages.NO_CONNECTION,
            ),
            onEvent = {},
            onBack = {},
            onSignInInstead = {},
        )
    }
}
