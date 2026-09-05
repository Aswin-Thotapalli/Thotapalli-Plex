package com.thotapalli.plex.ui.shared.tv

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.core.model.HomeUser
import com.thotapalli.plex.core.session.SignInState
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Spacing
import com.thotapalli.plex.ui.shared.AppLogo
import io.github.alexzhirkevich.qrose.rememberQrCodePainter

/**
 * Sign in, for a screen with no keyboard and no browser worth using: the approval link is shown
 * as a QR code to scan with a phone, with the code beneath as a fallback. One button to start,
 * one to cancel, both D-pad targets. See CLAUDE.md section 5 (the PIN flow) and 14 item 1.
 */
@Composable
internal fun TvSignIn(
    state: SignInState?,
    onSignIn: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = rememberTvZone("sign-in")
    TvFirstFocus(zone, key = state?.let { it::class.simpleName })

    TvZone(zone) {
        Box(
            modifier.fillMaxSize().background(TvPalette.ground).tvZone(zone),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xxl),
                modifier = Modifier.padding(TvDims.overscanX),
            ) {
                Column(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    modifier = Modifier.widthIn(max = 520.dp),
                ) {
                    AppLogo(size = 96.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlexText("Thotapalli", style = PlexTheme.type.display, colour = TvPalette.text)
                        Spacer(Modifier.width(Spacing.xs))
                        PlexText("Plex", style = PlexTheme.type.display, colour = TvPalette.gold)
                    }
                    when (state) {
                        null, is SignInState.Failed, SignInState.TimedOut -> {
                            PlexText(
                                text = "Sign in with your Plex account to reach your library.",
                                style = PlexTheme.type.body,
                                colour = TvPalette.textDim,
                            )
                            if (state is SignInState.Failed) {
                                PlexText(
                                    text = state.cause.message ?: "Sign in failed.",
                                    style = PlexTheme.type.caption,
                                    colour = TvPalette.error,
                                )
                            }
                            if (state == SignInState.TimedOut) {
                                PlexText(
                                    text = "That took too long. Try again.",
                                    style = PlexTheme.type.caption,
                                    colour = TvPalette.textDim,
                                )
                            }
                            Spacer(Modifier.height(Spacing.xs))
                            TvButton(label = "Sign in with Plex", key = "sign-in", primary = true, onClick = onSignIn)
                        }

                        SignInState.Starting -> {
                            PlexText(text = "Contacting Plex…", style = PlexTheme.type.body, colour = TvPalette.textDim)
                            TvButton(label = "Cancel", key = "cancel", onClick = onCancel)
                        }

                        is SignInState.AwaitingApproval -> {
                            PlexText(
                                text = "Scan the code with your phone and approve this device.",
                                style = PlexTheme.type.body,
                                colour = TvPalette.textDim,
                            )
                            PlexText(
                                text = "Or open plex.tv/link and enter",
                                style = PlexTheme.type.caption,
                                colour = TvPalette.textMuted,
                            )
                            PlexText(
                                text = state.code,
                                style = PlexTheme.type.title,
                                colour = TvPalette.gold,
                            )
                            Spacer(Modifier.height(Spacing.xs))
                            TvButton(label = "Cancel", key = "cancel", onClick = onCancel)
                        }

                        is SignInState.SignedIn -> {
                            PlexText(text = "Signed in.", style = PlexTheme.type.body, colour = TvPalette.textDim)
                        }
                    }
                }
                if (state is SignInState.AwaitingApproval) {
                    Box(
                        Modifier
                            .size(300.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .padding(Spacing.md),
                    ) {
                        Image(
                            painter = rememberQrCodePainter(state.authUrl),
                            contentDescription = "Sign-in QR code",
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * "Who is watching?" — one row per Home user. A protected user opens a PIN pad the remote can
 * drive: digits on a grid, a backspace and OK, with the entry shown as dots.
 */
@Composable
internal fun TvHomeUserPicker(
    users: List<HomeUser>,
    onSelect: (HomeUser, String?) -> Unit,
    modifier: Modifier = Modifier,
    pinError: String? = null,
) {
    val zone = rememberTvZone("home-users")
    var pinTarget by remember { mutableStateOf<HomeUser?>(null) }
    TvFirstFocus(zone, enabled = pinTarget == null)

    TvZone(zone) {
        Box(
            modifier.fillMaxSize().background(TvPalette.ground).tvZone(zone),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.widthIn(max = 560.dp).padding(TvDims.overscanX),
            ) {
                PlexText("Who is watching?", style = PlexTheme.type.display, colour = TvPalette.text)
                Spacer(Modifier.height(Spacing.sm))
                users.forEach { user ->
                    TvListRow(
                        title = user.title,
                        key = user.uuid,
                        detail = if (user.protected) "PIN protected" else null,
                        onClick = { if (user.protected) pinTarget = user else onSelect(user, null) },
                    )
                }
            }
        }
    }

    pinTarget?.let { user ->
        TvPinDialog(
            userTitle = user.title,
            error = pinError,
            onSubmit = { pin -> onSelect(user, pin) },
            onDismiss = { pinTarget = null },
        )
    }
}

/** A four-digit PIN pad for the remote. */
@Composable
internal fun TvPinDialog(
    userTitle: String,
    error: String?,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    TvDialog(onDismiss = onDismiss, width = 420.dp) {
        PlexText(text = "Enter PIN", style = PlexTheme.type.title, colour = TvPalette.text)
        PlexText(text = "$userTitle is protected by a PIN.", style = PlexTheme.type.body, colour = TvPalette.textDim)
        Box(Modifier.fillMaxWidth().padding(vertical = Spacing.xs), contentAlignment = Alignment.Center) {
            PlexText(
                text = buildString { repeat(4) { append(if (it < pin.length) "●" else "○"); append("  ") } }.trim(),
                style = PlexTheme.type.display,
                colour = TvPalette.gold,
            )
        }
        if (error != null) {
            PlexText(text = error, style = PlexTheme.type.caption, colour = TvPalette.error)
        }
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "OK"))
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                row.forEach { label ->
                    TvButton(
                        label = label,
                        key = "pin-$label",
                        primary = label == "OK",
                        enabled = label != "OK" || pin.length == 4,
                        onClick = {
                            when (label) {
                                "⌫" -> pin = pin.dropLast(1)
                                "OK" -> onSubmit(pin)
                                else -> if (pin.length < 4) pin += label
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
