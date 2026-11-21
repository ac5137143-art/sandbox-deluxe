package dev.sandbox.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dev.sandbox.app.ui.SandboxScreen
import dev.sandbox.app.ui.SandboxTheme

class MainActivity : ComponentActivity() {

    private val viewModel: SandboxViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            SandboxTheme {
                SandboxScreen(viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.engine.start()
    }

    override fun onStop() {
        super.onStop()
        viewModel.engine.stop()
    }
}
