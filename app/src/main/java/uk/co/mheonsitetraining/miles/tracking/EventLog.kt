package uk.co.mheonsitetraining.miles.tracking

import android.content.Context
import android.util.Log
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A short on-phone diary of what the tracker saw and did (Bluetooth events, charging,
 * start/stop, errors), so a trip that didn't record can be diagnosed. Shown in Setup.
 */
object EventLog {
    private const val MAX_LINES = 300
    private val format = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss", Locale.UK)
    private val lock = Any()
    private var file: File? = null
    private val state = MutableStateFlow<List<String>>(emptyList())

    /** Newest first. */
    val lines: StateFlow<List<String>> = state.asStateFlow()

    fun init(context: Context) = synchronized(lock) {
        if (file != null) return
        val f = File(context.applicationContext.filesDir, "events.log")
        file = f
        state.value = try {
            if (f.exists()) f.readLines().takeLast(MAX_LINES).asReversed() else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun log(context: Context, message: String) {
        Log.i("MheMiles", message)
        init(context)
        synchronized(lock) {
            val line = "${format.format(LocalDateTime.now())}  $message"
            val updated = (listOf(line) + state.value).take(MAX_LINES)
            state.value = updated
            try {
                file?.writeText(updated.asReversed().joinToString("\n", postfix = "\n"))
            } catch (e: Exception) {
                Log.w("MheMiles", "Couldn't write event log", e)
            }
        }
    }

    fun text(): String = state.value.asReversed().joinToString("\n")

    fun clear(context: Context) {
        init(context)
        synchronized(lock) {
            state.value = emptyList()
            file?.delete()
        }
    }
}
