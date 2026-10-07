package com.debloated.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.debloated.player.ui.DebloatedPlayerRoot
import com.debloated.player.ui.LibraryViewModel
import com.debloated.player.ui.theme.DebloatedPlayerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: LibraryViewModel by viewModels { LibraryViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DebloatedPlayerTheme {
                DebloatedPlayerRoot(viewModel)
            }
        }
    }
}
