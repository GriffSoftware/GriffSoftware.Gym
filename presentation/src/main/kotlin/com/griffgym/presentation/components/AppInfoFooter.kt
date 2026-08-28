package com.griffgym.presentation.components

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import com.griffgym.presentation.theme.GriffGymTheme

/**
 * The version of the app that is actually running, as the footer needs it.
 *
 * [code] is the build number, which is the only thing worth quoting in a bug report once
 * two builds have shared a [name].
 */
@Immutable
data class AppVersionUi(val name: String, val code: Long)

/**
 * The quiet end of a settings-style screen: the privacy policy, and which build this is.
 *
 * Stateless on purpose — it is handed a version and a click, so it previews without a
 * package manager and tests without a browser. Use the [AppInfoFooter] overload below to
 * get the wired-up version.
 *
 * The policy link is the only amber thing here. Everything else is deliberately at the
 * bottom of the type hierarchy: a lifter looking for their backup status should never have
 * their eye pulled down here, but somebody hunting for a build number should find it
 * exactly where every other app keeps it.
 *
 * [message] is shown under the link, in the app's warning colour, for the rare device that
 * has nothing able to open a web page.
 */
@Composable
fun AppInfoFooter(
    versionName: String,
    versionCode: Long,
    onPrivacyPolicyClick: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    val colors = GriffGymTheme.colors

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HairLine()

        Box(
            modifier = Modifier
                .clip(GriffGymTheme.shapes.card)
                .clickable(
                    role = Role.Button,
                    onClickLabel = AppInfoCopy.PRIVACY_POLICY_CLICK_LABEL,
                    onClick = onPrivacyPolicyClick,
                )
                // Never a fixed height: the label has to be able to grow with the system
                // font scale, and 48 dp is a floor for the finger, not a ceiling for the text.
                .defaultMinSize(minHeight = GriffGymTheme.dimens.touchTarget)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = AppInfoCopy.PRIVACY_POLICY,
                style = GriffGymTheme.typography.label,
                color = colors.primary,
                textAlign = TextAlign.Center,
            )
        }

        Text(
            text = GRIFF_GYM_BRAND,
            style = GriffGymTheme.typography.label,
            color = colors.textTertiary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = versionLabel(versionName, versionCode),
            style = GriffGymTheme.typography.dataSmall,
            color = colors.textTertiary,
            textAlign = TextAlign.Center,
        )

        if (message != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = message,
                style = GriffGymTheme.typography.bodySmall,
                color = colors.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * [AppInfoFooter] wired to the running build and to the lifter's browser.
 *
 * The version is read straight from the installed package rather than carried through a
 * ViewModel: it is a property of the APK on the phone, it cannot change while the screen is
 * open, and routing it through an injected provider would be three files of ceremony around
 * one synchronous call.
 */
@Composable
fun AppInfoFooter(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = rememberAppVersion()
    var linkFailed by rememberSaveable { mutableStateOf(false) }

    AppInfoFooter(
        versionName = version.name,
        versionCode = version.code,
        onPrivacyPolicyClick = {
            linkFailed = !context.openExternalUrl(PRIVACY_POLICY_URL)
        },
        modifier = modifier,
        message = if (linkFailed) AppInfoCopy.NO_BROWSER else null,
    )
}

/** The installed package's version, read once per [Context]. */
@Composable
internal fun rememberAppVersion(): AppVersionUi {
    val context = LocalContext.current
    return remember(context) { context.readAppVersion() }
}

private fun Context.readAppVersion(): AppVersionUi = try {
    // The PackageInfoFlags overload added in API 33 buys nothing for flags = 0, and the int
    // one still has to exist for everything below it.
    @Suppress("DEPRECATION")
    val info = packageManager.getPackageInfo(packageName, 0)
    AppVersionUi(
        // Nullable in the framework, and genuinely absent in some test and instant-app
        // packages.
        name = info.versionName.orEmpty(),
        code = PackageInfoCompat.getLongVersionCode(info),
    )
} catch (_: PackageManager.NameNotFoundException) {
    // The app asking the package manager about itself and being told it does not exist is
    // not a crashable offence — the footer simply has nothing to say.
    AppVersionUi(name = "", code = 0L)
}

/**
 * "Version 1.0 (1)", or just the build number when the package has no version name — never
 * "Version  (1)".
 */
private fun versionLabel(versionName: String, versionCode: Long): String =
    if (versionName.isBlank()) "Build ($versionCode)" else "Version $versionName ($versionCode)"

internal object AppInfoCopy {

    const val PRIVACY_POLICY = "PRIVACY POLICY"

    const val PRIVACY_POLICY_CLICK_LABEL = "Open the Griff Gym privacy policy"

    const val NO_BROWSER = "No app on this phone can open a web page. " +
        "The policy is at $PRIVACY_POLICY_URL."
}

@Preview(widthDp = 390, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun AppInfoFooterPreview() {
    GriffGymTheme {
        Column(
            Modifier
                .background(GriffGymTheme.colors.background)
                .padding(16.dp),
        ) {
            AppInfoFooter(
                versionName = "1.0",
                versionCode = 1L,
                onPrivacyPolicyClick = {},
            )
        }
    }
}

@Preview(widthDp = 390, backgroundColor = 0xFF14120C, showBackground = true, fontScale = 2f)
@Composable
private fun AppInfoFooterLargeFontPreview() {
    GriffGymTheme {
        Column(
            Modifier
                .background(GriffGymTheme.colors.background)
                .padding(16.dp),
        ) {
            AppInfoFooter(
                versionName = "1.4.2",
                versionCode = 118L,
                onPrivacyPolicyClick = {},
                message = AppInfoCopy.NO_BROWSER,
            )
        }
    }
}
