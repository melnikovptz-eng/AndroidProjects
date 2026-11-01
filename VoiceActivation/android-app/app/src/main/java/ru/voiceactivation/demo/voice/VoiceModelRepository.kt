package ru.voiceactivation.demo.voice

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

/** Imports user-selected model files through SAF into app-private storage. */
class VoiceModelRepository(context: Context) {
    private val app = context.applicationContext
    private val root = File(app.filesDir, "voice_models")

    fun importRussianWakeModel(uris: List<Uri>): File {
        val target = File(root, "russian-t-one").apply { mkdirs() }
        uris.forEach { uri ->
            val name = displayName(uri)
            require(name == "model.onnx" || name == "tokens.txt") {
                "Unexpected Russian model file: $name"
            }
            copy(uri, File(target, name))
        }
        val model = File(target, "model.onnx")
        val tokens = File(target, "tokens.txt")
        require(model.isFile && model.length() > MIN_RUSSIAN_MODEL_BYTES) {
            "Russian model.onnx is missing or incomplete"
        }
        require(tokens.isFile && tokens.length() > 100) { "Russian tokens.txt is missing" }
        return target
    }

    fun importSpeakerModel(uri: Uri): File {
        val name = displayName(uri)
        require(name.endsWith(".onnx", ignoreCase = true)) { "Select an ONNX speaker model" }
        val target = File(root, "speaker").apply { mkdirs() }
        val output = File(target, "speaker-embedding.onnx")
        copy(uri, output)
        require(output.length() > MIN_SPEAKER_MODEL_BYTES) { "Speaker model file is incomplete" }
        return output
    }

    fun russianModelDirectory(): File = File(root, "russian-t-one")
    fun speakerModelFile(): File = File(root, "speaker/speaker-embedding.onnx")

    private fun displayName(uri: Uri): String {
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) return cursor.getString(index)
                }
            }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "model.bin"
    }

    private fun copy(uri: Uri, destination: File) {
        destination.parentFile?.mkdirs()
        val temp = File(destination.parentFile, destination.name + ".part")
        try {
            app.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Unable to open selected model file" }
                FileOutputStream(temp).use { output -> input.copyTo(output) }
            }
            check(temp.length() > 0) { "Selected model file is empty" }
            if (destination.exists()) check(destination.delete()) { "Unable to replace previous model file" }
            check(temp.renameTo(destination)) { "Unable to finalize imported model file" }
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    companion object {
        private const val MIN_RUSSIAN_MODEL_BYTES = 50L * 1024 * 1024
        private const val MIN_SPEAKER_MODEL_BYTES = 1L * 1024 * 1024
    }
}
