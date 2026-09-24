package com.akashrajeev.voicebeam.vision

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Debug builds only: feeds frames from the bundled two-person clip into the
 * real face pipeline, so tap-to-lock, the lip gate and captions can be tested
 * without pointing the camera at real people. Frames normally come from
 * PixelCopy on the SurfaceView playing the clip; if the emulator hands back
 * black frames, the feed falls back to MediaMetadataRetriever decode. Output
 * frames are always 1280x720 video space.
 */
class DebugVideoFeed(context: Context, private val view: SurfaceView, sink: FaceSink) {

    private val app = context.applicationContext
    private val processor = FaceProcessor(context, sink)
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null

    val available: Boolean get() = processor.available

    fun start() {
        if (running.getAndSet(true)) return
        val copyThread = HandlerThread("vb-debugcopy").also { it.start() }
        val handler = Handler(copyThread.looper)
        thread = Thread({
            var retriever: MediaMetadataRetriever? = null
            try {
                var blackStreak = 0
                var useRetriever = false
                var copied = 0
                val started = SystemClock.uptimeMillis()
                while (running.get()) {
                    var frame: Bitmap? = null
                    if (!useRetriever) {
                        val w = view.width; val h = view.height
                        if (w > 0 && h > 0) {
                            val shot = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            val done = CountDownLatch(1)
                            val result = java.util.concurrent.atomic.AtomicInteger(-1)
                            try {
                                PixelCopy.request(view, shot, { r -> result.set(r); done.countDown() }, handler)
                                if (done.await(700, TimeUnit.MILLISECONDS) && result.get() == PixelCopy.SUCCESS) {
                                    val scale = minOf(w / 1280f, h / 720f)
                                    val vw = (1280 * scale).toInt(); val vh = (720 * scale).toInt()
                                    val vx = (w - vw) / 2; val vy = (h - vh) / 2
                                    if (vw > 0 && vh > 0) {
                                        frame = Bitmap.createScaledBitmap(Bitmap.createBitmap(shot, vx, vy, vw, vh), 1280, 720, true)
                                    }
                                }
                            } catch (t: Throwable) {
                                Log.w("VoiceBeamVision", "debug frame copy failed", t)
                            }
                        }
                        if (frame != null) {
                            copied++
                            if (isBlack(frame)) {
                                blackStreak++
                                frame = null
                            } else {
                                blackStreak = 0
                            }
                            if (blackStreak >= 8) {
                                Log.w("VoiceBeamVision", "PixelCopy returning black frames, switching to MediaMetadataRetriever")
                                useRetriever = true
                            }
                        }
                    } else {
                        if (retriever == null) {
                            retriever = MediaMetadataRetriever()
                            app.assets.openFd("feed/test_feed.mp4").use { retriever.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                        }
                        val pos = (SystemClock.uptimeMillis() - started) % 60_000L
                        for (opt in intArrayOf(MediaMetadataRetriever.OPTION_CLOSEST, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, MediaMetadataRetriever.OPTION_PREVIOUS_SYNC)) {
                            frame = try { retriever.getFrameAtTime(pos * 1000, opt) } catch (t: Throwable) { null }
                            if (frame != null) break
                        }
                        if (frame != null && (frame.width != 1280 || frame.height != 720)) {
                            frame = Bitmap.createScaledBitmap(frame, 1280, 720, true)
                        }
                    }
                    if (frame != null) processor.process(frame)
                    Thread.sleep(150)
                }
            } catch (_: InterruptedException) {
            } finally {
                try { retriever?.release() } catch (_: Throwable) {}
                copyThread.quitSafely()
            }
        }, "vb-debugvideo").also { it.start() }
    }

    private fun isBlack(bmp: Bitmap): Boolean {
        var sum = 0L
        val stepX = bmp.width / 16; val stepY = bmp.height / 16
        for (y in 0 until 16) for (x in 0 until 16) {
            val p = bmp.getPixel(x * stepX, y * stepY)
            sum += ((p shr 16) and 0xff) + ((p shr 8) and 0xff) + (p and 0xff)
        }
        return sum / 256 < 8 // mean channel value below 8 = effectively black
    }

    fun stop() {
        running.set(false)
        thread?.join(1500); thread = null
        processor.close()
    }
}
