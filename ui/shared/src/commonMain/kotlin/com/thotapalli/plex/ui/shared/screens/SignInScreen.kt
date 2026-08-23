package com.thotapalli.plex.ui.shared.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.thotapalli.plex.core.model.HomeUser
import com.thotapalli.plex.core.session.SignInState
import com.thotapalli.plex.ui.design.GlassRole
import com.thotapalli.plex.ui.design.PlexText
import com.thotapalli.plex.ui.design.PlexTheme
import com.thotapalli.plex.ui.design.Radius
import com.thotapalli.plex.ui.design.SizeClass
import com.thotapalli.plex.ui.design.Spacing
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import com.thotapalli.plex.ui.shared.AppLogo
import com.thotapalli.plex.ui.shared.LoadingIndicator
import com.thotapalli.plex.ui.shared.PrimaryButton
import com.thotapalli.plex.ui.shared.SecondaryButton
import com.thotapalli.plex.ui.shared.material
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
        // A quiet vertical wash so the mark sits in an atmosphere rather than on a flat plane.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to colours.surfaceElevated.copy(alpha = 0.5f),
                    0.5f to colours.background,
                    1f to colours.background,
                ),
            ),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.widthIn(max = 420.dp).padding(Spacing.lg),
        ) {
            AppLogo(size = 112.dp)

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
                    PrimaryButton(label = "Sign in with Plex", onClick = onSignIn)
                }

                SignInState.Starting -> {
                    LoadingIndicator(label = "Requesting a code")
                    SecondaryButton(label = "Cancel", onClick = onCancel)
                }

                is SignInState.AwaitingApproval -> {
                    if (PlexTheme.sizeClass == SizeClass.TELEVISION) {
                        // A television has no browser to open, so the app.plex.tv/auth URL is
                        // rendered as a QR the user scans with a phone (any browser signed into
                        // Plex, or the Plex app) — the poll below logs the TV in on approval.
                        // The short code is a manual fallback via plex.tv/link.
                        PlexText("Scan to sign in", style = PlexTheme.type.title)
                        PlexText(
                            text = "Scan with your phone's camera, or go to plex.tv/link " +
                                "and enter the code.",
                            colour = colours.textSecondary,
                        )
                        // A white plate so the code scans reliably regardless of the app theme.
                        Box(
                            Modifier
                                .background(Color.White, Radius.card)
                                .padding(Spacing.md),
                        ) {
                            Image(
                                painter = rememberQrCodePainter(state.authUrl),
                                contentDescription = "Plex sign-in QR code",
                                modifier = Modifier.size(240.dp),
                            )
                        }
                        Box(
                            Modifier
                                .background(colours.surfaceElevated, Radius.card)
                                .border(1.dp, colours.border, Radius.card)
                                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                        ) {
                            PlexText(state.code, style = PlexTheme.type.display)
                        }
                        LoadingIndicator(label = "Waiting for approval")
                        SecondaryButton(label = "Cancel", onClick = onCancel)
                    } else {
                        PlexText(
                            text = "Approve this device in your browser.",
                            colour = colours.textSecondary,
                        )
                        Box(
                            Modifier
                                .background(colours.surfaceElevated, Radius.card)
                                .border(1.dp, colours.border, Radius.card)
                                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                        ) {
                            PlexText(state.code, style = PlexTheme.type.title)
                        }
                        SecondaryButton(
                            label = "Open the browser again",
                            onClick = { onOpenUrl(state.authUrl) },
                        )
                        SecondaryButton(label = "Cancel", onClick = onCancel)
                    }
                }

                is SignInState.SignedIn -> LoadingIndicator(label = "Signed in as ${state.account.title}")
            }
        }
    }
}

/**
 * The Plex Home picker. Shown only when the account has more than one Home user; exactly
 * one skips it silently. See CLAUDE.md section 2.
 *
 * A Home user marked [HomeUser.protected] carries a PIN. Tapping it raises [PinDialog] and the
 * chosen user is only committed with the entered PIN; an unprotected user commits immediately with
 * a null PIN. [pinError], when supplied by the host, is surfaced inside the dialog so an incorrect
 * PIN can be shown and retried without dismissing it.
 */
@Composable
fun HomeUserPicker(
    users: List<HomeUser>,
    onSelect: (HomeUser, String?) -> Unit,
    modifier: Modifier = Modifier,
    pinError: String? = null,
) {
    val colours = PlexTheme.colours

    // The protected user awaiting PIN entry. Non-null means the dialog is open. On a correct PIN
    // the host navigates away from this screen, tearing the dialog down with it; on an incorrect
    // one the host re-renders with [pinError] and the dialog stays up for another attempt.
    var pinTarget by remember { mutableStateOf<HomeUser?>(null) }

    Box(
        modifier = modifier.fillMaxSize().background(colours.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.widthIn(max = 460.dp).padding(Spacing.lg),
        ) {
            PlexText("Who is watching?", style = PlexTheme.type.display)

            LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                items(users, key = { it.uuid }) { user ->
                    Box(
                        modifier = Modifier
                            .plexFocusable(
                                shape = Radius.card,
                                onClick = {
                                    if (user.protected) pinTarget = user else onSelect(user, null)
                                },
                            )
                            .background(colours.surface, Radius.card)
                            .border(1.dp, colours.border, Radius.card)
                            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    ) {
                        PlexText(user.title, style = PlexTheme.type.title)
                    }
                }
            }
        }
    }

    pinTarget?.let { user ->
        PinDialog(
            userTitle = user.title,
            error = pinError,
            onSubmit = { pin -> onSelect(user, pin) },
            onDismiss = { pinTarget = null },
        )
    }
}

/**
 * A small PIN-entry dialog for a protected Home user: a four-digit numeric field, obscured, with
 * Cancel and OK. Dressed in the app design system — a [GlassRole.SHEET] surface, [PlexText] and the
 * accent [PrimaryButton] — matching the grant-access and delete dialogs. OK stays enabled only for
 * a complete four-digit PIN, and pressing it does not dismiss the dialog: on success the host tears
 * this screen down, and on failure [error] renders here for a fresh attempt.
 */
@Composable
private fun PinDialog(
    userTitle: String,
    error: String?,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colours = PlexTheme.colours
    var pin by remember { mutableStateOf("") }
    val complete = pin.length == 4

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.material(GlassRole.SHEET, Radius.card),
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        titleContentColor = colours.textPrimary,
        textContentColor = colours.textSecondary,
        shape = Radius.card,
        title = { PlexText(text = "Enter PIN", style = PlexTheme.type.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PlexText(
                    text = "$userTitle is protected by a PIN.",
                    style = PlexTheme.type.body,
                    colour = colours.textSecondary,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .material(GlassRole.SECONDARY, Radius.glassSmall)
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                ) {
                    if (pin.isEmpty()) {
                        PlexText(
                            text = "4-digit PIN",
                            style = PlexTheme.type.body,
                            colour = colours.textSecondary,
                        )
                    }
                    BasicTextField(
                        value = pin,
                        // Digits only, capped at four, so the field can never hold an invalid PIN.
                        onValueChange = { entered ->
                            pin = entered.filter { it.isDigit() }.take(4)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        textStyle = LocalTextStyle.current.merge(PlexTheme.type.title)
                            .copy(color = colours.textPrimary),
                        cursorBrush = SolidColor(colours.accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (error != null) {
                    PlexText(
                        text = error,
                        style = PlexTheme.type.caption,
                        colour = colours.error,
                    )
                }
            }
        },
        confirmButton = {
            PrimaryButton(
                label = "OK",
                onClick = { onSubmit(pin) },
                enabled = complete,
            )
        },
        dismissButton = { SecondaryButton(label = "Cancel", onClick = onDismiss) },
    )
}
