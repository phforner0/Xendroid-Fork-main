package xendroid.compose.shots

import xendroid.compose.core.HudDetail
import xendroid.compose.core.HudLayout
import xendroid.compose.core.HudLook
import xendroid.compose.core.HudMetric
import xendroid.compose.core.HudStyle
import xendroid.compose.ui.ingame.HudPreview
import xendroid.compose.ui.ingame.InGameAction
import xendroid.compose.ui.ingame.InGameMenuModel
import xendroid.compose.ui.ingame.InGamePage
import xendroid.compose.ui.ingame.MenuStat
import xendroid.compose.ui.ingame.MenuValue

/** The in-game menu of the prints: Halo 3 at 30 FPS, a few values its own, three changes kept this session. */
object SampleMenu {
    fun model(art: Any?, savedChanges: Int = 3, developer: Boolean = false): InGameMenuModel = InGameMenuModel(
        values = mapOf(
            InGameAction.DISPLAY_MODE to MenuValue(options = listOf("Ajustar", "Preencher", "Esticar", "Inteira"), selected = 0),
            InGameAction.SCALING_EFFECT to MenuValue(text = "FSR", own = true),
            InGameAction.ANTIALIASING to MenuValue(text = "FXAA", own = true),
            InGameAction.SHARPNESS to MenuValue(text = "alta", own = true),
            InGameAction.DITHER to MenuValue(text = "como nos ajustes"),
            InGameAction.COLOR_FILTER to MenuValue(text = "Desligado"),
            InGameAction.STRETCH to MenuValue(on = false, text = "A partir da próxima abertura"),
            InGameAction.EXTERNAL_DISPLAY to MenuValue(text = "Este telefone"),
            InGameAction.TV_MARGIN to MenuValue(text = "0%"),
            InGameAction.DRIVER_INFO to MenuValue(text = "Driver: Turnip Mesa 25.3.0 r2"),
            InGameAction.WINFG to MenuValue(on = false),
            InGameAction.WINFG_PRESET to MenuValue(text = "Equilíbrio"),
            InGameAction.LSFG to MenuValue(on = false, enabled = false),
            InGameAction.FPS_LIMIT to MenuValue(options = listOf("Sem limite", "30", "45", "60", "90", "120"), selected = 1, own = true,
                note = "Próxima abertura: global 60 FPS · jogo 30 FPS"),
            InGameAction.REFRESH_RATE to MenuValue(text = "Automático", note = "Hz da tela · pedido Automático · efetivo 120 Hz"),
            InGameAction.SUSTAINED_PERFORMANCE to MenuValue(on = false),
            InGameAction.PERFORMANCE_HINTS to MenuValue(on = false, text = "ADPF do apresentador · Desligado"),
            InGameAction.BACKGROUND_POLICY to MenuValue(text = "AUTO"),
            InGameAction.PERFORMANCE_HUD to MenuValue(on = true),
            InGameAction.HUD_LAYOUT to MenuValue(options = listOf("Vertical", "Horizontal"), selected = 1),
            InGameAction.HUD_STYLE to MenuValue(options = listOf("Só FPS", "Métricas", "Painel"), selected = 1),
            InGameAction.HUD_METRICS to MenuValue(options = listOf("CPU", "GPU", "RAM", "Mem. GPU", "Bateria", "SoC", "Potência", "Gráfico de FPS") +
                if (developer) listOf("Vulkan") else emptyList(), checked = setOf(0, 1, 2, 4, 7)),
            InGameAction.HUD_POSITION to MenuValue(options = listOf("Topo", "Base"), selected = 0),
            InGameAction.HUD_LOOK to MenuValue(options = listOf("Caixa", "Contorno", "Texto"), selected = 0),
            InGameAction.HUD_SIZE to MenuValue(fraction = 0.25f, steps = 0, text = "100%"),
            InGameAction.HUD_OPACITY to MenuValue(fraction = 0.6f, steps = 0, text = "60%"),
            InGameAction.HUD_COLORS to MenuValue(fraction = 1f, steps = 0, text = "100%"),
            InGameAction.TOUCH_CONTROLS to MenuValue(on = true),
            InGameAction.CONTROL_STYLE to MenuValue(options = listOf("Moderno", "Clássico"), selected = 0),
            InGameAction.ADAPTIVE_STICKS to MenuValue(on = false, own = true),
            InGameAction.TOUCH_CAMERA to MenuValue(on = false, text = "O lado direito livre gira a câmera"),
            InGameAction.EDIT_TOUCH_LAYOUT to MenuValue(text = "Posição, tamanho, opacidade, ocultar"),
            InGameAction.SPLIT_SCREEN to MenuValue(text = "Desligada"),
            InGameAction.CONTROLLER_RUMBLE to MenuValue(text = "Média", note = "P1"),
            InGameAction.PHONE_CONTROLLERS to MenuValue(text = "Desligado"),
            InGameAction.UNBUFFERED_INPUT to MenuValue(on = true),
            InGameAction.GYRO_CAMERA to MenuValue(on = false),
            InGameAction.GYRO_AIM to MenuValue(text = "sempre"),
            InGameAction.GYRO_SENSITIVITY to MenuValue(text = "Normal"),
            InGameAction.GYRO_CALIBRATE to MenuValue(text = "Deixe o telefone parado ao fechar o menu"),
            InGameAction.VOLUME to MenuValue(fraction = 0.8f, steps = 0, text = "80%"),
            InGameAction.MUTE to MenuValue(on = false),
            InGameAction.AUTO_SAVE to MenuValue(on = true, text = "O que mudar aqui fica para Halo 3"),
            InGameAction.UNDO_SESSION to MenuValue(text = "$savedChanges mudanças nesta sessão", enabled = savedChanges > 0),
            InGameAction.MAKE_GLOBAL to MenuValue(text = "Viram os ajustes globais; este jogo deixa de ter cópia própria", enabled = savedChanges > 0),
            InGameAction.PAUSE_ON_OPEN to MenuValue(on = true, text = "Vale para todos os jogos"),
            InGameAction.MARK_SCENE to MenuValue(text = "0 até agora, para comparar execuções"),
        ),
        gameName = "Halo 3", art = art, paused = true,
        status = listOf(MenuStat("30", "FPS"), MenuStat("34", "ms", "p99"), MenuStat("41", "°C"), MenuStat("72%", label = "bateria"),
            MenuStat("Turnip Mesa 25.3.0")),
        notes = mapOf(
            InGamePage.GRAPHICS to listOf("As opções de Imagem valem na hora, e o jogo as guarda."),
            InGamePage.SESSION to listOf("v412 · 7bb3409\nAdreno (TM) 825"),
        ),
        savedChanges = savedChanges,
        hudPreview = HudPreview(HudDetail.FULL, setOf(HudMetric.CPU, HudMetric.GPU, HudMetric.RAM, HudMetric.BATTERY_TEMPERATURE),
            HudLook.BOX, HudStyle(layout = HudLayout.HORIZONTAL, opacity = 0.6f), 1f),
    )
}
