package com.thotapalli.plex.core.api

import com.thotapalli.plex.core.api.dto.HomeUsersDto
import com.thotapalli.plex.core.api.dto.PinDto
import com.thotapalli.plex.core.api.dto.ResourceDto
import com.thotapalli.plex.core.api.dto.UserDto
import com.thotapalli.plex.core.api.mapper.toHomeUsers
import com.thotapalli.plex.core.api.mapper.toPlexAccount
import com.thotapalli.plex.core.api.mapper.toPlexServers
import com.thotapalli.plex.core.model.HomeUser
import com.thotapalli.plex.core.model.PlexAccount
import com.thotapalli.plex.core.model.PlexServer
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess

/**
 * Everything this client asks of plex.tv: the PIN sign in flow, the resource list and
 * Plex Home. See CLAUDE.md section 5.
 */
class PlexTvApi(
    http: PlexHttp,
    private val identityHeaders: PlexHeaders,
) {

    private val client = http.client

    /** Step 1. Creates a strong PIN and returns its id and user facing code. */
    suspend fun createPin(): PlexPin {
        val response = client.post("$PLEX_TV_BASE/api/v2/pins") {
            parameter("strong", "true")
            applyIdentity(null)
        }
        response.requireSuccess("create pin")
        val dto: PinDto = response.body()
        return PlexPin(id = dto.id, code = dto.code, authToken = dto.authToken)
    }

    /**
     * Step 3. One poll of a PIN. [PlexPin.authToken] stays null until the user approves in
     * the browser; a non-null token ends the poll.
     */
    suspend fun checkPin(pinId: Long): PlexPin {
        val response = client.get("$PLEX_TV_BASE/api/v2/pins/$pinId") {
            applyIdentity(null)
        }
        // An expired PIN answers 404. That is a normal end state, not a transport failure.
        if (response.status == HttpStatusCode.NotFound) {
            return PlexPin(id = pinId, code = "", authToken = null, expired = true)
        }
        response.requireSuccess("check pin $pinId")
        val dto: PinDto = response.body()
        return PlexPin(id = dto.id, code = dto.code, authToken = dto.authToken)
    }

    /**
     * Step 2. The URL to open in the system browser. The client identifier here must be
     * the same one sent on every request or the approval binds to a different device.
     */
    suspend fun authUrl(code: String): String {
        val clientIdentifier = identityHeaders.headers()[PlexHeaderNames.CLIENT_IDENTIFIER].orEmpty()
        return buildString {
            append(PLEX_AUTH_BASE)
            append("/auth#?clientID=")
            append(clientIdentifier.encodeURLParameter())
            append("&code=")
            append(code.encodeURLParameter())
            append("&context%5Bdevice%5D%5Bproduct%5D=")
            append(PLEX_PRODUCT.encodeURLParameter())
        }
    }

    /** The account behind an account token. */
    suspend fun account(accountToken: String): PlexAccount {
        val response = client.get("$PLEX_TV_BASE/api/v2/user") {
            applyIdentity(accountToken)
        }
        response.requireSuccess("account")
        return response.body<UserDto>().toPlexAccount()
    }

    /**
     * Step 5. Every resource the account can reach, filtered to media servers.
     *
     * A shared library arrives with owned false and carries its own accessToken, which is
     * the only token ever sent to that server.
     */
    suspend fun servers(accountToken: String): List<PlexServer> {
        val response = client.get("$PLEX_TV_BASE/api/v2/resources") {
            parameter("includeHttps", "1")
            parameter("includeRelay", "1")
            applyIdentity(accountToken)
        }
        response.requireSuccess("resources")
        return response.body<List<ResourceDto>>().toPlexServers()
    }

    /**
     * Plex Home users. More than one shows a picker, exactly one skips it silently.
     * See CLAUDE.md section 2.
     */
    suspend fun homeUsers(accountToken: String): List<HomeUser> {
        val response = client.get("$PLEX_TV_BASE/api/v2/home/users") {
            applyIdentity(accountToken)
        }
        // An account with no Plex Home answers 403. That is not an error, it is one user.
        if (response.status == HttpStatusCode.Forbidden) return emptyList()
        response.requireSuccess("home users")
        return response.body<HomeUsersDto>().toHomeUsers()
    }

    /** Switches to a Home user and returns that user's own token. */
    suspend fun switchHomeUser(accountToken: String, uuid: String, pin: String? = null): String {
        val response = client.post("$PLEX_TV_BASE/api/v2/home/users/$uuid/switch") {
            if (pin != null) parameter("pin", pin)
            applyIdentity(accountToken)
        }
        response.requireSuccess("switch home user")
        // The switch response carries the switched user's own token, which replaces the
        // account token for every later request.
        return response.body<SwitchResponse>().authToken
    }

    private suspend fun HttpRequestBuilder.applyIdentity(token: String?) {
        val identity = identityHeaders.headers()
        headers {
            identity.forEach { (name, value) -> append(name, value) }
            append("Accept", "application/json")
            if (token != null) append(PlexHeaderNames.TOKEN, token)
        }
    }
}

@kotlinx.serialization.Serializable
private data class SwitchResponse(val authToken: String = "")

/** A PIN in flight. [authToken] is null until the user approves it in the browser. */
data class PlexPin(
    val id: Long,
    val code: String,
    val authToken: String?,
    val expired: Boolean = false,
)

/** Every non-success from Plex surfaces as this, with the status attached. */
class PlexApiException(
    val status: Int,
    val operation: String,
    val body: String?,
) : Exception("Plex $operation failed with HTTP $status${body?.let { ": $it" }.orEmpty()}")

internal suspend fun HttpResponse.requireSuccess(operation: String) {
    if (!status.isSuccess()) {
        val text = runCatching { bodyAsText() }.getOrNull()
        throw PlexApiException(status.value, operation, text?.take(500))
    }
}
