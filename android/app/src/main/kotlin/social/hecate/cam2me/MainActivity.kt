package social.hecate.cam2me

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.macula.sdk.FfiKeyPair

/**
 * Skeleton entry point. Proves the FFI wiring end to end -- generating a
 * puzzle-hardened Ed25519 identity via [FfiKeyPair] and rendering its
 * node_id -- without yet touching the mesh (no connect/advertise/stream
 * here). Camera capture and a real mesh session are the next feature
 * pass, not this one.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val identity = FfiKeyPair.generate()
        val nodeId = identity.nodeId().joinToString("") { "%02x".format(it) }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Hecate Cam2Me", style = MaterialTheme.typography.headlineMedium)
                        Text("macula-rust-sdk-ffi is alive.")
                        Text("node_id: $nodeId")
                    }
                }
            }
        }
    }
}
