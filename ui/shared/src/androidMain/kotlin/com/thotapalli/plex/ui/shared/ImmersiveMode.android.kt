package com.thotapalli.plex.ui.shared

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView

@Composable
actual fun ImmersiveSystemBars(hidden: Boolean) {
    val view = LocalView.current
    LaunchedEffect(hidden) {
        val controller = view.hostActivity()?.window?.insetsController ?: return@LaunchedEffect
        if (hidden) {
            controller.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsets.Type.systemBars())
        } else {
            controller.show(WindowInsets.Type.systemBars())
        }
    }
    // Never leave the bars hidden behind us.
    DisposableEffect(Unit) {
        onDispose {
            view.hostActivity()?.window?.insetsController?.show(WindowInsets.Type.systemBars())
        }
    }
}

private fun View.hostActivity(): Activity? {
    var ctx: Context? = context
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
