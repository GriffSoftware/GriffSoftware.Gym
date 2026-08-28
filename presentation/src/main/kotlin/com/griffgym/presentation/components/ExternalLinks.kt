package com.griffgym.presentation.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/**
 * The privacy policy, as published. The only web address the app knows about, kept in one
 * place so a change of host is a one-line change rather than a search.
 */
const val PRIVACY_POLICY_URL = "https://griffsoftware.com/privacy"

/**
 * Hands a web address to whatever the lifter uses to browse, and reports whether anything
 * took it.
 *
 * The app never renders the web itself: an in-app WebView would put a second, worse browser
 * in front of a document people are entitled to read in their own — with their own reader
 * mode, translation and text size — and would make Griff Gym responsible for a page it only
 * links to.
 *
 * `FLAG_ACTIVITY_NEW_TASK` because this is called with an application-scoped [Context] as
 * often as not, and leaving the browser inside the app's own task means a back press from
 * the policy lands somewhere in the training UI.
 *
 * Returns `false` — rather than crashing the app — on a device with no browser at all. That
 * is rare but real (a stripped OEM image, a managed profile with the browser disabled), and
 * a dead tap is still better handled than a stack trace.
 */
fun Context.openExternalUrl(url: String): Boolean = try {
    startActivity(
        Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    true
} catch (_: ActivityNotFoundException) {
    false
}
