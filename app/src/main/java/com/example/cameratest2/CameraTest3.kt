package com.example.cameratest2

import android.Manifest
import android.content.ContentValues
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
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.tabs.TabLayout
import java.io.File
import java.io.FileInputStream
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

    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
//            Manifest.permission.READ_MEDIA_IMAGES,
//            Manifest.permission.READ_MEDIA_VIDEO
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager

        for(camera in cameraManager.cameraIdList){
            Log.d("Camera id",camera.toString())
            val characteristics = cameraManager.getCameraCharacteristics(camera)
            val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            val isLogicalCamera = capabilities?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)
            if(isLogicalCamera == true){
                logicalCameraId = camera
            }
        }

        if(logicalCameraId.isNotEmpty()){
            val characteristics =
                cameraManager.getCameraCharacteristics(logicalCameraId)

            physicalIds = characteristics.physicalCameraIds


            for (id in physicalIds) {
                when (getLensType(cameraManager, id)) {
                    LensType.WIDE -> wideId = id
                    LensType.ULTRA_WIDE -> ultraWideId = id
                    LensType.TELEPHOTO -> telephotoId = id
                    else -> {}
                }
                Log.d("Lens Type", getLensType(cameraManager, id).name + " physical = > $id")
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
        }else{
            setContentView(R.layout.not_supported_multi_camera)
        }
    }

    override fun onPause() {
        closeCamera()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (logicalCameraId.isNotEmpty() && cameraDevice == null) {
            openCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraDevice?.close()
        cameraCaptureSession?.close()
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
        cameraMode = CameraMode.PHOTO
        captureButton.visibility = View.VISIBLE
        recordButton.visibility = View.GONE

        closeCamera()

        android.os.Handler(Looper.getMainLooper()).postDelayed({
            openCamera()
        },500)
    }

    private fun switchToVideoMode(){
        cameraMode = CameraMode.VIDEO
        captureButton.visibility = View.GONE
        recordButton.visibility = View.VISIBLE

        closeCamera()
        android.os.Handler(Looper.getMainLooper()).postDelayed({
            openCamera()
        },500)
    }

    private val zoom1xListener = View.OnClickListener {
        if(zoomType.value != ZoomType.ZOOM1X){
            zoomType.value = ZoomType.ZOOM1X
            zoom1x.isSelected = true
            zoom2x.isSelected = false
            closeCamera()
            android.os.Handler(Looper.getMainLooper()).postDelayed({
                openCamera()
            },500)
        }
    }
    private val zoom2xListener = View.OnClickListener {
        if(zoomType.value != ZoomType.ZOOM2X){
            zoomType.value = ZoomType.ZOOM2X
            zoom1x.isSelected = false
            zoom2x.isSelected = true
            closeCamera()
            android.os.Handler(Looper.getMainLooper()).postDelayed({
                openCamera()
            },500)
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
        if (surfaceTopReady && surfaceBottomReady) {
            openCamera()
        }
    }

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, requiredPermissions, 1001)
            return
        }
        try {
            cameraManager.openCamera(logicalCameraId, openCameraCallback, null)
        } catch (e: Exception) {
            Log.e("CameraTest3", "Error opening camera", e)
        }
    }

    private val openCameraCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            startCameraPreview()
        }

        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
            cameraDevice = null
        }
    }

    private fun startCameraPreview() {
        try{
            // Check if camera device is still valid
            if (cameraDevice == null) {
                Log.w("CameraTest3", "Camera device is null, cannot start preview")
                return
            }

            cameraCaptureSession?.close()
            cameraCaptureSession = null

            imageReaderTop?.close()
            imageReaderTop = null

            imageReaderBottom?.close()
            imageReaderBottom = null
        }catch (e: Exception){
            e.printStackTrace()
        }

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

        cameraDevice?.createCaptureSession(SessionConfiguration(SessionConfiguration.SESSION_REGULAR,
            configs,
            this.mainExecutor, object : CameraCaptureSession.StateCallback(){
            override fun onConfigureFailed(p0: CameraCaptureSession) {
                closeCamera()
                android.os.Handler(Looper.getMainLooper()).postDelayed({
                    openCamera()
                },500)
            }

            override fun onConfigured(session: CameraCaptureSession) {
                cameraCaptureSession = session
                session.setRepeatingRequest(captureRequest.build(), null, null)
            }
        }))
    }

    private val imageReaderTopListener : ImageReader.OnImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        val image = reader.acquireLatestImage()
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        image.close()

        savePhotoToGallery(bytes,"Top_${topLensName}_${System.currentTimeMillis()}")
    }

    private val imageReaderBottomListener : ImageReader.OnImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        val image = reader.acquireLatestImage()
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        image.close()

//        saveImage(bytes, filename = "Bottom_${bottomLensName}_${System.currentTimeMillis()}")
        savePhotoToGallery(bytes,"Top_${topLensName}_${System.currentTimeMillis()}")
    }

    private fun takePicture() {

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
            capture(captureRequest.build(), null, null)
        }
    }

    private fun saveImage(bytes: ByteArray,filename:String) {

        val picturesDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        if (!picturesDir!!.exists()) picturesDir.mkdirs()

        val file = File(picturesDir, "${filename}.jpg")
        FileOutputStream(file).use { it.write(bytes) }
        Log.d("Save Path", "Saved: ${file.absolutePath}")

        runOnUiThread {
            Toast.makeText(this, "Saved: ${file.absolutePath}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startRecording() {
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

        cameraDevice?.createCaptureSession(SessionConfiguration(SessionConfiguration.SESSION_REGULAR,
            configs,
            this.mainExecutor, object : CameraCaptureSession.StateCallback(){
                override fun onConfigureFailed(p0: CameraCaptureSession) {
                     Toast.makeText(this@CameraTest3, "Failed to configure camera.", Toast.LENGTH_SHORT).show()
                    isRecording = false
                    modeTabLayout.visibility = View.VISIBLE
                    zoom1x.visibility = View.VISIBLE
                    zoom2x.visibility = View.VISIBLE
                    closeCamera()
                    android.os.Handler(Looper.getMainLooper()).postDelayed({
                        openCamera()
                    },500)
                }

                override fun onConfigured(session: CameraCaptureSession) {
                    cameraCaptureSession = session
                    session.setRepeatingRequest(captureRequest.build(), null, null)
                    mediaRecorderTop?.start()
                    mediaRecorderBottom?.start()

                    startTimer()
                }
            }))
    }

    private fun stopRecording() {
        isRecording = false
        recordButton.setImageDrawable(null)

        modeTabLayout.visibility = View.VISIBLE
        zoom1x.visibility = View.VISIBLE
        zoom2x.visibility = View.VISIBLE
        stopTimer()

        try {
            cameraCaptureSession?.stopRepeating()
            cameraCaptureSession?.abortCaptures()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            mediaRecorderTop?.apply {
                stop()
                release()
                Toast.makeText(
                    this@CameraTest3,
                    "Top video saved!",
                    Toast.LENGTH_SHORT
                ).show()
            }
            mediaRecorderBottom?.apply {
                stop()
                release()
                Toast.makeText(
                    this@CameraTest3,
                    "Bottom video saved!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        mediaRecorderTop = null
        mediaRecorderBottom = null

        startCameraPreview()
    }

    private fun startTimer() {
        recordTime.visibility = View.VISIBLE
        startTime = System.currentTimeMillis()

        timerHandler = android.os.Handler(Looper.getMainLooper())
        timerRunnable = object : Runnable {
            override fun run() {
                val elapsed = (System.currentTimeMillis() - startTime) / 1000
                val minutes = elapsed / 60
                val seconds = elapsed % 60
                recordTime.text = String.format("%02d:%02d", minutes, seconds)
                timerHandler?.postDelayed(this, 1000)
            }
        }
        timerHandler?.post(timerRunnable!!)
    }

    private fun stopTimer() {
        timerHandler?.removeCallbacks(timerRunnable!!)
        recordTime.visibility = View.GONE
    }

    private fun setupMediaRecorder(tag: String,w:Int,h:Int): MediaRecorder {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "VID_${tag}_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraTest/") // visible in Gallery
        }

        val resolver = contentResolver
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)!!

        val fileDescriptor = resolver.openFileDescriptor(uri, "w")!!.fileDescriptor

        return MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(fileDescriptor)
            setVideoEncodingBitRate(10_000_000)
            setVideoFrameRate(30)
            setVideoSize(w, h)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT)
            setOrientationHint(getJpegOrientation())
            prepare()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        deviceId: Int
    ) {
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            tryOpenCamera()
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults, deviceId)
    }

    private fun getLensType(manager: CameraManager, physicalCameraId: String): LensType {
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
    }

    private fun getJpegOrientation(): Int {
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
    }

    private fun closeCamera() {
        try {
            cameraCaptureSession?.close()
            cameraCaptureSession = null

            cameraDevice?.close()
            cameraDevice = null

            imageReaderTop?.close()
            imageReaderTop = null

            imageReaderBottom?.close()
            imageReaderBottom = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun savePhotoToGallery(bytes: ByteArray,fileName:String) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${fileName}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraTest/")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)

        uri?.let {
            resolver.openOutputStream(it).use { out ->
                out?.write(bytes)
            }

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)

            runOnUiThread {
                Toast.makeText(this, "Photo saved to gallery 📷", Toast.LENGTH_SHORT).show()
            }
        }
    }

}
