package ai.roadie.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*

/** Test-only host recreates its content when the vehicle shell changes window configuration. */
class RenderTestActivity : ComponentActivity() {
    companion object { var screen by mutableStateOf<(@Composable () -> Unit)?>(null) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { screen?.invoke() }
    }
}
