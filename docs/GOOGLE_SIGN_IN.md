# Setting up Sign in with Google

The code on both sides (Android and the backend) is already implemented and wired to a
configuration value that does not exist yet. Nothing signs in with Google until you complete the
one-time setup below in Google Cloud Console — until then, the button on the app's entry screen
is present and tells the lifter honestly that Google sign-in isn't available.

You need **two** OAuth 2.0 client IDs in the same Google Cloud project: a **Web application**
client (used to check who a sign-in token was issued for) and an **Android** client (used to let
Google recognize the app itself). Only the Web client's ID ever appears in configuration — the
Android client's ID is never used in code, but Google refuses the sign-in request without it
existing.

---

## 1. Create or choose a Google Cloud project

Go to [console.cloud.google.com](https://console.cloud.google.com), and create a new project (or
reuse an existing one) — the free tier covers this completely.

## 2. Configure the OAuth consent screen

**APIs & Services → OAuth consent screen.** Choose **External** (unless you have a Google
Workspace organization and want to restrict sign-in to it — for a personal-use app, External is
right). Fill in the app name ("Griff Gym"), your email as support contact, and your email again
as developer contact. The default scopes (`openid`, `email`, `profile`) are all this needs —
don't add more. While the app is in "Testing" publishing status, only Google accounts you
explicitly add as test users can sign in; move it to "In production" once you're ready for
anyone to use it (Google's verification for these basic scopes is usually immediate).

## 3. Create the Web application client

**APIs & Services → Credentials → Create Credentials → OAuth client ID → Web application.**

- Name: anything recognizable, e.g. "GriffGym backend".
- No redirect URIs or JavaScript origins are needed — this client exists only so the backend has
  something to validate Google's tokens against, and to identify the app to Credential Manager.

Copy the resulting **Client ID** (looks like `123456789-abc...apps.googleusercontent.com`). This
is the value you'll put in two places below.

## 4. Create the Android client

**Same Credentials page → Create Credentials → OAuth client ID → Android.**

- Package name: `com.griffgym`
- SHA-1 certificate fingerprint: see below.

You need one Android client per signing key that will ever run the app — in practice, one for
your debug builds and, later, one for whatever key signs release builds.

### Getting the debug SHA-1

From the repository root:

```bash
./gradlew signingReport
```

Look for the `debug` variant's `SHA1:` line. (Equivalently: `keytool -list -v -keystore
~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`.)

Paste that into the Android client's SHA-1 field and save. You do **not** need this client's ID
anywhere — creating it is what makes Google accept sign-in requests from this app at all.

When you eventually sign release builds with a real keystore, come back and add a second Android
client with that keystore's SHA-1 — release builds won't be able to sign in with Google until you
do.

---

## 5. Configure the Android app

In `gradle.properties` at the repository root, uncomment and fill in the line already left there:

```properties
GRIFFGYM_GOOGLE_WEB_CLIENT_ID=123456789-abc...apps.googleusercontent.com
```

This is read by `infrastructure/build.gradle.kts` for both debug and release builds (same
mechanism as `GRIFFGYM_API_BASE_URL`). A release build refuses to compile without it set — a
release APK where the Google button would just silently fail is worse than a build that never
shipped. Debug builds work fine without it (the button reports "not available" instead).

## 6. Configure the backend

Same Web application Client ID, in `deploy.config.sh` at the repository root:

```bash
GOOGLE_WEB_CLIENT_ID="123456789-abc...apps.googleusercontent.com"
```

Then redeploy:

```bash
./deploy-backend.sh
```

This is not required for the backend to run — leaving it blank just makes `/api/v1/auth/google`
answer "not configured" for that one endpoint, same as today. Everything else keeps working
whether or not this step has been done yet.

---

## Verifying it end to end

1. Rebuild and install the app (a debug build is enough — Credential Manager only needs the
   Android client's package name + SHA-1 to exist, not a release signature, as long as you're
   testing with the debug-signed APK matching the debug client you registered).
2. On the entry screen, tap "SIGN IN WITH GOOGLE". You should see Google's account picker.
3. Pick an account. On success you land wherever a normal email/password sign-in would (backup
   prompt, restore prompt, or straight into the app).
4. If the picker shows "No accounts available" or fails immediately, double-check the Android
   client's package name and SHA-1 match the build you installed — this is the most common
   mistake, and Credential Manager's error messages for it are not specific.
