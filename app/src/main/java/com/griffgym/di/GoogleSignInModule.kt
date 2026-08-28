package com.griffgym.di

import com.griffgym.infrastructure.BuildConfig
import com.griffgym.presentation.account.GoogleWebClientId
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The one binding that has to be made in `:app`, because it is the only module that can see
 * both sides of it.
 *
 * The Google web client id is build configuration, and build configuration lives in
 * `:infrastructure` beside the API base URL it is paired with — the backend validates ID
 * tokens against this same id, so the two belong to the same environment and drift apart at
 * their peril. Its consumer, though, is `CredentialManagerGoogleSignInLauncher` in
 * `:presentation`, which does not depend on `:infrastructure` and must not start.
 *
 * `:app` is the composition root and depends on both, so it is where the two meet. The
 * alternative — `:infrastructure` providing a `@Named("...")` string that `:presentation`
 * injects by the same literal — also works in Hilt, but it trades a compiler-checked
 * qualifier for a string typed twice in two modules that cannot see each other.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object GoogleSignInModule {

    @Provides
    @Singleton
    @GoogleWebClientId
    fun provideGoogleWebClientId(): String = BuildConfig.GOOGLE_WEB_CLIENT_ID
}
