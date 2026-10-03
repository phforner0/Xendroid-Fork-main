package xendroid.compose.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import xendroid.compose.R
import xendroid.compose.settings.AppLanguage
import xendroid.compose.settings.AppLanguageStore
import xendroid.compose.ui.theme.UiScale
import xendroid.compose.ui.theme.UiScaleStore
import xendroid.compose.ui.theme.UiScaling

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun percent(value: Float): String = NumberFormat.getPercentInstance().format(value.toDouble())

/**
 * 15l: how large the app draws its screens and its text. Each change is saved at once; the
 * frontend follows it live and a game started afterwards draws its menu with it.
 */
@Composable
internal fun UiScaleRows() {
    val context = LocalContext.current
    var scale by remember { mutableStateOf(UiScaleStore.read(context)) }
    fun change(next: UiScale) {
        if (next == scale) return
        scale = next
        UiScaleStore.write(context, next)
    }
    val configuration = LocalConfiguration.current
    val size = UiScaling.fittingSize(scale.size, minOf(configuration.screenWidthDp, configuration.screenHeightDp).toFloat())
    val text = UiScaling.fittingText(scale.text, configuration.fontScale)
    ListItem(
        headlineContent = { Text(stringResource(R.string.ui_size, percent(scale.size))) },
        supportingContent = {
            Text(stringResource(R.string.ui_size_note) +
                if (size < scale.size) "\n" + stringResource(R.string.ui_size_limited, percent(size)) else "")
        },
        trailingContent = {
            SmallerLarger(
                canSmaller = scale.size > UiScale.SIZES.first(), onSmaller = { change(scale.smaller()) },
                canLarger = scale.size < UiScale.SIZES.last(), onLarger = { change(scale.larger()) },
            )
        },
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.ui_text_size, percent(scale.text))) },
        supportingContent = {
            Text(stringResource(R.string.ui_text_size_note) +
                if (text < scale.text) "\n" + stringResource(R.string.ui_text_size_limited, percent(text)) else "")
        },
        trailingContent = {
            SmallerLarger(
                canSmaller = scale.text > UiScale.TEXTS.first(), onSmaller = { change(scale.smallerText()) },
                canLarger = scale.text < UiScale.TEXTS.last(), onLarger = { change(scale.largerText()) },
            )
        },
    )
}

@Composable
private fun SmallerLarger(canSmaller: Boolean, onSmaller: () -> Unit, canLarger: Boolean, onLarger: () -> Unit) {
    val smaller = stringResource(R.string.ui_smaller)
    val larger = stringResource(R.string.ui_larger)
    Row {
        TextButton(onClick = onSmaller, enabled = canSmaller, modifier = Modifier.semantics { contentDescription = smaller }) {
            Text("−")
        }
        TextButton(onClick = onLarger, enabled = canLarger, modifier = Modifier.semantics { contentDescription = larger }) {
            Text("+")
        }
    }
}

@Composable
private fun languageName(language: AppLanguage): String = language.autonym ?: stringResource(R.string.app_language_system)

/** 15l: the app's language, from the phone's or one the app has. */
@Composable
internal fun AppLanguageRow() {
    val context = LocalContext.current
    var current by remember { mutableStateOf(AppLanguageStore.current(context)) }
    var choosing by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(stringResource(R.string.app_language, languageName(current))) },
        supportingContent = { Text(stringResource(R.string.app_language_note)) },
        trailingContent = { TextButton(onClick = { choosing = true }) { Text(stringResource(R.string.app_language_change)) } },
    )
    if (!choosing) return
    AlertDialog(
        onDismissRequest = { choosing = false },
        title = { Text(stringResource(R.string.app_language_title)) },
        text = {
            Column(Modifier.selectableGroup()) {
                AppLanguage.entries.forEach { language ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected = language == current, role = Role.RadioButton, onClick = {
                                choosing = false
                                if (language != current) {
                                    current = language
                                    context.findActivity()?.let { AppLanguageStore.set(it, language) }
                                }
                            })
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = language == current, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(languageName(language))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.common_close)) } },
    )
}
