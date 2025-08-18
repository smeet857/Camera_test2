package com.example.cameratest2

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.sqrt

enum class LensType {
    ULTRA_WIDE,
    WIDE,
    TELEPHOTO,
    UNKNOWN
}

class MainActivity : ComponentActivity() {

    var wideId: String = ""
    var ultraWideId: String = ""
    var telephotoId: String = ""

    private var currentCamera: CameraDevice? = null
    private var currentSession: CameraCaptureSession? = null
    private var imageReaderTop: ImageReader? = null
    private var imageReaderBottom: ImageReader? = null

    lateinit var cameraManager: CameraManager

    var surface1Size = mutableStateOf<Pair<Int, Int>?>(null)
    var surface2Size = mutableStateOf<Pair<Int, Int>?>(null)

    val isZoomed = mutableStateOf(false)

    val topLensId: String
        get() = if (isZoomed.value) telephotoId else wideId

    val bottomLensId: String
        get() = if (isZoomed.value) wideId else ultraWideId

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DualCameraPreview() }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @Composable
    fun DualCameraPreview() {
        val context = LocalContext.current
        val surface1 = remember { mutableStateOf<Surface?>(null) }
        val surface2 = remember { mutableStateOf<Surface?>(null) }
        val isSwitching = remember { mutableStateOf(false) }

        cameraManager = context.getSystemService(CAMERA_SERVICE) as CameraManager
        val characteristics =
            cameraManager.getCameraCharacteristics(cameraManager.cameraIdList.first())
        val physicalIds = characteristics.physicalCameraIds

        for (id in physicalIds) {
            when (getLensType(cameraManager, id)) {
                LensType.WIDE -> wideId = id
                LensType.ULTRA_WIDE -> ultraWideId = id
                LensType.TELEPHOTO -> telephotoId = id
                else -> {}
            }
            Log.d("Lens Type", getLensType(cameraManager, id).name + " physical = > $id")
        }


        if (physicalIds.isNotEmpty()) {
            Box(
                Modifier
                    .background(color = Color.Black)
                    .safeDrawingPadding()
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .weight(1f)
                            .minimumInteractiveComponentSize(),
                        Alignment.Center
                    ) {
                        when {
                            !isZoomed.value && wideId.isNotEmpty() -> {
                                CameraPreview(
                                    onSurfaceReady = { surface, w, h -> surface1.value = surface },
                                    onViewSize = { w, h -> surface1Size.value = w to h },

                                )
                            }

                            isZoomed.value && telephotoId.isNotEmpty() -> {
                                CameraPreview(
                                    onSurfaceReady = { surface, w, h -> surface1.value = surface },
                                    onViewSize = { w, h -> surface1Size.value = w to h },
                                )
                            }

                            else -> if (!isSwitching.value) {
                                Text(
                                    if (isZoomed.value) "Telephoto not available" else "Wide lens not available",
                                    color = Color.White
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(20.dp))
                    Box(
                        Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize(),
                        Alignment.Center
                    ) {
                        when {
                            !isZoomed.value && ultraWideId.isNotEmpty() -> {
                                CameraPreview(
                                    onSurfaceReady = { surface, w, h -> surface2.value = surface },
                                    onViewSize = { w, h -> surface2Size.value = w to h },
                                )
                            }

                            isZoomed.value && wideId.isNotEmpty() -> {
                                CameraPreview(
                                    onSurfaceReady = { surface, w, h -> surface2.value = surface },
                                    onViewSize = { w, h -> surface2Size.value = w to h },
                                )
                            }

                            else -> if (!isSwitching.value) {
                                Text(
                                    if (isZoomed.value) "Wide lens not available" else "Ultra Wide lens not available",
                                    color = Color.White
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(20.dp))
                    ZoomControls(
                        onZoom1x = {
                            isSwitching.value = true
                            isZoomed.value = false
                        },
                        onZoom2x = {
                            isSwitching.value = true
                            isZoomed.value = true
                        },
                    )
                    LaunchedEffect(isZoomed.value, surface1.value, surface2.value, cameraManager) {

                        if (surface1.value != null || surface2.value != null) {
                            openLogicalCameraWithPhysicals(
                                context,
                                cameraManager.cameraIdList.first(),
                                surface1.value,
                                surface2.value,
                                cameraManager,
                                topLensId,
                                bottomLensId,
                                onConfigured = { isSwitching.value = false }
                            )
                        }
                    }
                    CaptureButton(
                        onClick = {
                            capturePhoto({ files ->
                                for (file in files) {
                                    Log.d("Save file", "${file.absolutePath}")
                                }
                            })
                        }
                    )
                }
                if (isSwitching.value) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(Modifier.height(8.dp))
                            Text("Switching lens…", color = Color.White)
                        }
                    }
                }
            }
        } else {
            Box(
                Modifier
                    .background(Color.Black)
                    .fillMaxSize()
            ) {
                Text(
                    "Multi camera is not supported",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }

    fun getBestPreviewSize(
        cameraManager: CameraManager,
        cameraId: String,
        targetAspect: Float
    ): Size {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: return Size(1920, 1080) // fallback

        val choices = map.getOutputSizes(SurfaceTexture::class.java)
        if (choices.isNullOrEmpty()) return Size(1920, 1080)

        // Pick the size with the closest aspect ratio to targetAspect
        return choices.minByOrNull { size ->
            val aspect = size.width.toFloat() / size.height.toFloat()
            abs(aspect - targetAspect)
        } ?: choices[0]
    }

    fun getLensType(manager: CameraManager, physicalCameraId: String): LensType {
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

    @Composable
    fun ZoomControls(onZoom1x: () -> Unit, onZoom2x: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = onZoom1x,
                shape = CircleShape,
                modifier = Modifier.size(40.dp),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.DarkGray,
                    contentColor = Color.White
                )
            ) {
                Text("1x")
            }
            Spacer(modifier = Modifier.width(10.dp))
            Button(
                onClick = onZoom2x,
                shape = CircleShape,
                modifier = Modifier.size(40.dp),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.DarkGray,
                    contentColor = Color.White
                )
            ) {
                Text("2x")
            }
        }
    }

    @Composable
    fun CaptureButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
        var pressed by remember { mutableStateOf(false) }
        Box(
            modifier = modifier
                .size(70.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            tryAwaitRelease()
                            pressed = false
                            onClick()
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                    .border(4.dp, Color.White, CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(if (pressed) 45.dp else 50.dp)
                    .background(Color.White, CircleShape)
            )
        }
    }

    @Composable
    fun CameraPreview(
        onSurfaceReady: (Surface, Int, Int) -> Unit,
        onViewSize: (Int, Int) -> Unit,
    ) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                            st.setDefaultBufferSize(w, h)
                            onSurfaceReady(Surface(st), w, h)
                        }

                        override fun onSurfaceTextureSizeChanged(
                            st: SurfaceTexture,
                            w: Int,
                            h: Int
                        ) {
                        }

                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture) = true
                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                    }
                }
            },
            modifier = Modifier.onGloballyPositioned { coords ->
                val w = coords.size.width
                val h = coords.size.height
                onViewSize(w, h)
            }
        )
    }

    @SuppressLint("MissingPermission")
    fun openLogicalCameraWithPhysicals(
        context: Context,
        logicalCameraId: String,   // e.g. "0"
        surface1: Surface?,        // top preview
        surface2: Surface?,        // bottom preview
        cameraManager: CameraManager,
        physicalId1: String,       // "5" (WIDE) or "6" (TELE)
        physicalId2: String,       // "2" (ULTRA)
        onConfigured: (() -> Unit)
    ) {
        val executor = ContextCompat.getMainExecutor(context)

        // cleanup previous
        try {
            currentSession?.close(); currentSession = null
            currentCamera?.close(); currentCamera = null
            imageReaderTop?.close(); imageReaderTop = null
            imageReaderBottom?.close(); imageReaderBottom = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        cameraManager.openCamera(
            logicalCameraId,
            executor,
            object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    currentCamera = camera
                    try {
                        val requestBuilder =
                            camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                        val outputConfigs = mutableListOf<OutputConfiguration>()

                        // --- TOP preview ---
                        if (surface1 != null && physicalId1.isNotEmpty()) {
                            surface1Size.value?.let { (w, h) ->
                                val characteristics = cameraManager.getCameraCharacteristics(physicalId1)
                                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                                val jpegSizes = map?.getOutputSizes(ImageFormat.JPEG)
                                val bestSize = chooseBestSize(jpegSizes!!, w, h)


                                imageReaderTop = ImageReader.newInstance(
                                    bestSize.width,
                                    bestSize.height,
                                    ImageFormat.JPEG,
                                    1
                                )
                            }

                            val previewConfig = OutputConfiguration(surface1).apply {
                                setPhysicalCameraId(physicalId1) // 👈 tie to WIDE/TELE
                            }
                            val readerConfig = OutputConfiguration(imageReaderTop!!.surface).apply {
                                setPhysicalCameraId(physicalId1)
                            }
                            outputConfigs.add(previewConfig)
                            outputConfigs.add(readerConfig)
                            requestBuilder.addTarget(surface1)
                        }

                        // --- BOTTOM preview ---
                        if (surface2 != null && physicalId2.isNotEmpty()) {
                            surface2Size.value?.let { (w, h) ->

                                val characteristics = cameraManager.getCameraCharacteristics(physicalId2)
                                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                                val jpegSizes = map?.getOutputSizes(ImageFormat.JPEG)
                                val bestSize = chooseBestSize(jpegSizes!!, w, h)


                                imageReaderBottom = ImageReader.newInstance(
                                    bestSize.width,
                                    bestSize.height,
                                    ImageFormat.JPEG,
                                    1
                                )
                            }

                            val previewConfig = OutputConfiguration(surface2).apply {
                                setPhysicalCameraId(physicalId2) // 👈 tie to ULTRA
                            }
                            val readerConfig =
                                OutputConfiguration(imageReaderBottom!!.surface).apply {
                                    setPhysicalCameraId(physicalId2)
                                }
                            outputConfigs.add(previewConfig)
                            outputConfigs.add(readerConfig)
                            requestBuilder.addTarget(surface2)
                        }

                        camera.createCaptureSessionByOutputConfigurations(
                            outputConfigs,
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: CameraCaptureSession) {
                                    currentSession = session
                                    session.setRepeatingRequest(requestBuilder.build(), null, null)
                                    onConfigured.invoke()
                                }

                                override fun onConfigureFailed(session: CameraCaptureSession) {
                                    Log.e("Camera2", "Session configuration failed")
                                    onConfigured.invoke()
                                }
                            },
                            null
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    if (camera == currentCamera) currentCamera = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    if (camera == currentCamera) currentCamera = null
                }
            },
        )
    }


    fun chooseBestSize(jpegSizes: Array<Size>, viewW: Int, viewH: Int): Size {
        val viewAspect = viewW.toFloat() / viewH
        return jpegSizes.minByOrNull { size ->
            val aspect = size.width.toFloat() / size.height
            kotlin.math.abs(aspect - viewAspect)
        } ?: jpegSizes[0]
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("MissingPermission")
    fun capturePhoto(onSaved: (List<File>) -> Unit) {
        val camera = currentCamera ?: return
        val session = currentSession ?: return
        val captures = mutableListOf<CaptureRequest>()
        val files = mutableListOf<File>()

        // --- TOP (WIDE/TELE) ---
        imageReaderTop?.let { readerTop ->
            val fileTop = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "TOP_${System.currentTimeMillis()}.jpg"
            )
            files.add(fileTop)

            val requestTop =
                camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                    addTarget(readerTop.surface)


                    val characteristics = cameraManager.getCameraCharacteristics(topLensId)
                    val sensorOrientation =
                        characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
                    val rotation = display.rotation

                    val deviceRotationDegrees = when (rotation) {
                        Surface.ROTATION_0 -> 0
                        Surface.ROTATION_90 -> 90
                        Surface.ROTATION_180 -> 180
                        Surface.ROTATION_270 -> 270
                        else -> 0
                    }

// Final orientation
                    val jpegOrientation = (sensorOrientation + deviceRotationDegrees + 360) % 360

                    set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation)
                }
            captures.add(requestTop.build())

            readerTop.setOnImageAvailableListener({ r ->
                r.acquireNextImage().use { image ->
                    saveImageToFile(image, fileTop)
                    Log.d("Camera", "Top photo saved: ${fileTop.absolutePath}")
                }
            }, null)
        }

        // --- BOTTOM (ULTRA) ---
        imageReaderBottom?.let { readerBottom ->
            val fileBottom = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "BOTTOM_${System.currentTimeMillis()}.jpg"
            )
            files.add(fileBottom)

            val requestBottom =
                camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                    addTarget(readerBottom.surface)

                    val characteristics = cameraManager.getCameraCharacteristics(bottomLensId)
                    val sensorOrientation =
                        characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
                    val rotation = display.rotation

                    val deviceRotationDegrees = when (rotation) {
                        Surface.ROTATION_0 -> 0
                        Surface.ROTATION_90 -> 90
                        Surface.ROTATION_180 -> 180
                        Surface.ROTATION_270 -> 270
                        else -> 0
                    }

// Final orientation
                    val jpegOrientation = (sensorOrientation + deviceRotationDegrees + 360) % 360

                    set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation)
                }
            captures.add(requestBottom.build())

            readerBottom.setOnImageAvailableListener({ r ->
                r.acquireNextImage().use { image ->
                    saveImageToFile(image, fileBottom)
                    Log.d("Camera", "Bottom photo saved: ${fileBottom.absolutePath}")
                }
            }, null)
        }

        // Fire capture
        if (captures.isNotEmpty()) {
            if (captures.size > 1) session.captureBurst(captures, null, null)
            else session.capture(captures[0], null, null)
            onSaved(files)
        }
    }

    private fun saveImageToFile(image: Image, file: File) {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        FileOutputStream(file).use { it.write(bytes) }
    }

}



