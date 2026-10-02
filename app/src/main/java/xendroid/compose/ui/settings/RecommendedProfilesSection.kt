package xendroid.compose.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xendroid.compose.compatibility.DeviceFacts
import xendroid.compose.compatibility.LoadedProfile
import xendroid.compose.compatibility.ProfileSource
import xendroid.compose.compatibility.SettingsProfiles
import xendroid.compose.compatibility.SettingsProfiles.Kind
import xendroid.compose.settings.GameSettingsViewModel.ProfilePreview
import xendroid.compose.settings.GameSettingsViewModel.ProfilesState

/** C05: the game's recommended settings, where they come from and why, and the way back. */
@Composable
fun RecommendedProfilesCard(
    state: ProfilesState,
    onPreview: (LoadedProfile) -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Recommended settings", style = MaterialTheme.typography.titleSmall)
            state.applied?.let { applied ->
                val date = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(applied.appliedAt))
                Text("Applied “${applied.profileName}” on $date: ${applied.written.size} setting(s) for this game.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onRestore) { Text("Restore previous settings") }
            }
            state.offered.forEach { profile -> ProfileBlock(profile, state.facts, enabled = state.applied == null, onPreview) }
            if (state.notHere.isNotEmpty()) {
                Text("Not offered on this phone:", style = MaterialTheme.typography.bodySmall, color = muted)
                state.notHere.forEach { (profile, why) ->
                    Text("• ${profile.profile.name}: ${why.joinToString("; ")}", style = MaterialTheme.typography.bodySmall, color = muted)
                }
            }
            if (state.skipped.isNotEmpty()) {
                Text("Skipped profile files: " + state.skipped.take(3).joinToString("; ") +
                    (if (state.skipped.size > 3) " (and ${state.skipped.size - 3} more)" else ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ProfileBlock(loaded: LoadedProfile, facts: DeviceFacts?, enabled: Boolean, onPreview: (LoadedProfile) -> Unit) {
    val profile = loaded.profile
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(profile.name, style = MaterialTheme.typography.bodyLarge)
        Text(when (loaded.source) {
            ProfileSource.BUNDLED -> "From XenDroid, verified with the test below"
            ProfileSource.LOCAL -> "From the file ${loaded.origin} on this phone; not reviewed by XenDroid"
        }, style = MaterialTheme.typography.bodySmall, color = muted)
        Text(profile.reason, style = MaterialTheme.typography.bodySmall)
        profile.evidence.firstOrNull()?.let { test ->
            val elsewhere = facts != null && !SettingsProfiles.testedOnThisGpu(profile, facts)
            Text("Tested: ${test.result} on ${test.gpu}${if (test.driver.isNotBlank()) ", ${test.driver}" else ""}, " +
                "build ${test.build} (${test.date})" + (if (elsewhere) ". Not tested on this GPU." else ""),
                style = MaterialTheme.typography.bodySmall, color = muted)
        }
        TextButton(onClick = { onPreview(loaded) }, enabled = enabled) { Text("Preview changes") }
    }
}

/** Every setting the plan touches, before anything is written. */
@Composable
fun ProfilePreviewDialog(preview: ProfilePreview, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val writes = preview.plan.writes.size
    val restore = preview is ProfilePreview.Restore
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(when (preview) {
                is ProfilePreview.Apply -> "Apply “${preview.profile.profile.name}”?"
                is ProfilePreview.Restore -> "Restore previous settings?"
            })
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                preview.plan.lines.forEach { line ->
                    Text(when (line.kind) {
                        Kind.CHANGE -> "${line.title}: ${line.now} → ${line.target}"
                        Kind.PIN -> "${line.title}: ${line.target} (as now; kept for this game)"
                        Kind.SAME -> "${line.title}: ${line.target} (already set for this game)"
                        Kind.YOURS -> if (restore) "${line.title}: stays ${line.now}; you changed it after the profile"
                            else "${line.title}: stays ${line.now}, as you chose (the profile suggests ${line.target})"
                    }, style = MaterialTheme.typography.bodySmall)
                }
                Text(if (restore) "Only settings the profile wrote and you did not change since go back."
                    else "Only this game changes, from its next start. Settings you chose for it stay, and you can restore the previous ones later.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = restore || writes > 0) {
                Text(when {
                    restore -> "Restore"
                    writes == 0 -> "Nothing to change"
                    else -> "Apply $writes change(s)"
                })
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
