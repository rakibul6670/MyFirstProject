package com.rakibul.myfirstapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.rakibul.myfirstapp.service.GalleryMonitorService
import com.rakibul.myfirstapp.service.LocationService
import com.rakibul.myfirstapp.ui.theme.MyFirstAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyFirstAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainDashboardScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun MainDashboardScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // State for Location Service
    var isLocationServiceRunning by remember { mutableStateOf(false) }
    var fineLocationGranted by remember {
        mutableStateOf(hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION))
    }
    var backgroundLocationGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                hasPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else true
        )
    }

    // State for Gallery Monitor Service
    var isGalleryServiceRunning by remember { mutableStateOf(false) }
    var mediaImagesGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                hasPermission(context, Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                hasPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        )
    }
    var mediaVideoGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                hasPermission(context, Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                hasPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        )
    }

    var notificationPermissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            } else true
        )
    }

    // Permission launcher for Location & Notifications
    val locationPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: fineLocationGranted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: notificationPermissionGranted
        }
    }

    // Permission launcher for Media (Android 13+ & Legacy)
    val mediaPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mediaImagesGranted = permissions[Manifest.permission.READ_MEDIA_IMAGES] ?: mediaImagesGranted
            mediaVideoGranted = permissions[Manifest.permission.READ_MEDIA_VIDEO] ?: mediaVideoGranted
        } else {
            val storageGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
            mediaImagesGranted = storageGranted
            mediaVideoGranted = storageGranted
        }
    }

    // Background location launcher (Android 10+)
    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        backgroundLocationGranted = granted
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Background Services Monitor",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
        )

        Text(
            text = "Real-time Location & MediaStore Gallery Monitor (Logcat Viewer)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // ================= SECTION 1: GALLERY & MEDIA MONITOR =================
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "🖼️ Gallery & Media Monitor",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                Text(
                    text = if (isGalleryServiceRunning)
                        "🟢 GALLERY MONITOR ACTIVE: Filter Logcat by 'GalleryMonitorService' to see distinct ============== IMAGE and ============== VIDEO logs."
                    else
                        "🔴 GALLERY MONITOR STOPPED",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isGalleryServiceRunning) Color(0xFF2E7D32) else Color(0xFFC62828)
                )

                PermissionStatusItem(label = "Read Media Images", isGranted = mediaImagesGranted)
                PermissionStatusItem(label = "Read Media Videos", isGranted = mediaVideoGranted)

                OutlinedButton(
                    onClick = {
                        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            mutableListOf(
                                Manifest.permission.READ_MEDIA_IMAGES,
                                Manifest.permission.READ_MEDIA_VIDEO
                            ).apply {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                    add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                                }
                            }.toTypedArray()
                        } else {
                            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                        mediaPermissionsLauncher.launch(permissions)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant Gallery/Media Permissions")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            val serviceIntent = Intent(context, GalleryMonitorService::class.java).apply {
                                action = GalleryMonitorService.ACTION_START
                            }
                            ContextCompat.startForegroundService(context, serviceIntent)
                            isGalleryServiceRunning = true
                        },
                        enabled = mediaImagesGranted && !isGalleryServiceRunning,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Text("Start Gallery Monitor")
                    }

                    Button(
                        onClick = {
                            val serviceIntent = Intent(context, GalleryMonitorService::class.java).apply {
                                action = GalleryMonitorService.ACTION_STOP
                            }
                            context.startService(serviceIntent)
                            isGalleryServiceRunning = false
                        },
                        enabled = isGalleryServiceRunning,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                    ) {
                        Text("Stop Gallery Monitor")
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // ================= SECTION 2: LOCATION TRACKER =================
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "📍 Background Location Tracker",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                Text(
                    text = if (isLocationServiceRunning)
                        "🟢 LOCATION TRACKER ACTIVE: Filter Logcat by 'LocationService'"
                    else
                        "🔴 LOCATION TRACKER STOPPED",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isLocationServiceRunning) Color(0xFF2E7D32) else Color(0xFFC62828)
                )

                PermissionStatusItem(label = "Fine / Coarse Location", isGranted = fineLocationGranted)
                PermissionStatusItem(label = "Background Location ('Allow all the time')", isGranted = backgroundLocationGranted)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val permissions = mutableListOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            locationPermissionsLauncher.launch(permissions.toTypedArray())
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Grant Location", fontSize = 12.sp)
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        OutlinedButton(
                            onClick = {
                                backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Grant Background", fontSize = 12.sp)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            val serviceIntent = Intent(context, LocationService::class.java).apply {
                                action = LocationService.ACTION_START
                            }
                            ContextCompat.startForegroundService(context, serviceIntent)
                            isLocationServiceRunning = true
                        },
                        enabled = fineLocationGranted && !isLocationServiceRunning,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Text("Start Location Service")
                    }

                    Button(
                        onClick = {
                            val serviceIntent = Intent(context, LocationService::class.java).apply {
                                action = LocationService.ACTION_STOP
                            }
                            context.startService(serviceIntent)
                            isLocationServiceRunning = false
                        },
                        enabled = isLocationServiceRunning,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                    ) {
                        Text("Stop Location Service")
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionStatusItem(label: String, isGranted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        Text(
            text = if (isGranted) "✓ Granted" else "✗ Missing",
            color = if (isGranted) Color(0xFF2E7D32) else Color(0xFFC62828),
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
        )
    }
}

private fun hasPermission(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}