package io.github.pawelwywiol.minervahelper.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.pawelwywiol.minervahelper.R
import io.github.pawelwywiol.minervahelper.data.catalog.Systems

private val SHAPES = mapOf(
    "gamepad" to R.drawable.ic_sys_gamepad,
    "gamepad_modern" to R.drawable.ic_sys_gamepad_modern,
    "joystick" to R.drawable.ic_sys_joystick,
    "handheld" to R.drawable.ic_sys_handheld,
    "arcade" to R.drawable.ic_sys_arcade,
    "computer" to R.drawable.ic_sys_computer,
    "image" to R.drawable.ic_sys_image,
    "video" to R.drawable.ic_sys_video,
    "audio" to R.drawable.ic_sys_audio,
    "text" to R.drawable.ic_sys_text,
    "archive" to R.drawable.ic_sys_archive,
)

/**
 * Each vendor's own colour, lightened where the brand value would sink into the dark background —
 * Nintendo's #E60012 and Xbox's #107C10 are both too dark to read at 24dp on #14161A.
 *
 * Two are deliberately not the brand value. Sega's #117BBD is close enough to PlayStation blue to
 * be confusable in a mixed list, so it moves to cyan, which its Dreamcast era owns anyway; Atari's
 * #D21404 is another red next to Nintendo's, so it moves to the orange of the woodgrain 2600.
 * Colour is the second cue here, not the first — the row always spells the system out — so
 * telling the families apart beats matching a swatch nobody sees side by side.
 */
private val FAMILY_COLOURS = mapOf(
    "nintendo" to Color(0xFFF04438),
    "sony" to Color(0xFF4C8DFF),
    "microsoft" to Color(0xFF5CC26A),
    "sega" to Color(0xFF00BCD4),
    "atari" to Color(0xFFFF7A2F),
    "snk" to Color(0xFFFFD24A),
    "nec" to Color(0xFF26C6A6),
    "commodore" to Color(0xFFB48BE8),
    "sinclair" to Color(0xFFFF5FA2),
    "other" to Color(0xFF8C97AB),
    Systems.FILE_FAMILY to Color(0xFF6E7787),
)

/**
 * Fixed at 24dp on purpose: it must not grow with the font-size setting, or a row at +100% would
 * be mostly icon.
 */
@Composable
internal fun SystemIcon(icon: Systems.Icon, contentDescription: String? = null) = Icon(
    painter = painterResource(SHAPES[icon.shape] ?: R.drawable.ic_sys_file),
    contentDescription = contentDescription,
    tint = FAMILY_COLOURS[icon.family] ?: FAMILY_COLOURS.getValue("other"),
    modifier = Modifier.size(24.dp),
)
