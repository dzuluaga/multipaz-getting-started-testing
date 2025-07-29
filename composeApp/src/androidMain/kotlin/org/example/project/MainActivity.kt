package org.example.project

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.fragment.app.FragmentActivity
import org.multipaz.context.initializeApplication
import org.multipaz.prompt.AndroidPromptModel

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        initializeApplication(this.applicationContext)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App(AndroidPromptModel())
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(AndroidPromptModel())
}