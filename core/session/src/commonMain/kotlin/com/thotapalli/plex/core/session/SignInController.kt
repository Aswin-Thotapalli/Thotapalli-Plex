package com.thotapalli.plex.core.session

import com.thotapalli.plex.core.api.PlexTvApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * The PIN sign in flow from CLAUDE.md section 5.
 *
 * 1. Create a strong PIN.
 * 2. The caller opens [SignInState.AwaitingApproval.authUrl] in the system browser.
 * 3. Poll every 1000 ms, giving up after 300 s.
 * 4. Persist the token encrypted.
 */
class SignInController(
    private val api: PlexTvApi,
    private val tokens: TokenStore,
) {

    /**
     * Runs one sign in attempt end to end.
     *
     * Emits [SignInState.AwaitingApproval] as soon as the browser URL is known, so the
     * caller can open it while the poll is already running, then a terminal state.
     */
    fun signIn(): Flow<SignInState> = flow {
        emit(SignInState.Starting)

        val pin = api.createPin()
        val url = api.authUrl(pin.code)
        emit(SignInState.AwaitingApproval(code = pin.code, authUrl = url))

        var waitedMs = 0L
        while (waitedMs < TIMEOUT_MS) {
            delay(POLL_INTERVAL_MS)
            waitedMs += POLL_INTERVAL_MS

            val polled = runCatching { api.checkPin(pin.id) }.getOrElse { error ->
                emit(SignInState.Failed(error))
                return@flow
            }

            if (polled.expired) {
                emit(SignInState.TimedOut)
                return@flow
            }

            val token = polled.authToken
            if (!token.isNullOrBlank()) {
                val account = runCatching { api.account(token) }.getOrElse { error ->
                    emit(SignInState.Failed(error))
                    return@flow
                }
                tokens.storeAccountToken(token, account)
                emit(SignInState.SignedIn(token = token, account = account))
                return@flow
            }
        }

        emit(SignInState.TimedOut)
    }

    private companion object {
        const val POLL_INTERVAL_MS = 1_000L
        const val TIMEOUT_MS = 300_000L
    }
}

sealed interface SignInState {
    data object Starting : SignInState

    /** The browser must be pointed at [authUrl]. [code] is shown as a fallback. */
    data class AwaitingApproval(val code: String, val authUrl: String) : SignInState

    data class SignedIn(
        val token: String,
        val account: com.thotapalli.plex.core.model.PlexAccount,
    ) : SignInState

    /** 300 s elapsed, or the PIN expired. The screen returns to its initial state. */
    data object TimedOut : SignInState

    data class Failed(val cause: Throwable) : SignInState
}
