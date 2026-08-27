package com.griffgym.presentation.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.griffgym.presentation.theme.GriffGymTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The two credential forms.
 *
 * What is worth holding in place here is not the layout but the honesty of the errors: a
 * lifter told "Enter a valid email address" can fix it, and one shown a stack trace or an HTTP
 * status cannot. The server stays the real authority on whether a password is acceptable;
 * these messages exist to save a round trip, not to replace it.
 */
@RunWith(RobolectricTestRunner::class)
class AuthFormScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    // --- registration ---------------------------------------------------------------------

    @Test
    fun `registration asks for the password twice and offers a way to sign in instead`() {
        composeRule.setContent {
            GriffGymTheme {
                RegisterScreen(
                    state = RegisterUiState(),
                    onEvent = {},
                    onBack = {},
                    onSignInInstead = {},
                )
            }
        }

        // AuthTextField uppercases its label, which is part of the app's visual identity.
        composeRule.onNodeWithText("EMAIL").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("PASSWORD").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("CONFIRM PASSWORD").performScrollTo().assertIsDisplayed()

        // Somebody who already has an account must not have to guess how to get to sign-in.
        composeRule.onNodeWithText("ALREADY HAVE AN ACCOUNT? SIGN IN")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `a field error appears under the field it belongs to`() {
        composeRule.setContent {
            GriffGymTheme {
                RegisterScreen(
                    state = RegisterUiState(
                        email = "not-an-email",
                        fieldErrors = mapOf(AuthField.EMAIL to "Enter a valid email address"),
                    ),
                    onEvent = {},
                    onBack = {},
                    onSignInInstead = {},
                )
            }
        }

        composeRule.onNodeWithText("Enter a valid email address")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `a mismatched confirmation is named as such`() {
        composeRule.setContent {
            GriffGymTheme {
                RegisterScreen(
                    state = RegisterUiState(
                        password = "correct horse",
                        confirmPassword = "correct hors",
                        fieldErrors = mapOf(
                            AuthField.CONFIRM_PASSWORD to "Passwords do not match",
                        ),
                    ),
                    onEvent = {},
                    onBack = {},
                    onSignInInstead = {},
                )
            }
        }

        composeRule.onNodeWithText("Passwords do not match").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the submit button stays tappable so the form can explain what is wrong`() {
        val events = mutableListOf<RegisterUiEvent>()

        composeRule.setContent {
            GriffGymTheme {
                RegisterScreen(
                    state = RegisterUiState(email = "", password = ""),
                    onEvent = events::add,
                    onBack = {},
                    onSignInInstead = {},
                )
            }
        }

        // A button that greys itself out until a hidden set of rules is satisfied leaves the
        // lifter guessing which rule they are failing.
        // The heading says CREATE ACCOUNT too, so the button is picked out by being tappable.
        composeRule.onNode(hasText("CREATE ACCOUNT") and hasClickAction())
            .performScrollTo()
            .performClick()

        assertTrue(
            "an empty form should still submit and be told why",
            events.contains(RegisterUiEvent.Submit),
        )
    }

    // --- sign in --------------------------------------------------------------------------

    @Test
    fun `sign in greets the lifter and offers both other routes`() {
        composeRule.setContent {
            GriffGymTheme {
                LoginScreen(
                    state = LoginUiState(),
                    onEvent = {},
                    onBack = {},
                    onCreateAccountInstead = {},
                )
            }
        }

        composeRule.onNodeWithText("WELCOME BACK").assertIsDisplayed()
        composeRule.onNodeWithText("DON'T HAVE AN ACCOUNT? CREATE ACCOUNT")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("CONTINUE LOCALLY").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `bad credentials are reported in plain words, with no technical detail`() {
        val message = "Invalid email address or password."

        composeRule.setContent {
            GriffGymTheme {
                LoginScreen(
                    state = LoginUiState(
                        email = "lifter@example.com",
                        formError = message,
                    ),
                    onEvent = {},
                    onBack = {},
                    onCreateAccountInstead = {},
                )
            }
        }

        composeRule.onNodeWithText(message).performScrollTo().assertIsDisplayed()

        // Nothing about HTTP, exceptions or the transport ever reaches the lifter.
        listOf("401", "HttpException", "IOException", "Retrofit", "JWT").forEach { leak ->
            assertEquals(
                "'$leak' leaked into the sign-in screen",
                0,
                composeRule.onAllNodesWithText(leak, substring = true)
                    .fetchSemanticsNodes().size,
            )
        }
    }

    @Test
    fun `signing in from the login screen can still fall back to staying local`() {
        val events = mutableListOf<LoginUiEvent>()

        composeRule.setContent {
            GriffGymTheme {
                LoginScreen(
                    state = LoginUiState(),
                    onEvent = events::add,
                    onBack = {},
                    onCreateAccountInstead = {},
                )
            }
        }

        composeRule.onNodeWithText("CONTINUE LOCALLY").performScrollTo().performClick()

        // The same single confirmation the entry screen uses — never a silent decision.
        assertTrue(
            "continuing locally from sign-in skipped the warning",
            events.any { it is LoginUiEvent.ContinueLocallyRequested },
        )
    }
}
