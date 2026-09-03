package dev.basri.android.nobs_launcher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.basri.android.nobs_launcher.data.FaviconBytesFetcher
import dev.basri.android.nobs_launcher.data.FaviconRepository
import dev.basri.android.nobs_launcher.model.WebShortcut
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FaviconRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val repository = FaviconRepository(context)

    @Test
    fun closeShutsDownTheOwnedExecutor() {
        val executor = Executors.newSingleThreadExecutor()
        val candidateRepository = FaviconRepository(
            context = context,
            executor = executor,
        )

        candidateRepository.close()
        candidateRepository.close()

        assertTrue(executor.isShutdown)
    }

    @After
    fun cleanFiles() {
        File(context.filesDir, FaviconRepository.DIRECTORY_NAME)
            .listFiles()
            .orEmpty()
            .forEach(File::delete)
    }

    @Test
    fun validImageIsScaledAndStoredAsPrivatePng() {
        val source = Bitmap.createBitmap(512, 128, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().use { output ->
            source.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        source.recycle()

        val fileName = repository.store(UUID, "https://example.com/path", bytes)

        assertNotNull(fileName)
        val file = File(File(context.filesDir, FaviconRepository.DIRECTORY_NAME), fileName!!)
        assertTrue(file.isFile)
        val stored = checkNotNull(BitmapFactory.decodeFile(file.absolutePath))
        assertEquals(256, stored.width)
        assertEquals(64, stored.height)
        stored.recycle()
        assertFalse(File(file.parentFile, "$fileName.tmp").exists())
    }

    @Test
    fun invalidImageLeavesNoPrivateFile() {
        assertEquals(null, repository.store(UUID, "https://example.com", byteArrayOf(1, 2, 3)))
        assertTrue(
            File(context.filesDir, FaviconRepository.DIRECTORY_NAME)
                .listFiles()
                .orEmpty()
                .isEmpty(),
        )
    }

    @Test
    fun largeCompressibleImageIsSampledAndDeleteRemovesItsPrivateFiles() {
        assertEquals(32, FaviconRepository.decodeSampleSizeFor(8_192, 8_192))
        val source = Bitmap.createBitmap(1_024, 1_024, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().use { output ->
            source.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        source.recycle()
        assertTrue(bytes.size < 256 * 1_024)

        val fileName = checkNotNull(repository.store(UUID, "https://example.com/large", bytes))
        val directory = File(context.filesDir, FaviconRepository.DIRECTORY_NAME)
        val storedFile = File(directory, fileName)
        val temporaryFile = File(directory, "$fileName.tmp")
        val stored = checkNotNull(BitmapFactory.decodeFile(storedFile.absolutePath))
        assertEquals(256, maxOf(stored.width, stored.height))
        stored.recycle()

        repository.delete(fileName)

        assertFalse(storedFile.exists())
        assertFalse(temporaryFile.exists())
    }

    @Test
    fun fetchesCandidatesInOrderUntilOneDecodesThenStoresAndDeletesIt() {
        val validSource = Bitmap.createBitmap(512, 128, Bitmap.Config.ARGB_8888)
        val validBytes = ByteArrayOutputStream().use { output ->
            validSource.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        validSource.recycle()
        val requested = mutableListOf<String>()
        val fetcher = FaviconBytesFetcher { candidate ->
            requested += candidate
            when (candidate) {
                INVALID_CANDIDATE -> byteArrayOf(1, 2, 3)
                VALID_CANDIDATE -> validBytes
                else -> error("repository fetched past the first decodable candidate")
            }
        }
        val candidateRepository = FaviconRepository(
            context = context,
            fetcher = fetcher,
            executor = Executor(Runnable::run),
        )
        val shortcut = WebShortcut(UUID, "Example", "https://example.com/page")
        var storedFileName: String? = null

        candidateRepository.fetchAndStore(
            shortcut,
            listOf(INVALID_CANDIDATE, VALID_CANDIDATE, NEVER_REQUESTED_CANDIDATE),
        ) { storedFileName = it }

        assertEquals(listOf(INVALID_CANDIDATE, VALID_CANDIDATE), requested)
        val fileName = checkNotNull(storedFileName)
        val directory = File(context.filesDir, FaviconRepository.DIRECTORY_NAME)
        val storedFile = File(directory, fileName)
        val stored = checkNotNull(BitmapFactory.decodeFile(storedFile.absolutePath))
        assertEquals(256, stored.width)
        assertEquals(64, stored.height)
        stored.recycle()

        candidateRepository.delete(fileName)

        assertFalse(storedFile.exists())
        assertFalse(File(directory, "$fileName.tmp").exists())
    }

    @Test
    fun exceptionalFetcherCompletesExactlyOnceWithNull() {
        val candidateRepository = FaviconRepository(
            context = context,
            fetcher = FaviconBytesFetcher { throw IllegalStateException("broken decoder input") },
            executor = Executor(Runnable::run),
        )
        val shortcut = WebShortcut(UUID, "Example", "https://example.com/page")
        var completionCount = 0
        var completion: String? = "not-completed"

        candidateRepository.fetchAndStore(shortcut, listOf(VALID_CANDIDATE)) { fileName ->
            completionCount += 1
            completion = fileName
        }

        assertEquals(1, completionCount)
        assertNull(completion)
    }

    @Test
    fun oneDeadlineIsSharedAcrossAllCandidates() {
        val requested = mutableListOf<String>()
        val timeouts = mutableListOf<Long>()
        var nowMillis = 0L
        val fetcher = object : FaviconBytesFetcher {
            override fun fetch(iconUrl: String): ByteArray? = error("timed overload expected")

            override fun fetch(iconUrl: String, timeoutMillis: Long): ByteArray? {
                requested += iconUrl
                timeouts += timeoutMillis
                nowMillis += 3_000L
                return byteArrayOf(1, 2, 3)
            }
        }
        val candidateRepository = FaviconRepository(
            context = context,
            fetcher = fetcher,
            executor = Executor(Runnable::run),
            monotonicClockMillis = { nowMillis },
            overallFetchTimeoutMillis = 5_000L,
        )

        candidateRepository.fetchAndStore(
            WebShortcut(UUID, "Example", "https://example.com/page"),
            listOf("https://example.com/one", "https://example.com/two", "https://example.com/three"),
        ) {}

        assertEquals(
            listOf("https://example.com/one", "https://example.com/two"),
            requested,
        )
        assertEquals(listOf(5_000L, 2_000L), timeouts)
    }

    @Test
    fun asynchronousLoadsDecodeOnceThenUseTheMemoryCache() {
        val source = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().use { output ->
            source.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        source.recycle()
        var decodeCount = 0
        val candidateRepository = FaviconRepository(
            context = context,
            executor = Executor(Runnable::run),
            decodeExecutor = Executor(Runnable::run),
            bitmapDecoder = { path ->
                decodeCount += 1
                BitmapFactory.decodeFile(path)
            },
        )
        val fileName = checkNotNull(candidateRepository.store(UUID, "https://example.com", bytes))
        val loaded = mutableListOf<Drawable?>()

        candidateRepository.loadAsync(fileName) { loaded += it }
        candidateRepository.loadAsync(fileName) { loaded += it }

        assertEquals(2, loaded.size)
        assertTrue(loaded.all { it != null })
        assertEquals(1, decodeCount)
    }

    private companion object {
        const val UUID = "11111111-1111-4111-8111-111111111111"
        const val INVALID_CANDIDATE = "https://example.com/invalid.svg"
        const val VALID_CANDIDATE = "https://example.com/icon.png"
        const val NEVER_REQUESTED_CANDIDATE = "https://example.com/favicon.ico"
    }
}
