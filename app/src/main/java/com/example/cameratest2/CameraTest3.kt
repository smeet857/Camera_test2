package com.example.cameratest2

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.tabs.TabLayout
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sqrt

enum class ZoomType {
    ZOOM1X,
    ZOOM2X,
}

enum class CameraMode{
    PHOTO,
    VIDEO
}

class CameraTest3 : ComponentActivity() {
    private var wideId: String = ""
    private var ultraWideId: String = ""
    private var telephotoId: String = ""

    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    lateinit var cameraManager: CameraManager
    var physicalIds: Set<String> = emptySet()
    private var logicalCameraId = ""

    private val surface1 = mutableStateOf<Surface?>(null)
    val surface2 = mutableStateOf<Surface?>(null)

    private val zoomType = mutableStateOf(ZoomType.ZOOM1X)

    // Add camera state tracking
    private var isCameraOpening = false
    private var isCameraClosing = false
    private var shouldOpenCamera = false

    private val topLensId: String
        get() = if (zoomType.value == ZoomType.ZOOM2X) telephotoId else wideId

    private val topLensName: String
        get() = if (zoomType.value == ZoomType.ZOOM2X) "Telephoto" else "Wide"

    private val topLensUnsupportedErrorText: String
        get() = if (zoomType.value == ZoomType.ZOOM2X) "Telephoto lens is not supported" else "Wide lens is not supported"


    private val bottomLensId: String
        get() = if (zoomType.value == ZoomType.ZOOM2X) wideId else ultraWideId

    private val bottomLensName: String
        get() = if (zoomType.value == ZoomType.ZOOM2X) "Wide" else "Ultra Wide"

    private val bottomLensUnsupportedErrorText: String
        get() = if (zoomType.value == ZoomType.ZOOM2X) "Wide lens is not supported" else "Ultra-Wide lens is not supported"


    private lateinit var textureViewTop: TextureView
    private lateinit var textureViewBottom: TextureView
    private lateinit var topErrorText: TextView
    private lateinit var bottomErrorText: TextView
    private lateinit var zoom1x: TextView
    private lateinit var zoom2x: TextView
    private lateinit var captureButton: ImageButton
    private lateinit var recordButton: ImageButton
    private lateinit var modeTabLayout: TabLayout
    private  lateinit var recordTime : TextView


    private var surfaceTopReady = false
    private var surfaceBottomReady = false

    private var imageReaderTop: ImageReader? = null
     private var imageReaderBottom: ImageReader? = null

    private var isRecording = false
    private var cameraMode : CameraMode= CameraMode.PHOTO

    private var mediaRecorderTop: MediaRecorder? = null
    private var mediaRecorderBottom: MediaRecorder? = null

    private var timerHandler: android.os.Handler? = null
    private var timerRunnable: Runnable? = null
    private var startTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager

            for(camera in cameraManager.cameraIdList){
                Log.d("Camera id",camera.toString())
                try {
                    val characteristics = cameraManager.getCameraCharacteristics(camera)
                    val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                    val isLogicalCamera = capabilities?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)
                    if(isLogicalCamera == true){
                        logicalCameraId = camera
                    }
                } catch (e: Exception) {
                    Log.e("CameraTest3", "Error getting camera characteristics for camera $camera", e)
                }
            }

            if(logicalCameraId.isNotEmpty()){
                try {
                    val characteristics =
                        cameraManager.getCameraCharacteristics(logicalCameraId)

                    physicalIds = characteristics.physicalCameraIds

                    for (id in physicalIds) {
                        try {
                            when (getLensType(cameraManager, id)) {
                                LensType.WIDE -> wideId = id
                                LensType.ULTRA_WIDE -> ultraWideId = id
                                LensType.TELEPHOTO -> telephotoId = id
                                else -> {}
                            }
                            Log.d("Lens Type", getLensType(cameraManager, id).name + " physical = > $id")
                        } catch (e: Exception) {
                            Log.e("CameraTest3", "Error processing physical camera $id", e)
                        }
                    }

                    if(physicalIds.isNotEmpty()){
                        setContentView(R.layout.multi_camera_page)

                        topErrorText = findViewById(R.id.top_error_text)
                        bottomErrorText = findViewById(R.id.bottom_error_text)

                        textureViewTop = findViewById(R.id.texture_top)
                        textureViewBottom = findViewById(R.id.texture_bottom)

                        zoom1x = findViewById(R.id.zoom1x)
                        zoom2x = findViewById(R.id.zoom2x)
                        zoom1x.isSelected = true

                        captureButton = findViewById(R.id.captureButton)
                        recordButton = findViewById(R.id.recordButton)

                        recordTime = findViewById(R.id.recordTimer)

                        modeTabLayout = findViewById(R.id.modeTabLayout)

                        modeTabLayout.addTab(modeTabLayout.newTab().setText("PHOTO"))
                        modeTabLayout.addTab(modeTabLayout.newTab().setText("VIDEO"))

                        modeTabLayout.addOnTabSelectedListener(modeSwitchListener)

                        textureViewTop.surfaceTextureListener = surfaceTextureListenerTop
                        textureViewBottom.surfaceTextureListener = surfaceTextureListenerBottom

                        zoom1x.setOnClickListener(zoom1xListener)
                        zoom2x.setOnClickListener(zoom2xListener)

                        captureButton.setOnClickListener(captureListener)
                        recordButton.setOnClickListener(recordListener)
                    }else{
                        setContentView(R.layout.not_supported_multi_camera)
                    }
                } catch (e: Exception) {
                    Log.e("CameraTest3", "Error setting up camera views", e)
                    setContentView(R.layout.not_supported_multi_camera)
                }
            }else{
                setContentView(R.layout.not_supported_multi_camera)
            }
        } catch (e: Exception) {
            Log.e("CameraTest3", "Error in onCreate", e)
            setContentView(R.layout.not_supported_multi_camera)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            shouldOpenCamera = false
            closeCamera()
        } catch (e: Exception) {
            Log.e("CameraTest3", "Error in onPause", e)
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            if(logicalCameraId.isNotEmpty()){
                shouldOpenCamera = true
                if (surfaceTopReady && surfaceBottomReady && !isCameraOpening && !isCameraClosing) {
                    openCamera()
                }
            }
        } catch (e: Exception) {
            Log.e("CameraTest3", "Error in onResume", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            shouldOpenCamera = false
            closeCamera()
        } catch (e: Exception) {
            Log.e("CameraTest3", "Error in onDestroy", e)
        }
    }

    private val modeSwitchListener = object : TabLayout.OnTabSelectedListener {
        override fun onTabSelected(tab: TabLayout.Tab?) {
            when (tab?.position) {
                0 -> switchToPhotoMode()
                1 -> switchToVideoMode()
            }
        }
        override fun onTabUnselected(tab: TabLayout.Tab?) {}
        override fun onTabReselected(tab: TabLayout.Tab?) {}
    }

    private fun switchToPhotoMode(){
        try {
            if (cameraMode == CameraMode.PHOTO) return
            
            cameraMode = CameraMode.PHOTO
            captureButton.visibility = View.VISIBLE
            recordButton.visibility = View.GONE

            closeCamera()
            // Add delay before reopening to ensure proper cleanup
            android.os.Handler(Looper.getMainLooper()).postDelayed({
                try {
                    //if (shouldOpenCamera && surfaceTopReady && surfaceBottomReady) {
                        openCamera()
                    //}
                } catch (e: Exception) {
                    Log.e("CameraTest3", "Error reopening camera after photo mode switch", e)
                }
            }, 500)
        } catch (e: Exception) {
            Log.e("CameraTest3", "Error in switchToPhotoMode", e)
        }
    }

    private fun switchToVideoMode(){
        try {
            if (cameraMode == CameraMode.VIDEO) return
            
            cameraMode = CameraMode.VIDEO
            captureButton.visibility = View.GONE
            recordButton.visibility = View.VISIBLE

            closeCamera()
            // Add delay before reopening to ensure proper cleanup
            android.os.Handler(Looper.getMainLooper()).postDelayed({
                try {
                    //if (shouldOpenCamera && surfaceTopReady && surfaceBottomReady) {
                        openCamera()
                    //}
                } catch (e: Exception) {
                    Log.e("CameraTest3", "Error reopening camera after video mode switch", e)
                }
            }, 500)
        } catch (e: Exception) {
            Log.e("CameraTest3", "Error in switchToVideoMode", e)
        }
    }

    private val zoom1xListener = View.OnClickListener {
        try {
            if(zoomType.value != ZoomType.ZOOM1X){
                zoomType.value = ZoomType.ZOOM1X
                zoom1x.isSelected = true
                zoom2x.isSelected = false
                closeCamera()
                // Add delay before reopening to ensure proper cleanup
                android.os.Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        //if (shouldOpenCamera && surfaceTopReady && surfaceBottomReady) {
                            openCamera()
                        //}
                    } catch (e: Exception) {
                        Log.e("CameraTest3", "Error reopening camera after zoom1x switch", e)
                    }
                }, 500)
            }
        } catch (e: Exception) {
            Log.e("CameraTest3", "Error in zoom1xListener", e)
        }
    }
    private val zoom2xListener = View.OnClickListener {
        if(zoomType.value != ZoomType.ZOOM2X){
            zoomType.value = ZoomType.ZOOM2X
            zoom1x.isSelected = false
            zoom2x.isSelected = true
            closeCamera()
            // Add delay before reopening to ensure proper cleanup
            android.os.Handler(Looper.getMainLooper()).postDelayed({
                //if (shouldOpenCamera && surfaceTopReady && surfaceBottomReady) {
                    openCamera()
                //}
            }, 500)
        }
    }

    private val captureListener = View.OnClickListener {
        takePicture()
    }

    private val recordListener = View.OnClickListener {
        if(isRecording){
            stopRecording()
        }else{
            startRecording()
        }
    }

    private val surfaceTextureListenerTop = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            surfaceTopReady = true
            tryOpenCamera()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }

    private val surfaceTextureListenerBottom = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            surfaceBottomReady = true
            tryOpenCamera()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }

    private fun tryOpenCamera() {
        try {
            if (surfaceTopReady && surfaceBottomReady && shouldOpenCamera && !isCameraOpening && !isCameraClosing) {
                openCamera()
            }
        } catch (e: Exception) {
            Log.e("CameraTest3", "Error in tryOpenCamera", e)
        }
    }

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1001)
            return
        }
        
        if (isCameraOpening || isCameraClosing) {
            Log.d("CameraTest3", "Camera operation already in progress, skipping openCamera")
            return
        }
        
        isCameraOpening = true
        try {
            cameraManager.openCamera(logicalCameraId, openCameraCallback, null)
        } catch (e: Exception) {
            Log.e("CameraTest3", "Error opening camera", e)
            isCameraOpening = false
            shouldOpenCamera = false
        }
    }

    private val openCameraCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.d("CameraTest3", "Camera opened successfully")
            cameraDevice = camera
            isCameraOpening = false
            startCameraPreview()
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.d("CameraTest3", "Camera disconnected")
            isCameraOpening = false
            isCameraClosing = true
            try {
                camera.close()
            } catch (e: Exception) {
                Log.e("CameraTest3", "Error closing disconnected camera", e)
            }
            cameraDevice = null
            cameraCaptureSession?.close()
            cameraCaptureSession = null
            
            // Reset state and try to reopen if needed
            isCameraClosing = false
            if (shouldOpenCamera && surfaceTopReady && surfaceBottomReady) {
                // Add a small delay before retrying to avoid rapid open/close cycles
                android.os.Handler(Looper.getMainLooper()).postDelayed({
                    //if (shouldOpenCamera && surfaceTopReady && surfaceBottomReady) {
                        openCamera()
                    //}
                }, 500)
            }
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.e("CameraTest3", "Camera error: $error")
            isCameraOpening = false
            isCameraClosing = true
            try {
                camera.close()
            } catch (e: Exception) {
                Log.e("CameraTest3", "Error closing camera after error", e)
            }
            cameraDevice = null
            cameraCaptureSession?.close()
            cameraCaptureSession = null
            
            // Reset state
            isCameraClosing = false
            shouldOpenCamera = false
            
            // Show error to user
            runOnUiThread {
                Toast.makeText(this@CameraTest3, "Camera error occurred: $error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startCameraPreview() {
        try {
            // Check if camera device is still valid
            if (cameraDevice == null) {
                Log.w("CameraTest3", "Camera device is null, cannot start preview")
                return
            }
            
            cameraCaptureSession?.close()
            cameraCaptureSession = null

            if(cameraMode == CameraMode.PHOTO){
                imageReaderTop?.close()
                imageReaderTop = null

                imageReaderBottom?.close()
                imageReaderBottom = null
            }
        } catch (e: Exception) {
            Log.e("CameraTest3", "Error cleaning up previous session", e)
        }

        try {
            val captureRequest = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            val configs = mutableListOf<OutputConfiguration>()

            if(topLensId.isNotEmpty()){
                topErrorText.visibility = View.GONE
                topErrorText.text = ""
                textureViewTop.visibility = View.VISIBLE

                val textureTop = textureViewTop.surfaceTexture ?: return
                textureTop.setDefaultBufferSize(textureViewTop.width, textureViewTop.height)

                val surfaceTop = Surface(textureTop)
                configs.add(OutputConfiguration(surfaceTop).apply {
                    setPhysicalCameraId(topLensId)
                })

                if(cameraMode == CameraMode.PHOTO){
                    imageReaderTop = ImageReader.newInstance(
                        textureViewTop.width, textureViewTop.height,
                        ImageFormat.JPEG, 1
                    )

                    imageReaderTop!!.setOnImageAvailableListener(imageReaderTopListener,null)

                    configs.add(OutputConfiguration(imageReaderTop!!.surface).apply {
                        setPhysicalCameraId(topLensId)
                    })
                }

                captureRequest.addTarget(surfaceTop)
            }else{
                textureViewTop.visibility = View.GONE
                topErrorText.text = topLensUnsupportedErrorText
                topErrorText.visibility = View.VISIBLE
            }

            if(bottomLensId.isNotEmpty()){
                bottomErrorText.visibility = View.GONE
                bottomErrorText.text = ""
                textureViewBottom.visibility = View.VISIBLE

                val textureBottom = textureViewBottom.surfaceTexture ?: return
                textureBottom.setDefaultBufferSize(textureViewBottom.width, textureViewBottom.height)

                val surfaceBottom = Surface(textureBottom)
                configs.add(OutputConfiguration(surfaceBottom).apply {
                    setPhysicalCameraId(bottomLensId)
                })

                if(cameraMode == CameraMode.PHOTO){
                    imageReaderBottom = ImageReader.newInstance(
                        textureViewBottom.width, textureViewBottom.height,
                        ImageFormat.JPEG, 1
                    )

                    imageReaderBottom!!.setOnImageAvailableListener(imageReaderBottomListener,null)

                    configs.add(OutputConfiguration(imageReaderBottom!!.surface).apply {
                        setPhysicalCameraId(bottomLensId)
                    })
                }
                captureRequest.addTarget(surfaceBottom)
            }else{
                textureViewBottom.visibility = View.GONE
                bottomErrorText.text = bottomLensUnsupportedErrorText
                bottomErrorText.visibility = View.VISIBLE
            }

            // Check if camera device is still valid before creating session
            if (cameraDevice == null) {
                Log.w("CameraTest3", "Camera device became null before creating session")
                return
            }

            cameraDevice?.createCaptureSession(SessionConfiguration(SessionConfiguration.SESSION_REGULAR,
                configs,
                this.mainExecutor, object : CameraCaptureSession.StateCallback(){
                override fun onConfigureFailed(p0: CameraCaptureSession) {
                    Log.e("CameraTest3", "Failed to configure camera session")
                    closeCamera()
                    // Add delay before retrying to avoid rapid open/close cycles
                    android.os.Handler(Looper.getMainLooper()).postDelayed({
                        //if (shouldOpenCamera && surfaceTopReady && surfaceBottomReady) {
                            openCamera()
                        //}
                    }, 1000)
                }

                override fun onConfigured(session: CameraCaptureSession) {
                    Log.d("CameraTest3", "Camera session configured successfully")
                    cameraCaptureSession = session
                    try {
                        session.setRepeatingRequest(captureRequest.build(), null, null)
                    } catch (e: Exception) {
                        Log.e("CameraTest3", "Error setting repeating request", e)
                        closeCamera()
                    }
                }
            }))
        } catch (e: Exception) {
            Log.e("CameraTest3", "Error in startCameraPreview", e)
            if (e is android.hardware.camera2.CameraAccessException && e.reason == android.hardware.camera2.CameraAccessException.CAMERA_DISCONNECTED) {
                Log.w("CameraTest3", "Camera disconnected during preview setup")
                closeCamera()
                // Add delay before retrying to avoid rapid open/close cycles
                android.os.Handler(Looper.getMainLooper()).postDelayed({
                    //if (shouldOpenCamera && surfaceTopReady && surfaceBottomReady) {
                        openCamera()
                    //}
                }, 1000)
            } else {
                Toast.makeText(this@CameraTest3, "Error starting camera preview: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val imageReaderTopListener : ImageReader.OnImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        val image = reader.acquireLatestImage()
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        image.close()

        saveImage(bytes, filename = "Top_${topLensName}_${System.currentTimeMillis()}")
    }

    private val imageReaderBottomListener : ImageReader.OnImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        val image = reader.acquireLatestImage()
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        image.close()

        saveImage(bytes, filename = "Bottom_${bottomLensName}_${System.currentTimeMillis()}")
    }

    private fun takePicture() {
        try {
            // Check if camera device is still valid
            if (cameraDevice == null) {
                Log.w("CameraTest3", "Camera device is null, cannot take picture")
                Toast.makeText(this@CameraTest3, "Camera not available", Toast.LENGTH_SHORT).show()
                return
            }

            val captureRequest = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)

            if(imageReaderTop != null) {
                captureRequest.addTarget(imageReaderTop!!.surface)
            }

            if(imageReaderBottom != null) {
                captureRequest.addTarget(imageReaderBottom!!.surface)
            }

            captureRequest.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            captureRequest.set(CaptureRequest.JPEG_ORIENTATION, getJpegOrientation())

            cameraCaptureSession?.apply {
                try {
                    capture(captureRequest.build(), null, null)
                } catch (e: Exception) {
                    Log.e("CameraTest3", "Error capturing image", e)
                    if (e is android.hardware.camera2.CameraAccessException && e.reason == android.hardware.camera2.CameraAccessException.CAMERA_DISCONNECTED) {
                        Log.w("CameraTest3", "Camera disconnected during capture")
                        closeCamera()
                    } else {
                        Toast.makeText(this@CameraTest3, "Error taking picture: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } ?: run {
                Log.w("CameraTest3", "Camera capture session is null")
                Toast.makeText(this@CameraTest3, "Camera session not available", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("CameraTest3", "Error in takePicture", e)
            if (e is android.hardware.camera2.CameraAccessException && e.reason == android.hardware.camera2.CameraAccessException.CAMERA_DISCONNECTED) {
                Log.w("CameraTest3", "Camera disconnected during picture capture setup")
                closeCamera()
            } else {
                Toast.makeText(this@CameraTest3, "Error taking picture: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveImage(bytes: ByteArray, filename: String) {
        try {
            val picturesDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            if (!picturesDir!!.exists()) picturesDir.mkdirs()

            val file = File(picturesDir, "${filename}.jpg")
            FileOutputStream(file).use { it.write(bytes) }
            Log.d("Save Path", "Saved: ${file.absolutePath}")

            runOnUiThread {
                Toast.makeText(this, "Saved: ${file.absolutePath}", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("CameraTest3", "Error saving image: $filename", e)
            runOnUiThread {
                Toast.makeText(this, "Error saving image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startRecording() {
        try {
            // Check if camera device is still valid
            if (cameraDevice == null) {
                Log.w("CameraTest3", "Camera device is null, cannot start recording")
                Toast.makeText(this@CameraTest3, "Camera not available", Toast.LENGTH_SHORT).show()
                return
            }

            isRecording = true
            recordButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_action_stop_recording))
            modeTabLayout.visibility = View.GONE
            zoom1x.visibility = View.GONE
            zoom2x.visibility = View.GONE

            val configs = mutableListOf<OutputConfiguration>()

            val captureRequest = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)

            val characteristics = cameraManager.getCameraCharacteristics(logicalCameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

            val videoSizes = map?.getOutputSizes(MediaRecorder::class.java) ?: return

            if(topLensId.isNotEmpty()){
                val videoSize = videoSizes.first { it.width <= textureViewTop.width && it.height <= textureViewTop.height }

                mediaRecorderTop = setupMediaRecorder("top", h = videoSize.height,w = videoSize.width)

                val recordSurfaceTop = mediaRecorderTop!!.surface
                val textureTop = textureViewTop.surfaceTexture!!
                textureTop.setDefaultBufferSize(textureViewTop.width, textureViewTop.height)
                val surfaceTopPreview = Surface(textureTop)

               captureRequest.addTarget(surfaceTopPreview)
               captureRequest.addTarget(recordSurfaceTop)

                configs.add(OutputConfiguration(surfaceTopPreview).apply {
                    setPhysicalCameraId(topLensId)
                })
                configs.add(OutputConfiguration(recordSurfaceTop).apply {
                    setPhysicalCameraId(topLensId)
                })
            }

            if(bottomLensId.isNotEmpty()){
                val videoSize = videoSizes.first { it.width <= textureViewTop.width && it.height <= textureViewTop.height }

                mediaRecorderBottom = setupMediaRecorder("bottom", h = videoSize.height,w = videoSize.width)

                val recordSurfaceBottom = mediaRecorderBottom!!.surface
                val textureBottom = textureViewBottom.surfaceTexture!!

                textureBottom.setDefaultBufferSize(textureViewBottom.width, textureViewBottom.height)

                val surfaceBottomPreview = Surface(textureBottom)

                captureRequest.addTarget(surfaceBottomPreview)
                captureRequest.addTarget(recordSurfaceBottom)

                configs.add(OutputConfiguration(surfaceBottomPreview).apply {
                    setPhysicalCameraId(bottomLensId)
                })
                configs.add(OutputConfiguration(recordSurfaceBottom).apply {
                    setPhysicalCameraId(bottomLensId)
                })
            }

            // Check if camera device is still valid before creating session
            if (cameraDevice == null) {
                Log.w("CameraTest3", "Camera device became null before creating recording session")
                stopRecording()
                return
            }

            cameraDevice?.createCaptureSession(SessionConfiguration(SessionConfiguration.SESSION_REGULAR,
                configs,
                this.mainExecutor, object : CameraCaptureSession.StateCallback(){
                    override fun onConfigureFailed(p0: CameraCaptureSession) {
                         Log.e("CameraTest3", "Failed to configure camera session for recording")
                         Toast.makeText(this@CameraTest3, "Failed to configure camera.", Toast.LENGTH_SHORT).show()
                        isRecording = false
                        modeTabLayout.visibility = View.VISIBLE
                        zoom1x.visibility = View.VISIBLE
                        zoom2x.visibility = View.VISIBLE
                        closeCamera()
                        // Add delay before reopening to avoid rapid open/close cycles
                        android.os.Handler(Looper.getMainLooper()).postDelayed({
                            //if (shouldOpenCamera && surfaceTopReady && surfaceBottomReady) {
                                openCamera()
                            //}
                        }, 1000)
                    }

                    override fun onConfigured(session: CameraCaptureSession) {
                        Log.d("CameraTest3", "Camera session configured successfully for recording")
                        cameraCaptureSession = session
                        try {
                            session.setRepeatingRequest(captureRequest.build(), null, null)
                            // Start recording after session is configured
                            mediaRecorderTop?.start()
                            mediaRecorderBottom?.start()
                            startTimer()
                        } catch (e: Exception) {
                            Log.e("CameraTest3", "Error setting repeating request for recording", e)
                            if (e is android.hardware.camera2.CameraAccessException && e.reason == android.hardware.camera2.CameraAccessException.CAMERA_DISCONNECTED) {
                                Log.w("CameraTest3", "Camera disconnected during recording setup")
                                closeCamera()
                            } else {
                                Toast.makeText(this@CameraTest3, "Error starting recording: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                            stopRecording()
                        }
                    }
                }))
        } catch (e: Exception) {
            Log.e("CameraTest3", "Error in startRecording", e)
            if (e is android.hardware.camera2.CameraAccessException && e.reason == android.hardware.camera2.CameraAccessException.CAMERA_DISCONNECTED) {
                Log.w("CameraTest3", "Camera disconnected during recording setup")
                closeCamera()
            } else {
                Toast.makeText(this@CameraTest3, "Error starting recording: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            stopRecording()
        }
    }

    private fun stopRecording() {
        isRecording = false
        recordButton.setImageDrawable(null)

        modeTabLayout.visibility = View.VISIBLE
        zoom1x.visibility = View.VISIBLE
        zoom2x.visibility = View.VISIBLE
        stopTimer()

        try {
            mediaRecorderTop?.apply {
                try {
                    stop()
                    release()
                    Toast.makeText(
                        this@CameraTest3,
                        "Top video saved!",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Log.e("CameraTest3", "Error stopping top media recorder", e)
                }
            }
            mediaRecorderBottom?.apply {
                try {
                    stop()
                    release()
                    Toast.makeText(
                        this@CameraTest3,
                        "Bottom video saved!",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Log.e("CameraTest3", "Error stopping bottom media recorder", e)
                }
            }
        } catch (e: Exception) {
            Log.e("CameraTest3", "Error in stopRecording", e)
        }

        mediaRecorderTop = null
        mediaRecorderBottom = null

        // Add delay before starting preview to ensure proper cleanup
        android.os.Handler(Looper.getMainLooper()).postDelayed({
            //if (shouldOpenCamera && surfaceTopReady && surfaceBottomReady) {
                startCameraPreview()
            //}
        }, 500)
    }

    private fun startTimer() {
        try {
            recordTime.visibility = View.VISIBLE
            startTime = System.currentTimeMillis()

            timerHandler = android.os.Handler(Looper.getMainLooper())
            timerRunnable = object : Runnable {
                override fun run() {
                    try {
                        val elapsed = (System.currentTimeMillis() - startTime) / 1000
                        val minutes = elapsed / 60
                        val seconds = elapsed % 60
                        recordTime.text = String.format("%02d:%02d", minutes, seconds)
                        timerHandler?.postDelayed(this, 1000)
                    } catch (e: Exception) {
                        Log.e("CameraTest3", "Error in timer runnable", e)
                    }
                }
            }
            timerHandler?.post(timerRunnable!!)
        } catch (e: Exception) {
            Log.e("CameraTest3", "Error starting timer", e)
        }
    }

    private fun stopTimer() {
        try {
            timerHandler?.removeCallbacks(timerRunnable!!)
            timerHandler = null
            timerRunnable = null
            recordTime.visibility = View.GONE
        } catch (e: Exception) {
            Log.e("CameraTest3", "Error stopping timer", e)
        }
    }

    private fun setupMediaRecorder(tag: String, w: Int, h: Int): MediaRecorder {
        try {
            val file = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                "VID_${tag}_${System.currentTimeMillis()}.mp4")

            return MediaRecorder().apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(file.absolutePath)
                setVideoEncodingBitRate(10_000_000)
                setVideoFrameRate(30)
                setVideoSize(w, h)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setOrientationHint(getJpegOrientation())
                prepare()
            }
        } catch (e: Exception) {
            Log.e("CameraTest3", "Error setting up media recorder for tag: $tag", e)
            throw e
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        deviceId: Int
    ) {
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d("CameraTest3", "Camera permission granted")
            shouldOpenCamera = true
            tryOpenCamera()
        } else {
            Log.w("CameraTest3", "Camera permission denied")
            Toast.makeText(this@CameraTest3, "Camera permission is required to use this app", Toast.LENGTH_LONG).show()
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults, deviceId)
    }

    private fun getLensType(manager: CameraManager, physicalCameraId: String): LensType {
        try {
            val characteristics = manager.getCameraCharacteristics(physicalCameraId)
            val focalLengths =
                characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            val sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            if (focalLengths == null || focalLengths.isEmpty() || sensorSize == null) {
                return LensType.UNKNOWN
            }
            val sensorDiagonal =
                sqrt(sensorSize.width * sensorSize.width + sensorSize.height * sensorSize.height)
            val fullFrameDiagonal = sqrt(36.0 * 36.0 + 24.0 * 24.0)
            val focalLength = focalLengths[0]
            val eqFocalLength = focalLength * (fullFrameDiagonal / sensorDiagonal)
            return when {
                eqFocalLength < 20 -> LensType.ULTRA_WIDE
                eqFocalLength in 20.0..35.0 -> LensType.WIDE
                eqFocalLength > 35 -> LensType.TELEPHOTO
                else -> LensType.UNKNOWN
            }
        } catch (e: Exception) {
            Log.e("CameraTest3", "Error getting lens type for camera $physicalCameraId", e)
            return LensType.UNKNOWN
        }
    }

    private fun getJpegOrientation(): Int {
        try {
            val characteristics = cameraManager.getCameraCharacteristics(logicalCameraId)
            val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

            val deviceRotation = windowManager.defaultDisplay.rotation
            val deviceDegree = when (deviceRotation) {
                Surface.ROTATION_0 -> 0
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }

            // Back-facing vs Front-facing
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            return if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                (sensorOrientation + deviceDegree) % 360
            } else {
                (sensorOrientation - deviceDegree + 360) % 360
            }
        } catch (e: Exception) {
            Log.e("CameraTest3", "Error getting JPEG orientation", e)
            return 0
        }
    }

    private fun closeCamera() {
        if (isCameraClosing) {
            Log.d("CameraTest3", "Camera already closing, skipping closeCamera")
            return
        }

        isCameraClosing = true
        shouldOpenCamera = false

        try {
            cameraCaptureSession?.close()
            cameraCaptureSession = null
        } catch (e: Exception) {
            Log.e("CameraTest3", "Error closing camera session", e)
        }

        try {
            cameraDevice?.close()
            cameraDevice = null
        } catch (e: Exception) {
            Log.e("CameraTest3", "Error closing camera device", e)
        }

        try {
            if(cameraMode == CameraMode.PHOTO){
                imageReaderTop?.close()
                imageReaderTop = null

                imageReaderBottom?.close()
                imageReaderBottom = null
            }
        } catch (e: Exception) {
            Log.e("CameraTest3", "Error closing image readers", e)
        }

        isCameraClosing = false
        Log.d("CameraTest3", "Camera closed successfully")
    }
}
