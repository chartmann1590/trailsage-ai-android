package com.charles.trailsage.ui.screens.route

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charles.trailsage.routing.GeocodeService
import com.charles.trailsage.ui.components.*

@Composable
fun CustomRouteBuilderScreen(
    onBack: () -> Unit,
    onStartTour: () -> Unit,
    vm: CustomRouteViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val start by vm.start.collectAsStateWithLifecycle()
    val end by vm.end.collectAsStateWithLifecycle()
    val startSug by vm.startSuggestions.collectAsStateWithLifecycle()
    val endSug by vm.endSuggestions.collectAsStateWithLifecycle()
    val working = state is CustomRouteViewModel.State.Working

    DetailScaffold("Build an adventure", onBack) {
        InfoCard(
            "AI audio road trip",
            "Enter where you're starting and where you're headed. TrailSage maps the drive, finds real attractions along the way from Wikipedia, and the on-device AI narrates each one — playing automatically as you reach it.",
        )

        AddressField("Starting from", start, startSug, enabled = !working, onChange = vm::onStartChange, onPick = vm::pickStart)
        AddressField("Heading to", end, endSug, enabled = !working, onChange = vm::onEndChange, onPick = vm::pickEnd)

        when (val s = state) {
            is CustomRouteViewModel.State.Idle ->
                PrimaryButton("Build adventure", enabled = start.isNotBlank() && end.isNotBlank()) { vm.generate() }

            is CustomRouteViewModel.State.Working ->
                SurfaceCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text(s.status, style = MaterialTheme.typography.bodyMedium)
                    }
                }

            is CustomRouteViewModel.State.Done -> {
                InfoCard(
                    "Adventure ready: ${s.name}",
                    "${s.stopCount} stops • narration ${if (s.usedAi) "written by on-device AI" else "from public sources (install the AI model for AI narration)"}. It plays automatically as you reach each stop.",
                )
                PrimaryButton("Start adventure", onClick = onStartTour)
                SecondaryButton("Build another", onClick = vm::reset)
            }

            is CustomRouteViewModel.State.Error -> {
                ErrorStateCard(s.message)
                PrimaryButton("Try again", enabled = start.isNotBlank() && end.isNotBlank()) { vm.generate() }
            }
        }
    }
}

@Composable
private fun AddressField(
    label: String,
    value: String,
    suggestions: List<GeocodeService.Suggestion>,
    enabled: Boolean,
    onChange: (String) -> Unit,
    onPick: (GeocodeService.Suggestion) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            singleLine = true,
            enabled = enabled,
            label = { Text(label) },
            leadingIcon = { Icon(Icons.Default.LocationOn, null) },
        )
        if (suggestions.isNotEmpty()) {
            Surface(
                tonalElevation = 3.dp,
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Column {
                    suggestions.forEach { suggestion ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(suggestion) }.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Default.LocationOn, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(suggestion.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
