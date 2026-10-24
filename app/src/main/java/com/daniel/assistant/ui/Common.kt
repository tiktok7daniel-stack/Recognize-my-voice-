package com.daniel.assistant.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.daniel.assistant.AppContainer
import com.daniel.assistant.NabraApp

@Composable
fun rememberContainer(): AppContainer {
    val ctx = LocalContext.current
    return remember { (ctx.applicationContext as NabraApp).container }
}
