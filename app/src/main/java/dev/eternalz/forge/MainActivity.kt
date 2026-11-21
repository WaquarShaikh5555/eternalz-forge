package dev.eternalz.forge

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.eternalz.forge.data.SecureVault
import dev.eternalz.forge.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Prevent repository contents and credentials appearing in screenshots or the recents snapshot.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            val vault = remember { runCatching { SecureVault(applicationContext) } }
            if (vault.isFailure) {
                ForgeTheme {
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.systemBarsPadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
                            Text("Workspace locked", style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(16.dp))
                            Text("Android could not decrypt your local vault. No credentials or drafts were erased. Restart the device and try again. If the Keystore key was lost, clearing app storage will remove all local accounts and drafts.")
                        }
                    }
                }
            } else {
                val vm: ForgeViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = ForgeViewModel(vault.getOrThrow()) as T
                })
                val state by vm.state.collectAsStateWithLifecycle()
                ForgeTheme(state.theme) { ForgeApp(state, vm) }
            }
        }
    }
}
