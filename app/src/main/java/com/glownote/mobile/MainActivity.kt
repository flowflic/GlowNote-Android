package com.glownote.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.glownote.mobile.ui.GlowNoteApp
import com.glownote.mobile.ui.GlowNoteTheme
import com.glownote.mobile.ui.GlowNoteViewModel

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<GlowNoteViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GlowNoteTheme {
                GlowNoteApp(viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.syncOnAppForeground()
    }
}
