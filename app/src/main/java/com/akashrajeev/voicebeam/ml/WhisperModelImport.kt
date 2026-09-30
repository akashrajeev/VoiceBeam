package com.akashrajeev.voicebeam.ml

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest

/** Imports only the exact tested Turbo weight file, using Android's document picker. */
object WhisperModelImport {
    fun importModel(context: Context, uri: Uri, backend: AsrBackend) {
        val (name, size, sha) = when (backend) {
            AsrBackend.WHISPER_SMALL -> Triple("ggml-small-q5_1.bin", 190085487L, "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb")
            AsrBackend.WHISPER_TURBO -> Triple("ggml-large-v3-turbo-q5_0.bin", 574041195L, "394221709cd5ad1f40c46e6031ca61bce88931e6e088c188294c6d5a55ffa7e2")
            else -> error("Unsupported optional weight")
        }
        val dir = File(context.filesDir, "whisper").also { it.mkdirs() }
        val tmp = File(dir, "turbo-import.tmp")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Cannot open selected file" }
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        require(total <= size) { "Wrong model file size" }
                        digest.update(buffer, 0, n)
                        output.write(buffer, 0, n)
                    }
                }
            }
            require(total == size && digest.digest().joinToString("") { "%02x".format(it) } == sha) {
                "Select the original $name file"
            }
            check(tmp.renameTo(File(dir, name))) { "Cannot save model" }
        } finally { tmp.delete() }
    }
}
