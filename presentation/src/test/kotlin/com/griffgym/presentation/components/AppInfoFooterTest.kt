package com.griffgym.presentation.components

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import com.griffgym.presentation.account.AccountMode
import com.griffgym.presentation.account.AccountScreen
import com.griffgym.presentation.account.AccountUiState
import com.griffgym.presentation.theme.GriffGymTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The footer that closes a settings-style screen.
 *
 * Two things here are worth a test rather than an eyeball. The build number is what a lifter
 * is asked for when something has gone wrong, so it has to be legible and correct rather
 * than approximately right; and the privacy policy is a document people are entitled to
 * reach, which means a real touch target that survives a large system font.
 */
@RunWith(RobolectricTestRunner::class)
class AppInfoFooterTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the footer quotes the build it was given`() {
        renderFooter(versionName = "1.0", versionCode = 1L)

        composeRule.onNodeWithText("Version 1.0 (1)").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a package with no version name still gives up its build number`() {
        // Nothing worse than "Version  (118)" in a bug report.
        renderFooter(versionName = "", versionCode = 118L)

        composeRule.onNodeWithText("Build (118)").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the policy is one tap away, and one tap only`() {
        var clicks = 0
        renderFooter(versionName = "1.0", versionCode = 1L, onPrivacyPolicyClick = { clicks++ })

        composeRule.onNode(hasText(AppInfoCopy.PRIVACY_POLICY) and hasClickAction())
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun `the policy keeps a full touch target at twice the font size`() {
        renderFooter(versionName = "1.0", versionCode = 1L, fontScale = 2f)

        composeRule.onNode(hasText(AppInfoCopy.PRIVACY_POLICY) and hasClickAction())
            .performScrollTo()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)

        // The version line grows with it rather than being clipped away.
        composeRule.onNodeWithText("Version 1.0 (1)").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a phone with nothing to open a web page is told where the policy lives`() {
        renderFooter(versionName = "1.0", versionCode = 1L, message = AppInfoCopy.NO_BROWSER)

        composeRule.onNodeWithText(AppInfoCopy.NO_BROWSER, substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        assertTrue(AppInfoCopy.NO_BROWSER.contains(PRIVACY_POLICY_URL))
    }

    @Test
    fun `a local-only account screen still ends with the footer`() {
        renderAccount(AccountMode.LOCAL_ONLY)

        assertFooterIsReachable()
    }

    @Test
    fun `a signed-in account screen ends with the same footer`() {
        renderAccount(AccountMode.AUTHENTICATED)

        assertFooterIsReachable()
    }

    @Test
    fun `the wired footer opens exactly the published privacy policy, not a WebView`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        shadowOf(context).clearNextStartedActivities()

        composeRule.setContent {
            GriffGymTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    AppInfoFooter()
                }
            }
        }

        composeRule.onNode(hasText(AppInfoCopy.PRIVACY_POLICY) and hasClickAction())
            .performScrollTo()
            .performClick()

        val started = shadowOf(context).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(PRIVACY_POLICY_URL.toUri(), started.data)
        assertEquals(
            "The policy must open outside the app's own task, not inside it",
            Intent.FLAG_ACTIVITY_NEW_TASK,
            started.flags and Intent.FLAG_ACTIVITY_NEW_TASK,
        )
        // No further Intent queued — one tap opens one browser, not a WebView on top of it.
        assertTrue(shadowOf(context).nextStartedActivity == null)
    }

    @Test
    fun `opening the policy reports success when something can view it`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        shadowOf(context).clearNextStartedActivities()

        val opened = context.openExternalUrl(PRIVACY_POLICY_URL)

        assertTrue(opened)
        val started = shadowOf(context).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(PRIVACY_POLICY_URL.toUri(), started.data)
    }

    @Test
    fun `a device with nothing able to view a web page reports failure, not a crash`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        shadowOf(context).checkActivities(true)

        val opened = context.openExternalUrl(PRIVACY_POLICY_URL)

        assertFalse(opened)
    }

    private fun assertFooterIsReachable() {
        composeRule.onNode(hasText(AppInfoCopy.PRIVACY_POLICY) and hasClickAction())
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(GRIFF_GYM_BRAND).performScrollTo().assertIsDisplayed()
    }

    private fun renderAccount(mode: AccountMode) {
        composeRule.setContent {
            GriffGymTheme {
                AccountScreen(
                    state = AccountUiState(
                        isLoading = false,
                        mode = mode,
                        email = "lifter@griffgym.test",
                    ),
                    onEvent = {},
                    onCreateAccount = {},
                    onSignIn = {},
                )
            }
        }
    }

    private fun renderFooter(
        versionName: String,
        versionCode: Long,
        onPrivacyPolicyClick: () -> Unit = {},
        message: String? = null,
        fontScale: Float = 1f,
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale),
            ) {
                GriffGymTheme {
                    // Scrollable like the screens that host it, so `performScrollTo` behaves the
                    // same way here as it does in the account tests.
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        AppInfoFooter(
                            versionName = versionName,
                            versionCode = versionCode,
                            onPrivacyPolicyClick = onPrivacyPolicyClick,
                            message = message,
                        )
                    }
                }
            }
        }
    }
}
