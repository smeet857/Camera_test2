package com.example.cameratest2

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.Manifest
import android.content.Intent
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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

    private var wideId: String = ""
    private var ultraWideId: String = ""
    private var telephotoId: String = ""

    private var currentCamera: CameraDevice? = null
    private var currentSession: CameraCaptureSession? = null
    private var imageReaderTop: ImageReader? = null
    private var imageReaderBottom: ImageReader? = null

    lateinit var cameraManager: CameraManager
    lateinit var physicalIds: Set<String>

    var surface1Size = mutableStateOf<Pair<Int, Int>?>(null)
    var surface2Size = mutableStateOf<Pair<Int, Int>?>(null)

    val isZoomed = mutableStateOf(false)

    val topLensId: String
        get() = if (isZoomed.value) telephotoId else wideId

    val bottomLensId: String
        get() = if (isZoomed.value) wideId else ultraWideId

    var logicalCameraId = ""



    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CameraPermissionScreen() }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @Composable
    fun DualCameraPreview() {

        val context = LocalContext.current
        val surface1 = remember { mutableStateOf<Surface?>(null) }
        val surface2 = remember { mutableStateOf<Surface?>(null) }
        val isSwitching = remember { mutableStateOf(false) }

        cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

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
        }else{
            physicalIds = arrayOf("").toSet()
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
                                    Modifier.aspectRatio(3f/4f)
                                )
                            }

                            isZoomed.value && telephotoId.isNotEmpty() -> {
                                CameraPreview(
                                    onSurfaceReady = { surface, w, h -> surface1.value = surface },
                                    onViewSize = { w, h -> surface1Size.value = w to h },
                                    Modifier.aspectRatio(3f/4f)
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
                                    Modifier.aspectRatio(4f/3f)
                                )
                            }

                            isZoomed.value && wideId.isNotEmpty() -> {
                                CameraPreview(
                                    onSurfaceReady = { surface, w, h -> surface2.value = surface },
                                    onViewSize = { w, h -> surface2Size.value = w to h },
                                    Modifier.aspectRatio(4f/3f)
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
                                    Log.d("Save file", file.absolutePath)
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

    @RequiresApi(Build.VERSION_CODES.R)
    @Composable
    fun CameraPermissionScreen() {
        val context = LocalContext.current
        val activity = context as Activity
        val lifecycleOwner = LocalLifecycleOwner.current

        var permissionState by remember { mutableStateOf(checkCameraPermission(context)) }
        var shouldShowRationale by remember { mutableStateOf(false) }

        // Re-check when coming back from settings or activity resumes
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    permissionState = checkCameraPermission(context)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            permissionState = granted
            if (!granted) {
                shouldShowRationale =
                    ActivityCompat.shouldShowRequestPermissionRationale(
                        activity, Manifest.permission.CAMERA
                    )
            }
        }

        when {
            permissionState -> {
                // ✅ Permission Granted
                DualCameraPreview()
            }

            shouldShowRationale -> {
                // ❌ Denied once -> show rationale with retry
                PermissionUI(
                    title = "Camera Permission Needed",
                    message = "We need camera access to let you take photos.",
                    buttonText = "Grant Permission"
                ) { launcher.launch(Manifest.permission.CAMERA) }
            }

            else -> {
                // 🚫 Permanently Denied -> settings button
                PermissionUI(
                    title = "Permission Required",
                    message = "Camera permission is permanently denied. Please enable it from settings.",
                    buttonText = "Open Settings"
                ) { openAppSettings(context) }
            }
        }
    }

    @Composable
    fun PermissionUI(title: String, message: String, buttonText: String, onClick: () -> Unit) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .background(Color.White)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onClick) {
                    Text(buttonText)
                }
            }
        }
    }

    private fun checkCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun openAppSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }


    @SuppressLint("MissingPermission")
    fun openSimpleCamera(context: Context,surface: Surface,width: Int,height: Int){
        cameraManager.openCamera(logicalCameraId, ContextCompat.getMainExecutor(context), object : CameraDevice.StateCallback(){
            override fun onDisconnected(p0: CameraDevice) {
                currentCamera?.close()
                currentCamera = null
            }

            override fun onError(p0: CameraDevice, p1: Int) {
                Log.e("Open Camera","Error on opening camera")
            }

            override fun onOpened(p0: CameraDevice) {
                currentCamera = p0

                val config = OutputConfiguration(surface)/*.apply {
                    setPhysicalCameraId(wideId)
                }*/

                val outputConfigs = mutableListOf<OutputConfiguration>()
                outputConfigs.add(config)

                currentCamera?.createCaptureSession(SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputConfigs, context.mainExecutor, object : CameraCaptureSession.StateCallback(){
                    override fun onConfigureFailed(p0: CameraCaptureSession) {
                        TODO("Not yet implemented")
                    }

                    override fun onConfigured(session: CameraCaptureSession) {
                        dispatchCameraRequests(surface,session)
                    }
                }))
            }

        })
    }

    fun dispatchCameraRequests(outputTarget: Surface,session: CameraCaptureSession){
        val captureRequest = session.device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        captureRequest.addTarget(outputTarget)
        session.setRepeatingRequest(captureRequest.build(), null,null)
    }

    private fun chooseOptimalSize(
        sizes: Array<out Size>,
        viewWidth: Int,
        viewHeight: Int
    ): Size {
        val targetRatio = viewWidth.toFloat() / viewHeight.toFloat()
        var bestSize: Size = sizes[0]
        var minDiff = Float.MAX_VALUE

        for (size in sizes) {
            val ratio = size.width.toFloat() / size.height.toFloat()
            val diff = abs(ratio - targetRatio)
            if (diff < minDiff) {
                bestSize = size
                minDiff = diff
            }
        }
        return bestSize
    }


    fun getAvailableAspectRatios(context: Context, cameraId: String): Array<out Size> {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val characteristics = manager.getCameraCharacteristics(cameraId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        val sizes = map?.getOutputSizes(SurfaceTexture::class.java)
        return sizes ?: emptyArray()
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
        modifier: Modifier
    ) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                            val outputSizes = getAvailableAspectRatios(context,logicalCameraId)
                            val bestSize = chooseOptimalSize(outputSizes, w, h)
                            st.setDefaultBufferSize(bestSize.width, bestSize.height)

                            onSurfaceReady(Surface(st), bestSize.width, bestSize.height)
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
            modifier = modifier
        )
    }

    @SuppressLint("MissingPermission")
    fun openLogicalCameraWithPhysicals(
        context: Context,
        surface1: Surface?,        // top preview
        surface2: Surface?,        // bottom preview
        cameraManager: CameraManager,
        physicalId1: String,       // "5" (WIDE) or "6" (TELE)
        physicalId2: String,       // "2" (ULTRA)
        onConfigured: (() -> Unit)
    ) {
        val executor = ContextCompat.getMainExecutor(context)

        // cleanup previous
//        try {
//            currentSession?.close(); currentSession = null
//            currentCamera?.close(); currentCamera = null
//            imageReaderTop?.close(); imageReaderTop = null
//            imageReaderBottom?.close(); imageReaderBottom = null
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }

        cameraManager.openCamera(
            logicalCameraId,
            executor,
            object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    currentCamera = camera
                    try {
                        val captureRequest =
                            camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                        val outputConfigs = mutableListOf<OutputConfiguration>()

                        // --- TOP preview ---
                        if (surface1 != null && physicalId1.isNotEmpty()) {
                            surface1Size.value?.let { (w, h) ->
                                val sizes = getAvailableAspectRatios(context,physicalId1)
                                val bestSize = chooseOptimalSize(sizes,w,h)

                                imageReaderTop = ImageReader.newInstance(
                                    bestSize.width,
                                    bestSize.height,
                                    ImageFormat.JPEG,
                                    1
                                )

                                val previewConfig = OutputConfiguration(surface1).apply {
                                    setPhysicalCameraId(physicalId1)
                                }
                                val readerConfig = OutputConfiguration(imageReaderTop!!.surface).apply {
                                    setPhysicalCameraId(physicalId1)
                                }
                                outputConfigs.add(previewConfig)
                                outputConfigs.add(readerConfig)
                                captureRequest.addTarget(surface1)
                            }
                        }

                        // --- BOTTOM preview ---
                        if (surface2 != null && physicalId2.isNotEmpty()) {
                            surface2Size.value?.let { (w, h) ->

                                val sizes = getAvailableAspectRatios(context,physicalId2)
                                val bestSize = chooseOptimalSize(sizes,w,h)


                                imageReaderBottom = ImageReader.newInstance(
                                    bestSize.width,
                                    bestSize.height,
                                    ImageFormat.JPEG,
                                    1
                                )

                                val previewConfig = OutputConfiguration(surface2).apply {
                                    setPhysicalCameraId(physicalId2)
                                }
                                val readerConfig =
                                    OutputConfiguration(imageReaderBottom!!.surface).apply {
                                        setPhysicalCameraId(physicalId2)
                                    }
                                outputConfigs.add(previewConfig)
                                outputConfigs.add(readerConfig)
                                captureRequest.addTarget(surface2)
                            }
                        }

                        currentCamera?.createCaptureSession(SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputConfigs, context.mainExecutor, object : CameraCaptureSession.StateCallback(){
                            override fun onConfigureFailed(p0: CameraCaptureSession) {
                                TODO("Not yet implemented")
                            }

                            override fun onConfigured(session: CameraCaptureSession) {
                                session.setRepeatingRequest(captureRequest.build(), null,null)
                            }
                        }))

//                        camera.createCaptureSessionByOutputConfigurations(
//                            outputConfigs,
//                            object : CameraCaptureSession.StateCallback() {
//                                override fun onConfigured(session: CameraCaptureSession) {
//                                    currentSession = session
//                                    session.setRepeatingRequest(requestBuilder.build(), null, null)
//                                    onConfigured.invoke()
//                                }
//
//                                override fun onConfigureFailed(session: CameraCaptureSession) {
//                                    Log.e("Camera2", "Session configuration failed")
//                                    onConfigured.invoke()
//                                }
//                            },
//                            null
//                        )
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



