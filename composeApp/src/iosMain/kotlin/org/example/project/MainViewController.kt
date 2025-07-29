package org.example.project

import androidx.compose.ui.window.ComposeUIViewController
import org.multipaz.prompt.IosPromptModel

fun MainViewController() = ComposeUIViewController { App(IosPromptModel()) }