package com.akashrajeev.voicebeam.vision

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Debug builds only: copies frames from the SurfaceView playing the bundled
 * two-person clip and pushes them through the real face pipeline, so
 * tap-to-lock, the lip gate and captions can be tested without pointing the
 * camera at real people. Reading the displayed surface keeps lips in sync
 * with the wall-clock-paced debug audio feed, and works on emulators where
 * MediaMetadataRetriever cannot decode the clip.
 */
class DebugVideoFeed(context: Context, private val view: SurfaceView, sink: FaceSink) {

    private val processor = FaceProcessor(context, sink)
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null

    val available: Boolean get() = processor.available

    fun start() {
        if (running.getAndSet(true)) return
        val copyThread = HandlerThread("vb-debugcopy").also { it.start() }
        val handler = Handler(copyThread.looper)
        thread = Thread({
            try {
                while (running.get()) {
                    val w = view.width; val h = view.height
                    if (w > 0 && h > 0) {
                        val shot = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        val done = CountDownLatch(1)
                        try {
                            PixelCopy.request(view, shot, { done.countDown() }, handler)
                            if (done.await(700, TimeUnit.MILLISECONDS)) {
                                // ExoPlayer letterboxes the 1280x720 clip in the view; crop the
                                // video rect back out so faces stay in video coordinates.
                                val scale = minOf(w / 1280f, h / 720f)
                                val vw = (1280 * scale).toInt(); val vh = (720 * scale).toInt()
                                val vx = (w - vw) / 2; val vy = (h - vh) / 2
                                if (vw > 0 && vh > 0) {
                                    val frame = Bitmap.createScaledBitmap(Bitmap.createBitmap(shot, vx, vy, vw, vh), 1280, 720, true)
                                    processor.process(frame)
                                }
                            }
                        } catch (t: Throwable) {
                            Log.w("VoiceBeamVision", "debug frame copy failed", t)
                        }
                    }
                    Thread.sleep(150)
                }
            } catch (_: InterruptedException) {
            } finally {
                copyThread.quitSafely()
            }
        }, "vb-debugvideo").also { it.start() }
    }

    fun stop() {
        running.set(false)
        thread?.join(1500); thread = null
        processor.close()
    }
}
