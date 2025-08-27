package com.example.androidphp.utils

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.*
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject
import java.io.File

interface WebViewProvider {
    fun getWebView(): WebView
}

class NativeActionCoordinator : Fragment() {

    private var pendingCameraUri: Uri? = null
    private var activeLocationCallback: LocationCallback? = null
    private var activeLocationClient: FusedLocationProviderClient? = null
    private var locationRequestCounter: Int = 0

    // Location permission launcher
    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            Log.d("NativeActionCoordinator", "🔒 Location permission callback triggered")
            
            val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
            
            val locationPermissionStatus = when {
                fineLocationGranted -> "granted"
                coarseLocationGranted -> "granted"
                else -> "denied"
            }
            
            Log.d("NativeActionCoordinator", "📋 Permission results - Fine: $fineLocationGranted, Coarse: $coarseLocationGranted")
            
            val payload = JSONObject().apply {
                put("location", locationPermissionStatus)
                put("coarseLocation", if (coarseLocationGranted) "granted" else "denied")
                put("fineLocation", if (fineLocationGranted) "granted" else "denied")
            }
            
            dispatch("Native\\Mobile\\Events\\Geolocation\\PermissionRequestResult", payload.toString())
        }

    // Camera launcher
    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            Log.d("NativeActionCoordinator", "📸 cameraLauncher callback triggered. Success: $success")

            if (success && pendingCameraUri != null) {
                val context = requireContext()
                val dst = File(context.cacheDir, "captured.jpg")

                try {
                    context.contentResolver.openInputStream(pendingCameraUri!!)!!.use { input ->
                        dst.outputStream().use { output -> input.copyTo(output) }
                    }
                    context.contentResolver.delete(pendingCameraUri!!, null, null)

                    val payload = JSONObject().apply {
                        put("path", dst.absolutePath)
                    }

                    dispatch("Native\\Mobile\\Events\\Camera\\PhotoTaken", payload.toString())
                } catch (e: Exception) {
                    Log.e("NativeActionCoordinator", "❌ Error processing camera photo: ${e.message}")
                    Toast.makeText(context, "Failed to save photo", Toast.LENGTH_SHORT).show()
                }
            } else {
                val context = requireContext()
                val message = when {
                    !success -> "Camera capture was canceled"
                    pendingCameraUri == null -> "Camera error occurred"
                    else -> "Camera operation failed"
                }
                Log.e("NativeActionCoordinator", "❌ $message")
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

            }
        }

        fun launchBiometricPrompt() {
            val context = requireContext()
            val activity = requireActivity()

            val biometricManager = BiometricManager.from(context)
            val status = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
            )

            if (status != BiometricManager.BIOMETRIC_SUCCESS) {
                val message = when (status) {
                    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                        "This device has no biometric hardware."
                    BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                        "Biometric hardware is currently unavailable."
                    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                        "No biometric credentials are enrolled."
                    else ->
                        "Biometric authentication is not available."
                }

                NativeActions.showToast(context, message)
                dispatch("Native\\Mobile\\Events\\Biometric\\Completed", """{"success": false}""")
            }

            val executor = ContextCompat.getMainExecutor(context)
            val biometricPrompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        Log.d("Biometric", "✅ Auth succeeded")
                        dispatch("Native\\Mobile\\Events\\Biometric\\Completed", """{"success": true}""")
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        Log.w("Biometric", "❌ Auth failed")
                        dispatch("Native\\Mobile\\Events\\Biometric\\Completed", """{"success": false}""")
                    }

                    override fun onAuthenticationError(code: Int, msg: CharSequence) {
                        super.onAuthenticationError(code, msg)
                        Log.e("Biometric", "❌ Auth error: $msg")
                        dispatch("Native\\Mobile\\Events\\Biometric\\Completed", """{"success": false}""")
                    }
                }
            )

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Verify Identity")
                .setSubtitle("Biometric authentication required")
                .setNegativeButtonText("Cancel")
                .build()

            biometricPrompt.authenticate(promptInfo)
        }

    fun launchPushTokenDispatch() {
        try{
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.e("PushToken", "❌ Failed to fetch token", task.exception)
                    return@addOnCompleteListener
                }

                val token = task.result ?: return@addOnCompleteListener
                Log.d("PushToken", "✅ Got FCM token: $token")

                val payload = JSONObject().apply {
                    put("token", token)
                }
                dispatch("Native\\Mobile\\Events\\PushNotification\\TokenGenerated", payload.toString())
            }
        } catch (e: Exception) {
            val context = requireContext()
            Log.e("TOKEN ERROR", "❌ FCM init error: ${e.localizedMessage}")
            NativeActions.showToast(context, "Failed to initialize push notifications.")
        }
    }

    // File picker launcher
    private val filePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult
            val payload = JSONObject().apply {
                put("uri", uri.toString())
            }
            dispatch("file:chosen", payload.toString())
        }

    // Gallery launchers
    private val galleryPickerSingle =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            Log.d("NativeActionCoordinator", "📸 Single gallery picker callback triggered")
            Log.d("NativeActionCoordinator", "🔍 Received URI: $uri")

            if (uri != null) {
                Log.d("NativeActionCoordinator", "✅ Single gallery picker - URI received successfully")
                Log.d("NativeActionCoordinator", "📂 URI scheme: ${uri.scheme}")
                Log.d("NativeActionCoordinator", "📂 URI authority: ${uri.authority}")
                Log.d("NativeActionCoordinator", "📂 URI path: ${uri.path}")

                Log.d("NativeActionCoordinator", "📁 Processing single file - moving to background thread")
                
                // Process file in background thread for consistency
                Thread {
                    try {
                        val context = requireContext()
                        val timestamp = System.currentTimeMillis()
                        val dst = File(context.cacheDir, "gallery_selected_$timestamp")

                        Log.d("NativeActionCoordinator", "🧵 Background copying file to cache")

                        // Use buffered streams for better performance
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            dst.outputStream().buffered().use { output -> 
                                input.copyTo(output, bufferSize = 8192)
                            }
                        }

                        Log.d("NativeActionCoordinator", "✅ File copied successfully")

                        // Get file metadata
                        val fileMetadata = getFileMetadata(uri, dst.absolutePath)
                        val filesArray = org.json.JSONArray()
                        filesArray.put(fileMetadata)

                        val payload = JSONObject().apply {
                            put("success", true)
                            put("files", filesArray)
                            put("count", 1)
                        }

                        // Dispatch on main thread
                        activity?.runOnUiThread {
                            Log.d("NativeActionCoordinator", "📤 Dispatching MediaSelected event with payload: ${payload.toString()}")
                            dispatch("Native\\Mobile\\Events\\Gallery\\MediaSelected", payload.toString())
                            Log.d("NativeActionCoordinator", "✅ Single gallery picker - Event dispatched successfully")
                        }
                    } catch (e: Exception) {
                        Log.e("NativeActionCoordinator", "❌ Error processing gallery file in background: ${e.message}", e)
                        
                        activity?.runOnUiThread {
                            val payload = JSONObject().apply {
                                put("success", false)
                                put("files", org.json.JSONArray())
                                put("count", 0)
                                put("error", "Failed to process file: ${e.message}")
                            }
                            dispatch("Native\\Mobile\\Events\\Gallery\\MediaSelected", payload.toString())
                        }
                    }
                }.start()
            } else {
                Log.d("NativeActionCoordinator", "⚠️ Gallery picker was cancelled - URI is null")
                Log.d("NativeActionCoordinator", "❌ Single gallery picker - No file selected or operation cancelled")

                val payload = JSONObject().apply {
                    put("success", false)
                    put("files", org.json.JSONArray())
                    put("count", 0)
                    put("cancelled", true)
                }

                Log.d("NativeActionCoordinator", "📤 Dispatching MediaSelected event (cancelled) with payload: ${payload.toString()}")
                dispatch("Native\\Mobile\\Events\\Gallery\\MediaSelected", payload.toString())
                Log.d("NativeActionCoordinator", "✅ Single gallery picker - Cancellation event dispatched successfully")
            }
        }

    private val galleryPickerMultiple =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
            Log.d("NativeActionCoordinator", "📸 Multiple gallery picker callback triggered with ${uris.size} items")

            if (uris.isNotEmpty()) {
                Log.d("NativeActionCoordinator", "📁 Processing ${uris.size} files - moving to background thread")
                
                // Process files in background thread to avoid blocking UI
                Thread {
                    try {
                        val context = requireContext()
                        val filesArray = org.json.JSONArray()
                        val timestamp = System.currentTimeMillis()

                        Log.d("NativeActionCoordinator", "🧵 Background processing ${uris.size} files")

                        uris.forEachIndexed { index, uri ->
                            // Only log every few files to reduce output
                            if (index == 0 || (index + 1) % 3 == 0 || index == uris.size - 1) {
                                Log.d("NativeActionCoordinator", "📂 Processing file ${index + 1}/${uris.size}")
                            }

                            val dst = File(context.cacheDir, "gallery_selected_${timestamp}_$index")

                            // Use buffered streams for better performance
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                dst.outputStream().buffered().use { output -> 
                                    input.copyTo(output, bufferSize = 8192)
                                }
                            }

                            // Get file metadata and add to array
                            val fileMetadata = getFileMetadata(uri, dst.absolutePath)
                            filesArray.put(fileMetadata)
                        }
                        
                        Log.d("NativeActionCoordinator", "✅ All ${uris.size} files processed successfully")

                        val payload = JSONObject().apply {
                            put("success", true)
                            put("files", filesArray)
                            put("count", uris.size)
                        }

                        // Dispatch on main thread
                        activity?.runOnUiThread {
                            Log.d("NativeActionCoordinator", "📤 Dispatching MediaSelected event with ${uris.size} files")
                            dispatch("Native\\Mobile\\Events\\Gallery\\MediaSelected", payload.toString())
                            Log.d("NativeActionCoordinator", "✅ Multiple gallery picker - Event dispatched successfully")
                        }
                    } catch (e: Exception) {
                        Log.e("NativeActionCoordinator", "❌ Error processing gallery files in background: ${e.message}", e)
                        
                        activity?.runOnUiThread {
                            val payload = JSONObject().apply {
                                put("success", false)
                                put("files", org.json.JSONArray())
                                put("count", 0)
                                put("error", "Failed to process files: ${e.message}")
                            }
                            dispatch("Native\\Mobile\\Events\\Gallery\\MediaSelected", payload.toString())
                        }
                    }
                }.start()
            } else {
                Log.d("NativeActionCoordinator", "⚠️ Gallery picker was cancelled or no files selected")
                val payload = JSONObject().apply {
                    put("success", false)
                    put("files", org.json.JSONArray())
                    put("count", 0)
                    put("cancelled", true)
                }
                dispatch("Native\\Mobile\\Events\\Gallery\\MediaSelected", payload.toString())
            }
        }

    fun launchCamera() {
        val context = requireContext()
        val resolver = context.contentResolver

        val photoUri = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.Images.Media.TITLE, "NativePHP_${System.currentTimeMillis()}")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            }
        ) ?: return

        pendingCameraUri = photoUri
        Log.d("CAMERAFILE", pendingCameraUri.toString());
        cameraLauncher.launch(photoUri)
    }

    fun launchFilePicker(mime: String = "*/*") {
        filePicker.launch(arrayOf(mime))
    }

    private fun getFileMetadata(uri: Uri, cachePath: String): JSONObject {
        val context = requireContext()
        val metadata = JSONObject()
        
        try {
            // Get MIME type
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            
            // Determine file extension from MIME type
            val extension = when {
                mimeType.startsWith("image/jpeg") -> "jpg"
                mimeType.startsWith("image/png") -> "png"
                mimeType.startsWith("image/gif") -> "gif"
                mimeType.startsWith("image/webp") -> "webp"
                mimeType.startsWith("video/mp4") -> "mp4"
                mimeType.startsWith("video/avi") -> "avi"
                mimeType.startsWith("video/mov") -> "mov"
                mimeType.startsWith("video/3gp") -> "3gp"
                mimeType.startsWith("video/webm") -> "webm"
                else -> {
                    // Try to extract from MIME type
                    val parts = mimeType.split("/")
                    if (parts.size == 2) parts[1] else "bin"
                }
            }
            
            // Determine file type category
            val type = when {
                mimeType.startsWith("image/") -> "image"
                mimeType.startsWith("video/") -> "video"
                mimeType.startsWith("audio/") -> "audio"
                else -> "other"
            }
            
            metadata.apply {
                put("path", cachePath)
                put("mimeType", mimeType)
                put("extension", extension)
                put("type", type)
            }
            
            // Reduced logging to prevent buffer overflow
            // Log.d("NativeActionCoordinator", "📄 File metadata: path=$cachePath, mimeType=$mimeType, extension=$extension, type=$type")
            
        } catch (e: Exception) {
            Log.e("NativeActionCoordinator", "❌ Error getting file metadata", e)
            // Fallback metadata
            metadata.apply {
                put("path", cachePath)
                put("mimeType", "application/octet-stream")
                put("extension", "bin")
                put("type", "other")
            }
        }
        
        return metadata
    }

    fun launchGallery(mediaType: String, multiple: Boolean, maxItems: Int) {
        Log.d("NativeActionCoordinator", "🖼️ launchGallery: mediaType=$mediaType, multiple=$multiple, maxItems=$maxItems")

        // Convert media type to PickVisualMedia type
        val visualMediaType = when (mediaType.lowercase()) {
            "image", "images" -> ActivityResultContracts.PickVisualMedia.ImageOnly
            "video", "videos" -> ActivityResultContracts.PickVisualMedia.VideoOnly
            "all", "*" -> ActivityResultContracts.PickVisualMedia.ImageAndVideo
            else -> ActivityResultContracts.PickVisualMedia.ImageAndVideo // default fallback
        }

        Log.d("NativeActionCoordinator", "📂 Using visual media type: $visualMediaType")

        if (multiple) {
            Log.d("NativeActionCoordinator", "🚀 Launching multiple gallery picker with type: $visualMediaType")
            // Use PickMultipleVisualMedia with max items limit
            val request = androidx.activity.result.PickVisualMediaRequest.Builder()
                .setMediaType(visualMediaType)
                .build()
            galleryPickerMultiple.launch(request)
        } else {
            Log.d("NativeActionCoordinator", "🚀 Launching single gallery picker with type: $visualMediaType")
            Log.d("NativeActionCoordinator", "📋 Single picker - maxItems parameter ignored (value: $maxItems)")
            // Use PickVisualMedia
            val request = androidx.activity.result.PickVisualMediaRequest.Builder()
                .setMediaType(visualMediaType)
                .build()
            galleryPickerSingle.launch(request)
            Log.d("NativeActionCoordinator", "✅ Single gallery picker launched successfully")
        }
    }

    fun launchLocationRequest(fineAccuracy: Boolean) {
        locationRequestCounter++
        val requestId = locationRequestCounter
        Log.d("LOCATION_REQUEST", "📍 Starting location request #$requestId (fineAccuracy: $fineAccuracy)")
        
        // Check if there's already an active location request
        if (activeLocationCallback != null || activeLocationClient != null) {
            Log.w("LOCATION_REQUEST", "⚠️ Cleaning up previous location request first")
            stopActiveLocationRequest()
        }
        
        val context = requireContext()
        
        // Check if location permissions are granted
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            context, 
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            context, 
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        if (!fineLocationGranted && !coarseLocationGranted) {
            Log.e("LOCATION_REQUEST", "❌ No location permissions granted")
            val payload = JSONObject().apply {
                put("success", false)
                put("latitude", null)
                put("longitude", null)
                put("accuracy", null)
                put("timestamp", System.currentTimeMillis())
                put("provider", null)
                put("error", "Location permissions not granted")
            }
            dispatch("Native\\Mobile\\Events\\Geolocation\\LocationReceived", payload.toString())
            return
        }
        
        // Create location client and request fresh location using aggressive updates
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        activeLocationClient = fusedLocationClient
        val priority = if (fineAccuracy && fineLocationGranted) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_HIGH_ACCURACY  // Use high accuracy even for coarse to force fresh location
        }
        
        Log.d("LOCATION_REQUEST", "🚀 Requesting fresh location with aggressive updates")
        
        try {
            // Create aggressive location request to force fresh location (not cached)
            val locationRequest = LocationRequest.Builder(priority, 1000L) // 1 second interval
                .setMinUpdateIntervalMillis(500L) // 0.5 second minimum interval 
                .build()
            
            // Create callback that stops updates after first fresh result
            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    super.onLocationResult(locationResult)
                    
                    val location = locationResult.lastLocation
                    if (location != null) {
                        Log.d("LOCATION_REQUEST", "✅ Location received: lat=${location.latitude}, lng=${location.longitude}")
                        
                        val payload = JSONObject().apply {
                            put("success", true)
                            put("latitude", location.latitude)
                            put("longitude", location.longitude)
                            put("accuracy", location.accuracy)
                            put("timestamp", System.currentTimeMillis())
                            put("provider", location.provider ?: "fused")
                            put("error", false)
                        }
                        dispatch("Native\\Mobile\\Events\\Geolocation\\LocationReceived", payload.toString())
                        stopActiveLocationRequest()
                    } else {
                        Log.e("LOCATION_REQUEST", "❌ Location result was null")
                        val payload = JSONObject().apply {
                            put("success", false)
                            put("latitude", null)
                            put("longitude", null)
                            put("accuracy", null)
                            put("timestamp", System.currentTimeMillis())
                            put("provider", null)
                            put("error", "Location result was null")
                        }
                        dispatch("Native\\Mobile\\Events\\Geolocation\\LocationReceived", payload.toString())
                        stopActiveLocationRequest()
                    }
                }
            }
            
            activeLocationCallback = locationCallback
            
            // Start aggressive location updates to force fresh location
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            ).addOnSuccessListener {
                Log.d("LOCATION_REQUEST", "✅ Location updates started")
            }.addOnFailureListener { exception ->
                Log.e("LOCATION_REQUEST", "❌ Failed to start location updates: ${exception.message}")
                val payload = JSONObject().apply {
                    put("success", false)
                    put("latitude", null)
                    put("longitude", null)
                    put("accuracy", null)
                    put("timestamp", System.currentTimeMillis())
                    put("provider", null)
                    put("error", "Failed to start location updates: ${exception.message}")
                }
                dispatch("Native\\Mobile\\Events\\Geolocation\\LocationReceived", payload.toString())
                stopActiveLocationRequest()
            }
            
            // Set a timeout in case no location is received
            android.os.Handler(Looper.getMainLooper()).postDelayed({
                if (activeLocationCallback === locationCallback) {
                    Log.w("LOCATION_REQUEST", "⏰ Location request timed out")
                    stopActiveLocationRequest()
                    val payload = JSONObject().apply {
                        put("success", false)
                        put("latitude", null)
                        put("longitude", null)
                        put("accuracy", null)
                        put("timestamp", System.currentTimeMillis())
                        put("provider", null)
                        put("error", "Location request timed out")
                    }
                    dispatch("Native\\Mobile\\Events\\Geolocation\\LocationReceived", payload.toString())
                }
            }, 15000L) // 15 second timeout
        } catch (e: SecurityException) {
            Log.e("LOCATION_REQUEST", "❌ Security exception: ${e.message}")
            val payload = JSONObject().apply {
                put("success", false)
                put("latitude", null)
                put("longitude", null)
                put("accuracy", null)
                put("timestamp", System.currentTimeMillis())
                put("provider", null)
                put("error", "Security exception: ${e.message}")
            }
            dispatch("Native\\Mobile\\Events\\Geolocation\\LocationReceived", payload.toString())
        }
    }
    
    private fun stopActiveLocationRequest() {
        try {
            if (activeLocationCallback != null && activeLocationClient != null) {
                activeLocationClient?.removeLocationUpdates(activeLocationCallback!!)
                    ?.addOnSuccessListener {
                        Log.d("LOCATION_REQUEST", "✅ Location updates stopped")
                    }
                    ?.addOnFailureListener { exception ->
                        Log.e("LOCATION_REQUEST", "❌ Failed to stop location updates: ${exception.message}")
                    }
                activeLocationCallback = null
                activeLocationClient = null
            }
        } catch (e: Exception) {
            Log.e("LOCATION_REQUEST", "❌ Error stopping location request", e)
            // Force clear even if there was an error
            activeLocationCallback = null
            activeLocationClient = null
        }
    }

    fun launchLocationPermissionCheck() {
        Log.d("NativeActionCoordinator", "🔒 launchLocationPermissionCheck called")
        
        val context = requireContext()
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            context, 
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            context, 
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val locationPermissionStatus = when {
            fineLocationGranted -> "granted"
            coarseLocationGranted -> "granted"
            else -> "denied"
        }
        
        Log.d("NativeActionCoordinator", "📋 Permission status - Fine: $fineLocationGranted, Coarse: $coarseLocationGranted")
        
        val payload = JSONObject().apply {
            put("location", locationPermissionStatus)
            put("coarseLocation", if (coarseLocationGranted) "granted" else "denied")
            put("fineLocation", if (fineLocationGranted) "granted" else "denied")
        }
        
        dispatch("Native\\Mobile\\Events\\Geolocation\\PermissionStatusReceived", payload.toString())
    }

    fun launchLocationPermissionRequest() {
        Log.d("NativeActionCoordinator", "🔒 launchLocationPermissionRequest called")
        
        val context = requireContext()
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            context, 
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            context, 
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        // If permissions are already granted, no need to request
        if (fineLocationGranted || coarseLocationGranted) {
            Log.d("NativeActionCoordinator", "ℹ️ Location permissions already granted")
            val payload = JSONObject().apply {
                put("location", "granted")
                put("coarseLocation", if (coarseLocationGranted) "granted" else "denied")
                put("fineLocation", if (fineLocationGranted) "granted" else "denied")
            }
            dispatch("Native\\Mobile\\Events\\Geolocation\\PermissionRequestResult", payload.toString())
            return
        }
        
        // Check if we should show rationale or if permissions are permanently denied
        val shouldShowFineRationale = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
        val shouldShowCoarseRationale = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
        
        if (!shouldShowFineRationale && !shouldShowCoarseRationale) {
            // Check if this is first time asking or permissions are permanently denied
            val activity = requireActivity()
            val prefs = activity.getSharedPreferences("location_permissions", 0)
            val hasAskedBefore = prefs.getBoolean("has_asked_location_permissions", false)
            
            if (hasAskedBefore) {
                // Permissions are permanently denied, direct user to Settings
                Log.w("NativeActionCoordinator", "⚠️ Location permissions permanently denied, directing to Settings")
                val payload = JSONObject().apply {
                    put("location", "permanently_denied")
                    put("coarseLocation", "permanently_denied")
                    put("fineLocation", "permanently_denied")
                    put("message", "Location permissions were denied. Please enable them in Settings > App Permissions > Location")
                    put("needsSettings", true)
                }
                dispatch("Native\\Mobile\\Events\\Geolocation\\PermissionRequestResult", payload.toString())
                
                // Optionally open Settings directly
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", activity.packageName, null)
                    }
                    activity.startActivity(intent)
                } catch (e: Exception) {
                    Log.e("NativeActionCoordinator", "Failed to open Settings: ${e.message}")
                }
                return
            } else {
                // First time asking, mark that we've asked
                prefs.edit().putBoolean("has_asked_location_permissions", true).apply()
            }
        }
        
        // Request permissions normally
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        Log.d("NativeActionCoordinator", "🚀 Requesting location permissions")
        locationPermissionLauncher.launch(permissions)
    }

    fun launchAlert(title: String, message: String, buttons: Array<String>) {
        Log.d("NativeActionCoordinator", "🚨 launchAlert called with title: '$title', message: '$message', buttons: ${buttons.contentToString()}")
        
        val context = requireContext()
        
        // Use NativeActions to show the alert with callback
        NativeActions.showAlert(context, title, message, buttons) { index, label ->
            Log.d("NativeActionCoordinator", "🔘 Alert button clicked: index=$index, label='$label'")
            
            // Create payload for the ButtonPressed event
            val payload = JSONObject().apply {
                put("index", index)
                put("label", label)
            }
            
            // Dispatch the event back to PHP
            dispatch("Native\\Mobile\\Events\\Alert\\ButtonPressed", payload.toString())
        }
    }

    private fun dispatch(event: String, payloadJson: String) {
        Log.d("JSFUNC", "native:$event");
        Log.d("JSFUNC", "$payloadJson");
        val eventForJs = event.replace("\\", "\\\\")
        val js = """
            (function () {
                const payload = $payloadJson;
                const detail = { event: "$event", payload };
                document.dispatchEvent(new CustomEvent("native-event", { detail }));
                if (window.Livewire && typeof window.Livewire.dispatch === 'function') {
                    window.Livewire.dispatch("native:$eventForJs", payload);
                }
                fetch('/_native/api/events', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-Requested-With': 'XMLHttpRequest'
                    },
                    body: JSON.stringify({
                        event: "$eventForJs",
                        payload: payload
                    })
                }).then(response => response.json())
                  .then(data => {
                      if (data.message && data.message.includes("Unknown named parameter")) {
                          console.log("API Event Dispatch: Parameter issue detected");
                      } else {
                          console.log("API Event Dispatch Success");
                      }
                  })
                  .catch(error => console.error("API Event Dispatch Error:", error.message));
            })();
        """.trimIndent()

        Log.d("NativeActionCoordinator", "📢 Dispatching JS event: $event")

        (activity as? WebViewProvider)?.getWebView()?.evaluateJavascript(js, null)
    }

    companion object {
        fun install(activity: FragmentActivity): NativeActionCoordinator =
            activity.supportFragmentManager.findFragmentByTag("NativeActionCoordinator") as? NativeActionCoordinator
                ?: NativeActionCoordinator().also {
                    activity.supportFragmentManager.beginTransaction()
                        .add(it, "NativeActionCoordinator")
                        .commitNow()
                }
    }
}