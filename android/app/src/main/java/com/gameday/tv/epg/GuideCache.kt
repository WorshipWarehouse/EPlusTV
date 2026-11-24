package com.gameday.tv.epg

import com.gameday.tv.core.Diag
import com.gameday.tv.core.JsonCodec
import com.gameday.tv.model.GuideSnapshot
import java.io.File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * Atomic file cache. A partially written or corrupt cache is treated as "no cache" instead of
 * crashing, which is what makes a power cut during refresh recoverable.
 */
class GuideCache(private val file: File) {

    fun read(): GuideSnapshot? {
        if (!file.exists()) return null
        return try {
            val text = file.readText()
            if (text.isBlank()) return null
            val snapshot = JsonCodec.json.decodeFromString<GuideSnapshot>(text)
            if (snapshot.channels.isEmpty() && snapshot.programs.isEmpty()) null else snapshot
        } catch (e: Exception) {
            Diag.warn("GuideCache", "Discarding unreadable guide cache: ${e.javaClass.simpleName}")
            null
        }
    }

    fun write(snapshot: GuideSnapshot): Boolean {
        return try {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(JsonCodec.json.encodeToString(snapshot))
            if (!temp.renameTo(file)) {
                file.delete()
                if (!temp.renameTo(file)) {
                    Diag.error("GuideCache", "Could not replace guide cache")
                    return false
                }
            }
            true
        } catch (e: Exception) {
            Diag.error("GuideCache", "Could not write guide cache: ${e.javaClass.simpleName}")
            false
        }
    }

    fun clear() {
        file.delete()
        File(file.parentFile, file.name + ".tmp").delete()
    }

    fun sizeBytes(): Long = if (file.exists()) file.length() else 0L
}
