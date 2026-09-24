package com.akashrajeev.voicebeam.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import com.akashrajeev.voicebeam.core.FaceObservation
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Debug builds only: feeds pre-extracted frames of the bundled two-person clip
 * through the real face pipeline, so tap-to-lock, the lip gate and captions can
 * be tested without pointing the camera at real people. Frames are JPEGs decoded
 * in software - no MediaCodec, so this works on emulators whose video decoder is
 * broken - and the same frame is handed to the UI so the demo screen shows what
 * is being analysed.
 *
 * On a real phone MediaPipe measures lips on-device and results flow straight
 * through. On the x86_64 emulator the ML Kit fallback detects face boxes well
 * but too slowly for the lip gate's 700 ms window (and its lip contours are
 * unreliable on this footage), so in that mode the feed keeps boxes from the
 * latest detection and replays mouth-openness values measured ahead of time on
 * the host by the same MediaPipe face landmarker the app ships (lips.json).
 * Everything downstream - tracking, LipActivity, the gate - is the real code.
 */
class DebugVideoFeed(context: Context, sink: FaceSink, private val onFrame: (Bitmap) -> Unit) {

    private val app = context.applicationContext
    private val latest = AtomicReference<List<FaceObservation>>(emptyList())
    private val processor: FaceProcessor
    private val fallbackMode: Boolean
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null

    init {
        var proc: FaceProcessor? = null
        proc = FaceProcessor(context, FaceSink { t, faces, w, h ->
            if (proc?.usingFallback == true) latest.set(faces) else sink.onFaces(t, faces, w, h)
        })
        processor = proc
        fallbackMode = proc.usingFallback
        if (fallbackMode) Log.i("VoiceBeamVision", "debug feed using ML Kit boxes + host-measured lips")
    }

    val available: Boolean get() = processor.available

    fun start() {
        if (running.getAndSet(true)) return
        val lips = if (fallbackMode) try {
            JSONObject(app.assets.open("feed/lips.json").use { it.readBytes().decodeToString() }).getJSONArray("frames")
        } catch (t: Throwable) {
            Log.w("VoiceBeamVision", "lips.json missing, using ML Kit lip values", t); null
        } else null
        thread = Thread({
            try {
                val started = SystemClock.uptimeMillis()
                var lastIdx = -1
                while (running.get()) {
                    // 480 frames at 8 fps = 60 s loop, matching the clip. The lip
                    // gate needs several samples inside its 700 ms window.
                    val idx = (((SystemClock.uptimeMillis() - started) / 125L) % 480L).toInt() + 1
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
                            if (fallbackMode && lips != null) {
                                processor.process(bmp) // keeps boxes fresh at ML Kit's pace
                                val entry = lips.optJSONObject(idx - 1)
                                val faces = latest.get()
                                if (entry != null && faces.isNotEmpty()) {
                                    val out = faces.map { f ->
                                        val v = if (f.box.cx < 0.5f) entry.optDouble("l") else entry.optDouble("r")
                                        f.copy(mouthOpenness = v.toFloat())
                                    }
                                    sink.onFaces(SystemClock.uptimeMillis(), out, 640, 360)
                                }
                            } else {
                                processor.process(bmp)
                            }
                        }
                    }
                    Thread.sleep(40)
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
