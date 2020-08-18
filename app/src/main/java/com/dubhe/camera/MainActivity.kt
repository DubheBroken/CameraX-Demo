package com.dubhe.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.VideoCapture.OnVideoSavedCallback
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

typealias LumaListener = (luma: Double) -> Unit

class MainActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService

    var cameraProvider: ProcessCameraProvider? = null//相机信息
    var preview: Preview? = null//预览对象
    var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA//当前相机
    var camera: Camera? = null//相机对象
    private var imageCapture: ImageCapture? = null//拍照用例
    var videoCapture: VideoCapture? = null//录像用例

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        camera_capture_button.setOnClickListener { takePhoto() }
        btnStartVideo.setOnClickListener {
            btnStartVideo.text = "Stop Video"
            takeVideo()
        }
        btnSwitch.setOnClickListener {
            if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            }
            startCamera()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun startCamera() {
        cameraExecutor = Executors.newSingleThreadExecutor()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(Runnable {
            cameraProvider = cameraProviderFuture.get()//获取相机信息

            //预览配置
            preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(viewFinder.createSurfaceProvider())
                    }

            imageCapture = ImageCapture.Builder().build()//拍照用例配置

            val imageAnalyzer = ImageAnalysis.Builder()
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { luma ->
                            Log.d(TAG, "Average luminosity: $luma")
                        })
                    }

            videoCapture = VideoCapture.Builder()//录像用例配置
//                .setTargetAspectRatio(AspectRatio.RATIO_16_9) //设置高宽比
//                .setTargetRotation(viewFinder.display.rotation)//设置旋转角度
//                .setAudioRecordSource(AudioSource.MIC)//设置音频源麦克风
                    .build()

            try {
                cameraProvider?.unbindAll()//先解绑所有用例
                camera = cameraProvider?.bindToLifecycle(this, cameraSelector, preview, imageCapture, videoCapture)//绑定用例
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).path +
                "/CameraX" + SimpleDateFormat(FILENAME_FORMAT, Locale.CHINA).format(System.currentTimeMillis()) + ".jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exc: ImageCaptureException) {
                        Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    }

                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        val savedUri = Uri.fromFile(file)
                        val msg = "Photo capture succeeded: $savedUri"
                        Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                        Log.d(TAG, msg)
                    }
                })
    }

    @SuppressLint("RestrictedApi", "ClickableViewAccessibility")
    private fun takeVideo() {

        //视频保存路径
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).path + "/CameraX" + SimpleDateFormat(
                FILENAME_FORMAT, Locale.CHINA
        ).format(System.currentTimeMillis()) + ".mp4")
        //开始录像
        videoCapture?.startRecording(file, Executors.newSingleThreadExecutor(), object : OnVideoSavedCallback {
            override fun onVideoSaved(@NonNull file: File) {
                //保存视频成功回调，会在停止录制时被调用
                Toast.makeText(this@MainActivity, file.absolutePath, Toast.LENGTH_SHORT).show()
            }

            override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) {
                //保存失败的回调，可能在开始或结束录制时被调用
                Log.e("", "onError: $message")
            }
        })

        btnStartVideo.setOnClickListener {
            videoCapture?.stopRecording()//停止录制
            preview?.clear()//清除预览
            btnStartVideo.text = "Start Video"
            btnStartVideo.setOnClickListener {
                btnStartVideo.text = "Stop Video"
                takeVideo()
            }
            Toast.makeText(this, file.path, Toast.LENGTH_SHORT).show()
            Log.d("path", file.path)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private class LuminosityAnalyzer(private val listener: LumaListener) : ImageAnalysis.Analyzer {

        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()
            val data = ByteArray(remaining())
            get(data)
            return data
        }

        override fun analyze(image: ImageProxy) {

            val buffer = image.planes[0].buffer
            val data = buffer.toByteArray()
            val pixels = data.map { it.toInt() and 0xFF }
            val luma = pixels.average()

            listener(luma)

            image.close()
        }
    }

    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO)
    }
}