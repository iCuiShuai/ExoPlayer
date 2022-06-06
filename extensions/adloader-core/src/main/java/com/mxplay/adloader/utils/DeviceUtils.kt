package com.mxplay.adloader.utils

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.view.Display
import android.view.Surface

object DeviceUtils {
    fun getScreenOrientation(context: Context, display: Display): Int {
        val orientation = context.resources.configuration.orientation
        val rotation = display.orientation
        return when (rotation) {
            Surface.ROTATION_0 -> if (orientation == Configuration.ORIENTATION_LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE // wide screen
            else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT // tall/square screen
            Surface.ROTATION_90 -> if (orientation != Configuration.ORIENTATION_LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT // wide screen
            else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE // tall/square screen
            Surface.ROTATION_180 -> if (orientation == Configuration.ORIENTATION_LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE // wide screen
            else ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT // tall/square screen
            Surface.ROTATION_270 -> if (orientation != Configuration.ORIENTATION_LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT // wide screen
            else ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE // tall / square screen
            else -> if (orientation == Configuration.ORIENTATION_LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    @JvmStatic
    fun getDeviceWidth(): Int {
        val displayMetrics = Resources.getSystem().displayMetrics
        return displayMetrics.widthPixels
    }

}