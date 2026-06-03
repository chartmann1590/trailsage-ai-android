package com.charles.trailsage.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charles.trailsage.ui.navigation.MainNavGraph
import com.charles.trailsage.ui.navigation.SetupNavGraph
import com.charles.trailsage.ui.theme.TrailSageTheme

/**
 * App root. Implements the required setup gate: [SetupNavGraph] is shown until all
 * required offline assets are installed and verified; only then is [MainNavGraph]
 * reachable (prompt.txt startup behavior + RequiredSetupGate).
 */
@Composable
fun TrailSageApp(vm: AppViewModel = hiltViewModel()) {
    val state by vm.setup.collectAsStateWithLifecycle()
    TrailSageTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (state.status.setupComplete) {
                MainNavGraph(vm)
            } else {
                SetupNavGraph(vm, state)
            }
        }
    }
}
