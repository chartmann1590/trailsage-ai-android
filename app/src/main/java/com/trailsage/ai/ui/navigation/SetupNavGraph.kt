package com.charles.trailsage.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.charles.trailsage.domain.SetupState
import com.charles.trailsage.ui.AppViewModel
import com.charles.trailsage.ui.SetupUiState
import com.charles.trailsage.ui.screens.settings.VoiceSettingsViewModel
import com.charles.trailsage.ui.screens.setup.*

/**
 * Onboarding graph. Drives the required setup flow + state machine; the main app stays
 * unreachable until [AppViewModel.verify] flips setupComplete (RequiredSetupGate).
 */
@Composable
fun SetupNavGraph(vm: AppViewModel, state: SetupUiState) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "splash") {
        composable("splash") { SplashScreen { nav.navigate("welcome") } }
        composable("welcome") {
            WelcomeSetupScreen { vm.mark(SetupState.NOT_STARTED); nav.navigate("why") }
        }
        composable("why") { WhySetupScreen { vm.checkDevice(); nav.navigate("device") } }
        composable("device") {
            DeviceCheckScreen(state) {
                nav.navigate(if (state.compatibility?.supported == true) "required" else "failed")
            }
        }
        composable("required") {
            RequiredDownloadsScreen(state, vm::download) { nav.navigate("ai") }
        }
        composable("ai") { AiModelDownloadScreen(state, vm::download) { nav.navigate("tts") } }
        composable("tts") { TtsEngineDownloadScreen(state, vm::download) { nav.navigate("voice") } }
        composable("voice") {
            val voiceVm: VoiceSettingsViewModel = hiltViewModel()
            val voices by voiceVm.voices.collectAsStateWithLifecycle()
            NaturalVoiceSetupScreen(
                state = state,
                onDownload = vm::download,
                onPreview = { voices.firstOrNull { it.installed }?.let(voiceVm::preview) },
                onNext = { nav.navigate("sample") },
            )
        }
        composable("sample") { SampleTourSetupScreen { vm.installSample(); nav.navigate("verify") } }
        composable("verify") {
            SetupVerificationScreen(state, onVerify = { vm.verify() }, onFailed = { nav.navigate("failed") })
        }
        composable("failed") {
            SetupFailedScreen(
                state.status.lastError ?: "Required production assets are still missing.",
            ) { nav.navigate("required") }
        }
    }
}
