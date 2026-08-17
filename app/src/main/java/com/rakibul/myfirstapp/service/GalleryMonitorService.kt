package com.rakibul.myfirstapp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rakibul.myfirstapp.R

data class GalleryStats(
    val photoCount: Int,
    val videoCount: Int,
    val documentCount: Int
)

data class MediaItemDetails(
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val dateAdded: Long
)

class GalleryMonitorService : Service() {

    private var mediaObserver: ContentObserver? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startGalleryMonitoring()
            ACTION_STOP -> stopGalleryMonitoring()
        }
        return START_STICKY
    }

    private fun startGalleryMonitoring() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Gallery & Media Monitor Active")
            .setContentText("Monitoring photo, video & document changes in real time...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            }
            startForeground(NOTIFICATION_ID, notification, serviceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        registerContentObservers()
        Log.d(TAG, "🟢 Gallery ContentObserver successfully registered for Images, Videos, & Documents.")
        
        // Initial scan log
        queryAndLogGalleryStats("INITIAL SCAN")
    }

    private fun registerContentObservers() {
        mediaObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                val mediaType = when {
                    uri?.toString()?.contains("images") == true -> "IMAGE"
                    uri?.toString()?.contains("video") == true -> "VIDEO"
                    else -> "DOCUMENT / FILE"
                }
                queryAndLogGalleryStats(mediaType)
            }
        }

        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            mediaObserver!!
        )

        contentResolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            true,
            mediaObserver!!
        )

        contentResolver.registerContentObserver(
            MediaStore.Files.getContentUri("external"),
            true,
            mediaObserver!!
        )
    }

    private fun queryAndLogGalleryStats(triggerSource: String) {
        try {
            val photoCount = getMediaCount(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            val videoCount = getMediaCount(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            val documentCount = getDocumentCount()

            val latestPhoto = getLatestMediaDetails(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            val latestVideo = getLatestMediaDetails(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)

            when (triggerSource) {
                "IMAGE" -> {
                    val logMessage = """
                        ===========================================================================
                        🖼️ [MEDIA GALLERY MONITOR LOG]
                        ============================== IMAGE DETECTED ==============================
                        Total Photos    : $photoCount
                        Total Videos    : $videoCount
                        Total Documents : $documentCount
                        ----------------------------------------------------
                        📸 Latest Photo Details:
                          Name      : ${latestPhoto?.name ?: "N/A"}
                          MimeType  : ${latestPhoto?.mimeType ?: "N/A"}
                          Size      : ${formatFileSize(latestPhoto?.sizeBytes ?: 0)}
                          DateAdded : ${latestPhoto?.dateAdded ?: "N/A"}
                        ===========================================================================
                    """.trimIndent()
                    Log.d(TAG, logMessage)
                }

                "VIDEO" -> {
                    val logMessage = """
                        ===========================================================================
                        🎥 [MEDIA GALLERY MONITOR LOG]
                        ============================== VIDEO DETECTED ==============================
                        Total Photos    : $photoCount
                        Total Videos    : $videoCount
                        Total Documents : $documentCount
                        ----------------------------------------------------
                        🎬 Latest Video Details:
                          Name      : ${latestVideo?.name ?: "N/A"}
                          MimeType  : ${latestVideo?.mimeType ?: "N/A"}
                          Size      : ${formatFileSize(latestVideo?.sizeBytes ?: 0)}
                          DateAdded : ${latestVideo?.dateAdded ?: "N/A"}
                        ===========================================================================
                    """.trimIndent()
                    Log.d(TAG, logMessage)
                }

                else -> {
                    val logMessage = """
                        ===========================================================================
                        📁 [MEDIA GALLERY MONITOR LOG]
                        ============================ $triggerSource ============================
                        Total Photos    : $photoCount
                        Total Videos    : $videoCount
                        Total Documents : $documentCount
                        ----------------------------------------------------
                        📸 Latest Photo: ${latestPhoto?.name ?: "None"} (${formatFileSize(latestPhoto?.sizeBytes ?: 0)})
                        🎬 Latest Video: ${latestVideo?.name ?: "None"} (${formatFileSize(latestVideo?.sizeBytes ?: 0)})
                        ===========================================================================
                    """.trimIndent()
                    Log.d(TAG, logMessage)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying MediaStore stats: ${e.message}", e)
        }
    }

    private fun getMediaCount(contentUri: Uri): Int {
        val cursor = contentResolver.query(contentUri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)
        val count = cursor?.count ?: 0
        cursor?.close()
        return count
    }

    private fun getDocumentCount(): Int {
        val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ? OR ${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ?"
        val selectionArgs = arrayOf("application/%", "text/%")
        val cursor = contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            arrayOf(MediaStore.Files.FileColumns._ID),
            selection,
            selectionArgs,
            null
        )
        val count = cursor?.count ?: 0
        cursor?.close()
        return count
    }

    private fun getLatestMediaDetails(contentUri: Uri): MediaItemDetails? {
        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED
        )
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        val cursor = contentResolver.query(contentUri, projection, null, null, sortOrder)

        var details: MediaItemDetails? = null
        if (cursor != null && cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val dateIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)

            details = MediaItemDetails(
                name = if (nameIndex >= 0) cursor.getString(nameIndex) ?: "Unknown" else "Unknown",
                mimeType = if (mimeIndex >= 0) cursor.getString(mimeIndex) ?: "Unknown" else "Unknown",
                sizeBytes = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L,
                dateAdded = if (dateIndex >= 0) cursor.getLong(dateIndex) else 0L
            )
        }
        cursor?.close()
        return details
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes Bytes"
        }
    }

    private fun stopGalleryMonitoring() {
        Log.d(TAG, "🛑 Stopping Gallery ContentObserver service...")
        mediaObserver?.let { contentResolver.unregisterContentObserver(it) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Gallery Monitor Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification required for background MediaStore change monitoring."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val CHANNEL_ID = "gallery_monitor_channel"
        const val NOTIFICATION_ID = 2002
        private const val TAG = "GalleryMonitorService"
    }
}
