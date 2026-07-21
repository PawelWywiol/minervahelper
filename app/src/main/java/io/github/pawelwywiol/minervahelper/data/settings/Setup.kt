package io.github.pawelwywiol.minervahelper.data.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/** Records that the user has read what the app does and does not take responsibility for (R30). */
class Setup(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("setup", Context.MODE_PRIVATE)

    var accepted by mutableStateOf(prefs.getBoolean(ACCEPTED_KEY, false))
        private set

    fun accept() {
        prefs.edit { putBoolean(ACCEPTED_KEY, true) }
        accepted = true
    }

    companion object {
        private const val ACCEPTED_KEY = "disclaimer_accepted"

        @Volatile
        private var instance: Setup? = null

        fun get(context: Context): Setup =
            instance ?: synchronized(this) { instance ?: Setup(context).also { instance = it } }
    }
}
