package com.akashrajeev.voicebeam.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Debug builds only: feeds pre-extracted frames of the bundled two-person clip
 * through the real face pipeline, so tap-to-lock, the lip gate and captions can
 * be tested without pointing the camera at real people. Frames are JPEGs decoded
 * in software - no MediaCodec, so this works on emulators whose video decoder is
 * broken - and the same frame is handed to the UI so the demo screen shows what
 * is being analysed. Frame positions follow the wall clock, staying in sync with
 * the debug audio feed.
 */
class DebugVideoFeed(context: Context, sink: FaceSink, private val onFrame: (Bitmap) -> Unit) {

    private val app = context.applicationContext
    private val processor = FaceProcessor(context, sink)
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null

    val available: Boolean get() = processor.available

    fun start() {
        if (running.getAndSet(true)) return
        thread = Thread({
            try {
                val started = SystemClock.uptimeMillis()
                var lastIdx = -1
                while (running.get()) {
                    // 240 frames at 4 fps = 60 s loop, matching the clip.
                    val idx = (((SystemClock.uptimeMillis() - started) / 250L) % 240L).toInt() + 1
                    if (idx != lastIdx) {
                        lastIdx = idx
                        val name = "feed/frames/f%04d.jpg".format(idx)
                        val bmp = try {
                            app.assets.open(name).use { BitmapFactory.decodeStream(it) }
                        } catch (t: Throwable) {
                            Log.w("VoiceBeamVision", "debug frame load failed: $name", t)
                            null
                        }
                        if (bmp != null) {
                            onFrame(bmp)
                            processor.process(bmp)
                        }
                    }
                    Thread.sleep(60)
                }
            } catch (_: InterruptedException) {
            }
        }, "vb-debugvideo").also { it.start() }
    }

    fun stop() {
        running.set(false)
        thread?.join(1500); thread = null
        processor.close()
    }
}
