package xendroid.compose.ui.library

import xendroid.compose.R
import androidx.compose.ui.res.stringResource
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
                Text(stringResource(R.string.fr_welcome), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.fr_intro),
                    style = MaterialTheme.typography.bodyMedium)

                Section(stringResource(R.string.fr_this_phone))
                checks.forEach { CheckLine(it) }

                Section(stringResource(R.string.fr_your_games))
                CheckLine(FirstRun.folderCheck(folderReady, AllFilesAccess.isSupported))
                if (!folderReady && AllFilesAccess.isSupported) {
                    OutlinedButton(onClick = onChooseFolder) { Text(stringResource(R.string.fr_choose_folder)) }
                }

                Section(stringResource(R.string.fr_locale))
                if (locale.any) {
                    val language = locale.languageLabel?.let { stringResource(R.string.fr_language, it) }
                    val region = locale.countryLabel?.let { stringResource(R.string.fr_region, it) }
                    Text(stringResource(R.string.fr_from_phone, listOfNotNull(language, region).joinToString(" · ")),
                        style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = {
                        localeStatus = context.getString(R.string.fr_saving)
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
                            localeStatus = result.fold({ context.getString(R.string.fr_locale_saved) },
                                { context.getString(R.string.fr_locale_failed, it.message ?: it.javaClass.simpleName) })
                        }
                    }) { Text(stringResource(R.string.fr_use_locale)) }
                } else {
                    Text(stringResource(R.string.fr_no_locale),
                        style = MaterialTheme.typography.bodyMedium)
                }
                localeStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

                Section(stringResource(R.string.fr_profile))
                Text(stringResource(R.string.fr_profile_note), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onOpenProfiles) { Text(stringResource(R.string.fr_open_profiles)) }

                Section(stringResource(R.string.fr_driver))
                Text(stringResource(R.string.fr_driver_note), style = MaterialTheme.typography.bodyMedium)

                Section(stringResource(R.string.fr_interface))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UiMode.entries.forEach { option ->
                        FilterChip(selected = mode == option, onClick = {
                            mode = option
                            UiModeStore.write(context, option)
                        }, label = { Text(if (option == UiMode.PLAYER) stringResource(R.string.fr_player) else stringResource(R.string.fr_developer)) })
                    }
                }
                Text(if (mode == UiMode.DEVELOPER) stringResource(R.string.fr_developer_note)
                    else stringResource(R.string.fr_player_note),
                    style = MaterialTheme.typography.bodySmall)

                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClose) { Text(stringResource(R.string.fr_skip)) }
                    Button(onClick = {
                        if (mode == null) UiModeStore.write(context, UiMode.PLAYER)
                        onClose()
                    }) { Text(stringResource(R.string.common_done)) }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

/** U02: the check as shown; the model keeps the English text for tests and logs. */
@Composable
private fun checkTitle(check: FirstRun.Check): String = when (check.kind) {
    FirstRun.Kind.GPU_OK, FirstRun.Kind.GPU_NONE -> stringResource(R.string.fr_check_gpu)
    FirstRun.Kind.ARM64_OK, FirstRun.Kind.ARM64_NONE -> stringResource(R.string.fr_check_arm64)
    FirstRun.Kind.ANDROID_OK, FirstRun.Kind.ANDROID_OLD -> "Android"
    FirstRun.Kind.FOLDER_SET, FirstRun.Kind.FOLDER_UNSUPPORTED, FirstRun.Kind.FOLDER_UNSET -> stringResource(R.string.fr_check_folder)
    null -> check.title
}

@Composable
private fun checkDetail(check: FirstRun.Check): String = when (check.kind) {
    FirstRun.Kind.GPU_OK, FirstRun.Kind.ARM64_OK -> check.arg ?: check.detail
    FirstRun.Kind.GPU_NONE -> stringResource(R.string.fr_check_gpu_none)
    FirstRun.Kind.ARM64_NONE -> stringResource(R.string.fr_check_arm64_none)
    FirstRun.Kind.ANDROID_OK -> stringResource(R.string.fr_check_android, check.arg.orEmpty())
    FirstRun.Kind.ANDROID_OLD -> stringResource(R.string.fr_check_android_old, check.arg.orEmpty())
    FirstRun.Kind.FOLDER_SET -> stringResource(R.string.fr_check_folder_set)
    FirstRun.Kind.FOLDER_UNSUPPORTED -> stringResource(R.string.fr_check_folder_unsupported)
    FirstRun.Kind.FOLDER_UNSET -> stringResource(R.string.fr_check_folder_unset)
    null -> check.detail
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
            Text(checkTitle(check), style = MaterialTheme.typography.bodyLarge)
            Text(checkDetail(check), style = MaterialTheme.typography.bodySmall)
        }
    }
}
