package com.thotapalli.plex.desktop.harness

import com.thotapalli.plex.core.api.ConnectionSelector
import com.thotapalli.plex.core.api.PlexTvApi
import com.thotapalli.plex.core.api.PlexHttp
import com.thotapalli.plex.core.session.DpapiSecureStore
import com.thotapalli.plex.core.session.FileKeyValueStore
import com.thotapalli.plex.core.session.IdentityHeaderProvider
import com.thotapalli.plex.core.session.PlexSession
import com.thotapalli.plex.core.session.ServerDirectory
import com.thotapalli.plex.core.session.SignInController
import com.thotapalli.plex.core.session.SignInState
import com.thotapalli.plex.core.session.StorageKeys
import com.thotapalli.plex.core.session.TokenStore
import com.thotapalli.plex.core.session.currentDeviceInfo
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import java.awt.Desktop
import java.net.URI

/**
 * The command line harness for CLAUDE.md section 16 phase 2.
 *
 * It exercises the real sign in flow against plex.tv and the real connection probe against
 * whatever servers the account can reach. Nothing here is used by the shipped application;
 * it exists so each phase 2 step has an observable output.
 *
 * Run with:
 *   gradlew :app:desktop:harness --args="signin"
 *   gradlew :app:desktop:harness --args="servers"
 *   gradlew :app:desktop:harness --args="token"
 *   gradlew :app:desktop:harness --args="identity"
 *   gradlew :app:desktop:harness --args="libraries"
 *   gradlew :app:desktop:harness --args="continue"
 *   gradlew :app:desktop:harness --args="search blade"
 */
fun main(argv: Array<String>) {
    val command = argv.firstOrNull() ?: "help"

    val plain = FileKeyValueStore()
    val secure = DpapiSecureStore()
    val device = currentDeviceInfo(appVersion = "0.1.0")
    val identity = IdentityHeaderProvider(plain, device)
    val http = PlexHttp.create()
    val api = PlexTvApi(http, identity)
    val tokens = TokenStore(secure, plain)

    try {
        when (command) {
            "identity" -> printIdentity(identity, device.toString())
            "signin" -> runBlocking { signIn(api, tokens) }
            "servers" -> runBlocking { servers(api, identity, tokens, plain) }
            "token" -> readBackToken(tokens, identity)
            "libraries", "continue", "search" -> runBlocking {
                LibraryHarness(identity, tokens, plain).run(command, argv.drop(1).joinToString(" ").ifBlank { null })
            }
            "signout" -> {
                tokens.signOut()
                println("Signed out. The client identifier is deliberately kept:")
                println("  ${identity.clientIdentifier}")
            }
            else -> printHelp()
        }
    } finally {
        http.close()
    }
}

private fun printHelp() {
    println(
        """
        Thotapalli Plex harness

          identity   print the identity headers sent on every request
          signin     run the PIN sign in flow and store the account token
          servers    list servers and the connection chosen for each
          token      read the stored token back, proving it survived a restart
          signout    clear the token, keeping the client identifier
          libraries  list every library and a sample of its contents
          continue   print Continue Watching with resume positions
          search <q> search across every library
        """.trimIndent(),
    )
}

/** Phase 2 step 1, observable form. The unit test is the actual verification. */
private fun printIdentity(identity: IdentityHeaderProvider, device: String) = runBlocking {
    println("Device: $device")
    println()
    identity.headers().forEach { (name, value) -> println("$name: $value") }
    println("X-Plex-Session-Identifier: ${identity.newSessionIdentifier()}  (per playback session)")
}

/** Phase 2 step 2. Prints an account token after browser approval. */
private suspend fun signIn(api: PlexTvApi, tokens: TokenStore) {
    val controller = SignInController(api, tokens)

    controller.signIn().collect { state ->
        when (state) {
            SignInState.Starting -> println("Requesting a PIN from plex.tv ...")

            is SignInState.AwaitingApproval -> {
                println()
                println("Approve this device in the browser.")
                println("  code: ${state.code}")
                println("  url:  ${state.authUrl}")
                println()
                openBrowser(state.authUrl)
                println("Polling every 1000 ms, giving up after 300 s ...")
            }

            is SignInState.SignedIn -> {
                println()
                println("Signed in.")
                println("  account:      ${state.account.title} (${state.account.username})")
                println("  account id:   ${state.account.id}")
                println("  account token: ${state.token}")
                println()
                println("The token is now stored encrypted with DPAPI under")
                println("  ${DpapiSecureStore.defaultDirectory()}")
            }

            SignInState.TimedOut ->
                println("Timed out after 300 s with no approval.")

            is SignInState.Failed -> {
                println("Sign in failed: ${state.cause.message}")
                state.cause.printStackTrace()
            }
        }
    }
}

/** Phase 2 step 3. Prints server name, chosen connection URI and whether it is local. */
private suspend fun servers(
    api: PlexTvApi,
    identity: IdentityHeaderProvider,
    tokens: TokenStore,
    plain: FileKeyValueStore,
) {
    val token = tokens.accountToken()
    if (token.isNullOrBlank()) {
        println("Not signed in. Run the signin command first.")
        return
    }

    val http = PlexHttp.create()
    try {
        val selector = ConnectionSelector(
            http = http,
            identityHeaders = identity,
            nowMs = System::currentTimeMillis,
            elapsedMs = { System.nanoTime() / 1_000_000 },
        )
        val directory = ServerDirectory(
            api = PlexTvApi(http, identity),
            selector = selector,
            store = plain,
            nowMs = System::currentTimeMillis,
        )
        val session = PlexSession(PlexTvApi(http, identity), tokens, directory, identity)

        val homeUsers = session.homeUsers()
        println("Plex Home users: ${homeUsers.size}")
        homeUsers.forEach { println("  ${it.title}${if (it.admin) "  (admin)" else ""}") }
        println(
            if (homeUsers.size > 1) "  more than one, so a picker is shown"
            else "  one or none, so the picker is skipped silently",
        )
        println()

        val all = session.servers(refresh = true)
        if (all.isEmpty()) {
            println("No servers on this account.")
            return
        }

        for (server in all) {
            println("Server: ${server.name}")
            println("  machine identifier: ${server.machineIdentifier}")
            println("  owned:              ${server.owned}")
            println("  connections offered: ${server.connections.size}")
            server.connections.forEach {
                println("    ${it.uri}  local=${it.local} relay=${it.relay}")
            }

            val chosen = session.connection(server)
            if (chosen == null) {
                println("  chosen: none answered inside the 3000 ms probe")
            } else {
                println("  chosen connection:  ${chosen.uri}")
                println("  local:              ${chosen.local}")
                println("  relay:              ${chosen.relay}")
                println("  round trip:         ${chosen.roundTripMs} ms")
            }
            println()
        }

        val active = session.activeServer()
        println("Active server: ${active?.name ?: "none"}")
    } finally {
        http.close()
    }
}

/** Phase 2 step 4. Reads the stored token back, which is only possible after a restart. */
private fun readBackToken(tokens: TokenStore, identity: IdentityHeaderProvider) {
    println("Reading persisted state in a fresh process.")
    println()
    println("  client identifier: ${identity.clientIdentifier}")
    println("  (from ${FileKeyValueStore.defaultFile()})")
    println()

    val token = tokens.accountToken()
    if (token.isNullOrBlank()) {
        println("  account token: absent. Run the signin command first.")
        return
    }

    println("  signed in as: ${tokens.signedInUsername()}")
    println("  account token: ${token.take(6)}... (${token.length} characters)")
    println("  decrypted with DPAPI CryptUnprotectData, scoped to the current user")
    println()
    println("Read back successfully. Storage key: ${StorageKeys.ACCOUNT_TOKEN}")
}

private fun openBrowser(url: String) {
    val opened = runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
            true
        } else {
            false
        }
    }.getOrDefault(false)

    if (!opened) println("Could not open a browser automatically. Open the url above by hand.")
}
