package dev.basri.android.nobs_launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import dev.basri.android.nobs_launcher.model.WebShortcut
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class FaviconRepository(
    context: Context,
    private val fetcher: FaviconBytesFetcher = FaviconHttpFetcher(),
    private val executor: Executor = Executors.newSingleThreadExecutor(),
) : FaviconGateway, AutoCloseable {
    private val directory = File(context.applicationContext.filesDir, DIRECTORY_NAME)

    /**
     * Fetches and stores on [executor], then invokes [onComplete] exactly once on that same
     * executor thread. Candidate-fetch or image-processing exceptions complete with `null`.
     */
    override fun fetchAndStore(
        shortcut: WebShortcut,
        candidates: List<String>,
        onComplete: (String?) -> Unit,
    ) {
        executor.execute {
            val fileName = try {
                candidates.firstNotNullOfOrNull { candidate ->
                    fetcher.fetch(candidate)
                        ?.let { bytes -> store(shortcut.uuid, shortcut.url, bytes) }
                }
            } catch (_: Exception) {
                null
            }
            onComplete(fileName)
        }
    }

    fun store(uuid: String, url: String, bytes: ByteArray): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (
            bounds.outWidth <= 0 ||
            bounds.outHeight <= 0 ||
            bounds.outWidth > MAX_SOURCE_DIMENSION ||
            bounds.outHeight > MAX_SOURCE_DIMENSION ||
            bounds.outWidth.toLong() * bounds.outHeight.toLong() > MAX_SOURCE_PIXELS
        ) {
            return null
        }
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = decodeSampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        val decoded = runCatching {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
        }.getOrNull() ?: return null
        val output = runCatching { scaleDown(decoded) }.getOrNull() ?: run {
            decoded.recycle()
            return null
        }
        val fileName = "$uuid-${shortHash(url)}.png"
        val target = safeFile(fileName) ?: run {
            if (output !== decoded) output.recycle()
            decoded.recycle()
            return null
        }
        val temporary = File(directory, "$fileName.tmp")
        val stored = runCatching {
            if (!directory.exists() && !directory.mkdirs()) return@runCatching false
            FileOutputStream(temporary).use { stream ->
                if (!output.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    return@runCatching false
                }
                stream.flush()
                stream.fd.sync()
            }
            if (target.exists() && !target.delete()) return@runCatching false
            temporary.renameTo(target)
        }.getOrDefault(false)
        temporary.delete()
        if (output !== decoded) output.recycle()
        decoded.recycle()
        return fileName.takeIf { stored }
    }

    fun load(fileName: String?): Drawable? {
        val file = fileName?.let(::safeFile)?.takeIf(File::isFile) ?: return null
        return Drawable.createFromPath(file.absolutePath)
    }

    override fun delete(fileName: String) {
        safeFile(fileName)?.delete()
        safeFile("$fileName.tmp")?.delete()
    }

    override fun close() {
        (executor as? ExecutorService)?.shutdownNow()
    }

    private fun scaleDown(source: Bitmap): Bitmap {
        val largest = maxOf(source.width, source.height)
        if (largest <= MAX_OUTPUT_DIMENSION) return source
        val scale = MAX_OUTPUT_DIMENSION.toDouble() / largest.toDouble()
        val width = maxOf(1, (source.width * scale).roundToInt())
        val height = maxOf(1, (source.height * scale).roundToInt())
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    private fun safeFile(fileName: String): File? = fileName
        .takeIf(SAFE_FILE_NAME::matches)
        ?.let { File(directory, it) }

    private fun shortHash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .take(HASH_BYTES)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    companion object {
        const val DIRECTORY_NAME = "favicons"
        private const val MAX_OUTPUT_DIMENSION = 256
        private const val MAX_SOURCE_DIMENSION = 8_192
        private const val MAX_SOURCE_PIXELS = 16_777_216L
        private const val HASH_BYTES = 8
        private val SAFE_FILE_NAME = Regex("[A-Za-z0-9._-]+")

        internal fun decodeSampleSizeFor(width: Int, height: Int): Int {
            val largest = maxOf(width, height)
            var sampleSize = 1
            while (largest / (sampleSize * 2) >= MAX_OUTPUT_DIMENSION) {
                sampleSize *= 2
            }
            return sampleSize
        }
    }
}
