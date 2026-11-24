package app.dastyar.agent

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred

/** One-shot screenshot. The system consent dialog is shown by the activity before this starts. */
class ProjectionForegroundService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private val finished = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifications.ensure(this)
        val notification = notification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(ID, notification)
        }
        val data = ProjectionSession.data
        val code = ProjectionSession.resultCode
        if (data == null || code != android.app.Activity.RESULT_OK) {
            finish(null)
            return START_NOT_STICKY
        }
        try {
            capture(code, data)
        } catch (_: Exception) {
            finish(null)
        }
        return START_NOT_STICKY
    }

    private fun capture(code: Int, data: Intent) {
        val manager = getSystemService(MediaProjectionManager::class.java)
        val projection = manager.getMediaProjection(code, data)
        this.projection = projection
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                finish(null)
            }
        }, Handler(mainLooper))
        val metrics = resources.displayMetrics
        var width = metrics.widthPixels.coerceAtLeast(1)
        var height = metrics.heightPixels.coerceAtLeast(1)
        val longEdge = maxOf(width, height)
        if (longEdge > 1280) {
            val scale = 1280f / longEdge
            width = (width * scale).toInt().coerceAtLeast(1)
            height = (height * scale).toInt().coerceAtLeast(1)
        }
        val worker = HandlerThread("dastyar-shot").also { it.start() }
        thread = worker
        val handler = Handler(worker.looper)
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        this.reader = reader
        reader.setOnImageAvailableListener({ imageReader ->
            val image = imageReader.acquireLatestImage() ?: return@setOnImageAvailableListener
            val path = runCatching { writeJpeg(image, width, height) }.getOrNull()
            image.close()
            finish(path)
        }, handler)
        display = projection.createVirtualDisplay(
            "dastyar",
            width,
            height,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            handler,
        )
        handler.postDelayed({ finish(null) }, 12_000)
    }

    private fun writeJpeg(image: android.media.Image, width: Int, height: Int): String {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width
        val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
        if (cropped !== bitmap) bitmap.recycle()
        val file = File(cacheDir, "shot-${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { cropped.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        cropped.recycle()
        return file.absolutePath
    }

    private fun finish(path: String?) {
        if (!finished.compareAndSet(false, true)) return
        ProjectionSession.waiter?.complete(path)
        ProjectionSession.waiter = null
        ProjectionSession.data = null
        runCatching { display?.release() }
        runCatching { reader?.close() }
        runCatching { projection?.stop() }
        display = null
        reader = null
        projection = null
        thread?.quitSafely()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notification(): Notification {
        val fa = Bootstrap.language(this).startsWith("fa")
        return NotificationCompat.Builder(this, Notifications.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(if (fa) "گرفتن یک نماگرفت" else "Capturing one screenshot")
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ID = 7102
    }
}

object ProjectionSession {
    @Volatile var resultCode: Int = 0
    @Volatile var data: Intent? = null
    @Volatile var waiter: CompletableDeferred<String?>? = null
}
