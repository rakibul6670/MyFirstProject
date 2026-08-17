package com.rakibul.myfirstapp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.rakibul.myfirstapp.R

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                super.onLocationResult(result)
                result.locations.forEach { location ->
                    val statusMessage = """
                        ====================================================
                        📍 [LOCATION FOREGROUND SERVICE LOG]
                        Status    : ACTIVE TRACKING
                        Latitude  : ${location.latitude}
                        Longitude : ${location.longitude}
                        Accuracy  : ${location.accuracy}m
                        Timestamp : ${System.currentTimeMillis()}
                        ----------------------------------------------------
                        🔥 [FIREBASE READY PAYLOAD]:
                        {
                          "latitude": ${location.latitude},
                          "longitude": ${location.longitude},
                          "accuracy": ${location.accuracy},
                          "timestamp": ${System.currentTimeMillis()}
                        }
                        ====================================================
                    """.trimIndent()

                    Log.d(TAG, statusMessage)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startLocationTracking()
            ACTION_STOP -> stopLocationTracking()
        }
        return START_STICKY
    }

    private fun startLocationTracking() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Real-Time Location Tracking Active")
            .setContentText("Background location updates are currently logging...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(2000L)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d(TAG, "🟢 Location service successfully started with HIGH_ACCURACY.")
        } catch (unlikely: SecurityException) {
            Log.e(TAG, "❌ Missing location permission: ${unlikely.message}")
        }
    }

    private fun stopLocationTracking() {
        Log.d(TAG, "🛑 Stopping location foreground service...")
        fusedLocationClient.removeLocationUpdates(locationCallback)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Tracking Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification required for background location tracking."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val CHANNEL_ID = "location_tracking_channel"
        const val NOTIFICATION_ID = 1001
        private const val TAG = "LocationService"
    }
}
