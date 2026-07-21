package com.pawelwywiol.minervahelper.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.pawelwywiol.minervahelper.R

private val DRAWABLES = mapOf(
    "cartridge" to R.drawable.ic_media_cartridge,
    "disc" to R.drawable.ic_media_disc,
    "floppy" to R.drawable.ic_media_floppy,
    "tape" to R.drawable.ic_media_tape,
    "arcade" to R.drawable.ic_media_arcade,
    "handheld" to R.drawable.ic_media_handheld,
    "computer" to R.drawable.ic_media_computer,
    "document" to R.drawable.ic_media_document,
)

/**
 * Fixed at 24dp on purpose: it must not grow with the font-size setting, or a row at +100% would be
 * mostly icon.
 */
@Composable
internal fun MediaIcon(media: String, contentDescription: String? = null) = Icon(
    painter = painterResource(DRAWABLES[media] ?: R.drawable.ic_media_unknown),
    contentDescription = contentDescription,
    tint = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.size(24.dp),
)
