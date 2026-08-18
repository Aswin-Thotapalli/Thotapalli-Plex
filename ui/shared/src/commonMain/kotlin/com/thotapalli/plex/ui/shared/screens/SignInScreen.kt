package com.thotapalli.plex.ui.shared.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.core.model.HomeUser
import com.thotapalli.plex.core.session.SignInState
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Radius
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.shared.plexFocusable

/**
 * Sign in: the application mark, one sentence, one button. Then a progress indicator and a
 * cancel action. A timeout returns to the initial state with a short message.
 * See CLAUDE.md section 14 item 1.
 */
@Composable
fun SignInScreen(
    state: SignInState?,
    onSignIn: () -> Unit,
    onCancel: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colours = PlexTheme.colours

    Box(
        modifier = modifier.fillMaxSize().background(colours.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.widthIn(max = 420.dp).padding(Spacing.lg),
        ) {
            AppMark()

            PlexText("Thotapalli Plex", style = PlexTheme.type.display)

            when (state) {
                null, is SignInState.Failed, SignInState.TimedOut -> {
                    PlexText(
                        text = "Sign in with your Plex account to reach your library.",
                        colour = colours.textSecondary,
                    )

                    if (state is SignInState.Failed) {
                        PlexText(
                            text = state.cause.message ?: "Sign in failed.",
                            colour = colours.error,
                            style = PlexTheme.type.caption,
                        )
                    }
                    if (state == SignInState.TimedOut) {
                        PlexText(
                            text = "That took too long. Try again.",
                            colour = colours.textSecondary,
                            style = PlexTheme.type.caption,
                        )
                    }

                    Spacer(Modifier.height(Spacing.xs))
                    ActionButton("Sign in with Plex", onSignIn)
                }

                SignInState.Starting -> {
                    PlexText("Requesting a code", colour = colours.textSecondary)
                    ActionButton("Cancel", onCancel, primary = false)
                }

                is SignInState.AwaitingApproval -> {
                    PlexText("Approve this device in your browser.", colour = colours.textSecondary)
                    Box(
                        Modifier
                            .background(colours.surfaceElevated, Radius.card)
                            .border(1.dp, colours.border, Radius.card)
                            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    ) {
                        PlexText(state.code, style = PlexTheme.type.title)
                    }
                    ActionButton("Open the browser again", { onOpenUrl(state.authUrl) }, primary = false)
                    ActionButton("Cancel", onCancel, primary = false)
                }

                is SignInState.SignedIn -> PlexText("Signed in as ${state.account.title}")
            }
        }
    }
}

/**
 * The Plex Home picker. Shown only when the account has more than one Home user; exactly
 * one skips it silently. See CLAUDE.md section 2.
 */
@Composable
fun HomeUserPicker(
    users: List<HomeUser>,
    onSelect: (HomeUser) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colours = PlexTheme.colours

    Box(
        modifier = modifier.fillMaxSize().background(colours.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.widthIn(max = 420.dp).padding(Spacing.lg),
        ) {
            PlexText("Who is watching?", style = PlexTheme.type.display)

            LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                items(users, key = { it.uuid }) { user ->
                    Box(
                        modifier = Modifier
                            .plexFocusable(shape = Radius.card, onClick = { onSelect(user) })
                            .background(colours.surface, Radius.card)
                            .border(1.dp, colours.border, Radius.card)
                            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    ) {
                        PlexText(user.title, style = PlexTheme.type.title)
                    }
                }
            }
        }
    }
}

/**
 * The mark from CLAUDE.md section 15: a rounded plate carrying a horizontal bar above a
 * play triangle, which read together as the letter T and as a play control.
 */
@Composable
private fun AppMark() {
    val colours = PlexTheme.colours
    Box(
        modifier = Modifier.size(96.dp).background(colours.surfaceElevated, Radius.sheet),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(48.dp, 8.dp).background(colours.accent, Radius.pill))
            Spacer(Modifier.height(Spacing.xs))
            Box(Modifier.size(28.dp, 30.dp).background(colours.accent, Radius.poster))
        }
    }
}

@Composable
private fun ActionButton(label: String, onClick: () -> Unit, primary: Boolean = true) {
    val colours = PlexTheme.colours
    Box(
        modifier = Modifier
            .plexFocusable(shape = Radius.pill, onClick = onClick, scaleOnFocus = false)
            .background(if (primary) colours.accent else colours.surface, Radius.pill)
            .border(1.dp, if (primary) colours.accent else colours.border, Radius.pill)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
    ) {
        PlexText(
            text = label,
            style = PlexTheme.type.label,
            colour = when {
                primary && colours.isDark -> colours.background
                primary -> colours.surface
                else -> colours.textSecondary
            },
        )
    }
}
