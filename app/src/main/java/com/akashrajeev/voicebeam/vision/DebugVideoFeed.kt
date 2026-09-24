package com.akashrajeev.voicebeam.vision

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Debug builds only: decodes a bundled two-person clip and pushes every frame
 * through the real face pipeline, so tap-to-lock, the lip gate and captions can
 * be tested without pointing the camera at real people. Frame positions follow
 * the wall clock, so lips stay in sync with the debug audio feed.
 */
class DebugVideoFeed(context: Context, sink: FaceSink) {

    private val app = context.applicationContext
    private val processor = FaceProcessor(context, sink)
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null

    val available: Boolean get() = processor.available

    fun start() {
        if (running.getAndSet(true)) return
        thread = Thread({
            val retriever = MediaMetadataRetriever()
            try {
                app.assets.openFd("feed/test_feed.mp4").use { retriever.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 60_000L
                val started = SystemClock.uptimeMillis()
                while (running.get()) {
                    val pos = (SystemClock.uptimeMillis() - started) % durationMs
                    val bmp = try { retriever.getFrameAtTime(pos * 1000, MediaMetadataRetriever.OPTION_CLOSEST) } catch (t: Throwable) { null }
                    if (bmp != null) processor.process(bmp)
                    val wait = 66 - (SystemClock.uptimeMillis() % 66)
                    if (wait in 1..66) Thread.sleep(wait)
                }
            } catch (t: Throwable) {
                Log.w("VoiceBeamVision", "debug video feed failed", t)
            } finally {
                try { retriever.release() } catch (_: Throwable) {}
            }
        }, "vb-debugvideo").also { it.start() }
    }

    fun stop() {
        running.set(false)
        thread?.join(1500); thread = null
        processor.close()
    }
}
