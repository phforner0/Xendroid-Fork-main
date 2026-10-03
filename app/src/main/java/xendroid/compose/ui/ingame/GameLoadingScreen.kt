package xendroid.compose.ui.ingame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File
import xendroid.compose.R

/**
 * 15e: what the screen shows until the game's first frame (Bannerlator `e2acda52`): the game's
 * cover blurred and dimmed behind everything, the cover and the name in the middle, the stage
 * of the start ([BootStatus], the core's own counters) with a bar that moves a step per stage,
 * and Cancel. Past [BOOT_STILL_WORKING_SECONDS] it says the start is still going and why the
 * first one is slow. Without a cover (not known yet) it is the same on black.
 */
@Composable
fun GameLoadingScreen(
    status: BootStatus,
    art: File?,
    name: String?,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().background(Color.Black)) {
        if (art != null) {
            AsyncImage(
                model = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.4f,
                modifier = Modifier.fillMaxSize().blur(24.dp),
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66000000), Color(0xE6000000)))))
        Column(
            Modifier.align(Alignment.Center).widthIn(max = 520.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (art != null) {
                AsyncImage(
                    model = art,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(128.dp).clip(RoundedCornerShape(12.dp)),
                )
            }
            if (!name.isNullOrBlank()) {
                Text(name, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(bootStatusText(status), color = Color.White, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            LinearProgressIndicator(
                progress = { bootProgress(status.stage) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            )
            if (status.seconds >= BOOT_STILL_WORKING_SECONDS) {
                Text(stringResource(R.string.boot_still_working), color = Color(0xCCFFFFFF),
                    style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
            if (onCancel != null) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel), color = Color.White) }
            }
        }
    }
}
