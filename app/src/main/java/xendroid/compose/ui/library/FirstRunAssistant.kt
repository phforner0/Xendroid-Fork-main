package xendroid.compose.ui.library

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xendroid.compose.core.AllFilesAccess
import xendroid.compose.core.EmulatorRuntime
import xendroid.compose.settings.ConfigStore
import xendroid.compose.settings.SettingsSchema
import xendroid.compose.settings.UiMode
import xendroid.compose.settings.UiModeStore

/**
 * L01: first-run assistant over the library. Checks this phone, then the choices a new user
 * needs: games folder, the games' language and region, profile, driver (optional) and the
 * interface mode. Works offline; every choice can be changed later.
 */
@Composable
fun FirstRunAssistant(
    folderReady: Boolean,
    onChooseFolder: () -> Unit,
    onOpenProfiles: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val checks = remember {
        FirstRun.deviceChecks(EmulatorRuntime.gpuDeviceName, Build.SUPPORTED_ABIS.toList(), Build.VERSION.SDK_INT)
    }
    val locale = remember { Locale.getDefault().let { FirstRun.guestLocale(it.language, it.country) } }
    var localeStatus by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableStateOf(if (UiModeStore.isChosen(context)) UiModeStore.read(context) else null) }

    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false,
        dismissOnClickOutside = false, dismissOnBackPress = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Welcome to XenDroid", style = MaterialTheme.typography.headlineSmall)
                Text("A few checks and choices. Everything can be changed later, and nothing here is sent anywhere.",
                    style = MaterialTheme.typography.bodyMedium)

                Section("This phone")
                checks.forEach { CheckLine(it) }

                Section("Your games")
                CheckLine(FirstRun.folderCheck(folderReady, AllFilesAccess.isSupported))
                if (!folderReady && AllFilesAccess.isSupported) {
                    OutlinedButton(onClick = onChooseFolder) { Text("Choose game folder") }
                }

                Section("Language and region in games")
                if (locale.any) {
                    Text("From this phone: " + listOfNotNull(locale.languageLabel?.let { "language $it" },
                        locale.countryLabel?.let { "region $it" }).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = {
                        localeStatus = "Saving…"
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    EmulatorRuntime.ensureLoaded()
                                    ConfigStore(context.applicationContext).editLiveConfig { config ->
                                        locale.languageValue?.let { config.putSetting(SettingsSchema.byKey.getValue("Console|user_language"), it) }
                                        locale.countryValue?.let { config.putSetting(SettingsSchema.byKey.getValue("Console|user_country"), it) }
                                    }
                                }
                            }
                            localeStatus = result.fold({ "Games will use them from the next launch." },
                                { "Could not save: ${it.message ?: it.javaClass.simpleName}" })
                        }
                    }) { Text("Use them for games") }
                } else {
                    Text("This phone's language has no console equivalent; games keep English unless changed in Settings.",
                        style = MaterialTheme.typography.bodyMedium)
                }
                localeStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

                Section("Profile")
                Text("Games save to a profile (gamertag). One called \"XenDroid\" is created when none exists; " +
                    "create or rename yours in Profiles.", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onOpenProfiles) { Text("Open Profiles") }

                Section("GPU driver (optional)")
                Text("The phone's own Vulkan driver is enough to start. A Turnip package can be installed later in " +
                    "Settings → Custom Vulkan driver, and switched back at any time.", style = MaterialTheme.typography.bodyMedium)

                Section("Interface")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UiMode.entries.forEach { option ->
                        FilterChip(selected = mode == option, onClick = {
                            mode = option
                            UiModeStore.write(context, option)
                        }, label = { Text(if (option == UiMode.PLAYER) "Player (recommended)" else "Developer") })
                    }
                }
                Text(if (mode == UiMode.DEVELOPER) "Every engine setting and the experimental options (debug builds only)."
                    else "Essential settings only; Developer can be turned on in Settings at any time.",
                    style = MaterialTheme.typography.bodySmall)

                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClose) { Text("Skip") }
                    Button(onClick = {
                        if (mode == null) UiModeStore.write(context, UiMode.PLAYER)
                        onClose()
                    }) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun CheckLine(check: FirstRun.Check) {
    val mark = when (check.status) {
        FirstRun.Status.OK -> "✓"
        FirstRun.Status.WARNING -> "!"
        FirstRun.Status.BLOCKED -> "✗"
    }
    val color = when (check.status) {
        FirstRun.Status.OK -> MaterialTheme.colorScheme.primary
        FirstRun.Status.WARNING -> MaterialTheme.colorScheme.tertiary
        FirstRun.Status.BLOCKED -> MaterialTheme.colorScheme.error
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(mark, color = color, style = MaterialTheme.typography.bodyLarge)
        Column {
            Text(check.title, style = MaterialTheme.typography.bodyLarge)
            Text(check.detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}
