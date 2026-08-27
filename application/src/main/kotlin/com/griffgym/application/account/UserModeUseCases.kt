package com.griffgym.application.account

import com.griffgym.domain.model.UserMode
import com.griffgym.domain.repository.UserModeRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetUserModeUseCase @Inject constructor(
    private val userModeRepository: UserModeRepository,
) {
    operator fun invoke(): Flow<UserMode> = userModeRepository.observeUserMode()

    suspend fun current(): UserMode = userModeRepository.getUserMode()
}

/**
 * Records that the lifter chose to keep everything on this phone.
 *
 * Nothing else happens here — no data is moved, nothing is disabled. Local-only is the app
 * working exactly as it always has, with the decision written down so the warning screen is
 * not shown again.
 */
class ContinueLocallyUseCase @Inject constructor(
    private val userModeRepository: UserModeRepository,
) {
    suspend operator fun invoke() = userModeRepository.chooseLocalOnly()
}
