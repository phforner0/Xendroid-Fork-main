package xendroid.compose.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalContext
import xendroid.compose.settings.SettingValue
import xendroid.compose.settings.SettingsCategory
import xendroid.compose.settings.SettingsSchema
import xendroid.compose.settings.SettingsViewModel
import xendroid.compose.settings.UiMode
import xendroid.compose.settings.UiModeStore

/**
 * Two-level settings: an INDEX of sections (the 124-entry schema is too long for one list), and
 * a per-section DETAIL with that section's rows. Navigation between the two is internal state so
 * the single SettingsViewModel (and its config handle / flush lifecycle) is shared; the system
 * back button goes detail->index->exit via [BackHandler].
 */
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val values by vm.values.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    // Durable flush on pause; re-open on resume. Dispose flush = backstop.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_PAUSE -> vm.flush()
                Lifecycle.Event.ON_RESUME -> vm.onResume()
                else -> {}
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); vm.flush() }
    }

    var selected by remember { mutableStateOf<SettingsCategory?>(null) }
    val context = LocalContext.current
    var mode by remember { mutableStateOf(UiModeStore.read(context)) }
    val categories = remember(mode) { SettingsSchema.categoriesFor(mode) }
    if (!ready) {
        ConfigLoadNotice(error, vm::onResume, onBack)
        return
    }
    if (error != null) {
        AlertDialog(
            onDismissRequest = vm::clearError,
            text = { Text(error.orEmpty()) },
            confirmButton = { TextButton(onClick = { vm.clearError(); vm.flush() }) { Text("Retry save") } },
            dismissButton = { TextButton(onClick = vm::clearError) { Text("Close") } },
        )
    }
    val section = selected
    if (section == null) {
        SettingsIndex(
            categories = categories,
            modifiedCountOf = { cat -> cat.settings.count { values[it.key]?.modified == true } },
            onOpen = { selected = it },
            onBack = { vm.flush(); onBack() },
            dataBundle = {
                UiModeRow(mode) { next ->
                    UiModeStore.write(context, next)
                    mode = next
                }
                HorizontalDivider()
                MenuButtonsRow()
                HorizontalDivider()
                DataBundleSection(beforeImport = vm::flush, afterImport = vm::onResume)
                UpdateChannelSection()
            },
        )
    } else {
        BackHandler { selected = null }
        SettingsCategoryDetail(
            category = section,
            categories = categories,
            values = values,
            vm = vm,
            onBack = { selected = null },
        )
    }
}

/** U04: which controller button confirms in the app's menus (never in the game). */
@Composable
internal fun MenuButtonsRow() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var swap by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf(xendroid.compose.gamepad.MenuButtonPrefs.swapConfirm(context))
    }
    ListItem(
        headlineContent = { Text(if (swap) "Menus: B confirms, A goes back" else "Menus: A confirms, B goes back") },
        supportingContent = {
            Text("For the library, the in-game menu and the layout editor; the game's own buttons never change. " +
                "The in-game menu follows it from the next game you open.")
        },
        trailingContent = {
            TextButton(onClick = {
                swap = !swap
                xendroid.compose.gamepad.MenuButtonPrefs.setSwapConfirm(context, swap)
            }) { Text("Swap") }
        },
    )
}

/** L02: Player shows the settings players change; Developer shows every engine setting. */
@Composable
internal fun UiModeRow(mode: UiMode, onChange: (UiMode) -> Unit) {
    ListItem(
        headlineContent = { Text("Interface: ${mode.label}") },
        supportingContent = {
            Text(if (mode == UiMode.PLAYER)
                "Essential settings only. Developer shows every engine setting and the experimental options " +
                    "(still limited to developer builds where they are). Hidden settings keep their values."
            else "Every engine setting. Player keeps only what players change.")
        },
        trailingContent = {
            TextButton(onClick = { onChange(if (mode == UiMode.PLAYER) UiMode.DEVELOPER else UiMode.PLAYER) }) {
                Text(if (mode == UiMode.PLAYER) "Switch to Developer" else "Switch to Player")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsIndex(
    categories: List<SettingsCategory>,
    modifiedCountOf: (SettingsCategory) -> Int,
    onOpen: (SettingsCategory) -> Unit,
    onBack: () -> Unit,
    dataBundle: @Composable () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item(key = "data-bundle") {
                dataBundle()
                HorizontalDivider()
            }
            items(categories, key = { it.title }) { cat ->
                val modified = modifiedCountOf(cat)
                ListItem(
                    headlineContent = { Text(cat.title) },
                    supportingContent = {
                        Text(buildString {
                            append("${cat.settings.size} settings")
                            if (modified > 0) append("  ·  $modified changed")
                        })
                    },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Open")
                    },
                    modifier = Modifier.clickable { onOpen(cat) },
                )
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsCategoryDetail(
    category: SettingsCategory,
    categories: List<SettingsCategory>,
    values: Map<String, SettingValue>,
    vm: SettingsViewModel,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(category.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to sections")
                    }
                },
            )
        }
    ) { padding ->
        var query by remember { mutableStateOf("") }
        Column(Modifier.fillMaxSize().padding(padding)) {
        OutlinedTextField(query, onValueChange = { query = it }, label = { Text("Search settings") },
            singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        LazyColumn(Modifier.weight(1f)) {
            if (query.isNotBlank()) {
                items(categories.flatMap { it.settings }.filter {
                    it.title.contains(query, true) || it.name.contains(query, true) || it.desc.contains(query, true)
                }, key = { it.key }) { setting ->
                    val sv = values[setting.key]
                    SettingRow(vm, setting, sv?.modified == true, sv?.raw)
                }
            } else {
            items(category.settings, key = { it.key }) { setting ->
                val sv = values[setting.key]
                SettingRow(vm, setting, modified = sv?.modified == true, raw = sv?.raw)
                HorizontalDivider()
            }
            }
        }
        }
    }
}
