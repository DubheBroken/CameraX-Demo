package com.dubhe.camera

import android.content.Context
import android.content.pm.PackageManager


class CameraUtils {

    private fun checkCameraHardware(context: Context): Boolean {
        // 支持所有版本
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)
        //  Android 2.3 (API Level 9) 及以上的
        // return  Camera.getNumberOfCameras() > 0;
    }

}