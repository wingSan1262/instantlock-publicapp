package com.risyan.quickshutdownphone.screen_content_guard.service_utils

import android.content.Context
import android.graphics.Bitmap
import com.risyan.quickshutdownphone.MyApp
import com.risyan.quickshutdownphone.base.data.SharedPrefApi
import com.risyan.quickshutdownphone.base.showIncognitoWarning
import com.risyan.quickshutdownphone.base.showToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull


interface NonScreenShotImageGrader {
    fun trackIfBlank(
        context: Context,
        bitmap: Bitmap,
        timeoutMs: Long = 10_000L,
        onBlankDetected: ((isBlank: Boolean) -> Unit) = {}
    )
}

class NonScreenShotImageGraderImp(
    val ownerScope: CoroutineScope,
    val sharedPreferences: SharedPrefApi = MyApp.getInstance().sharedPrefApi
) : NonScreenShotImageGrader {

    val BLANK_IMAGE_OCCURUANCE_THRESHOLD = 3


    fun getColorDistribution(bitmap: Bitmap, step: Int = 20): HashMap<Int, Int> {
        val colorMap = HashMap<Int, Int>()
        val width = bitmap.width
        val height = bitmap.height

        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val pixel = bitmap.getPixel(x, y)
                colorMap[pixel] = colorMap.getOrDefault(pixel, 0) + 1
            }
        }

        return colorMap
    }
    fun hasDominantColor(
        colorDistribution: HashMap<Int, Int>
    ): Boolean {
        if(colorDistribution.size > 1 ){
            return false
        }

        val totalRecordedPixels = colorDistribution.values.sum()

        colorDistribution.forEach { (t, u) ->
            if(u.toFloat() / totalRecordedPixels.toFloat() > 0.93){
                return@hasDominantColor true
            }
        }
        return false
    }




    suspend fun isBlankScreen(
        context: Context,
        bitmap: Bitmap,
        timeoutMs: Long = 10_000L
    ): Boolean = withContext(Dispatchers.Default) {

        val result = withTimeoutOrNull(timeoutMs) {
            hasDominantColor(getColorDistribution(bitmap))
        }

        if (result == null) {
            withContext(Dispatchers.Main) {
                context.showToast("Check Incognito Timeout")
            }
        }

        // default to true (treat timeout as blank/incognito)
        result ?: true
    }

    override fun trackIfBlank(
        context: Context,
        bitmap: Bitmap,
        timeoutMs: Long,
        onBlankDetected: ((isBlank: Boolean) -> Unit)
    ){
        ownerScope.launch(Dispatchers.Main){
            try {
                val isBlank = isBlankScreen(
                    context,
                    bitmap,
                    timeoutMs = 10_000L
                )

                onBlankDetected(isBlank)
                if (!isBlank) {
                    return@launch
                }

                val incognitoDialogMode = MyApp.getInstance().userSetting.incognitoDialogMode

                if (incognitoDialogMode) {
                    // Dialog mode: only increment after user confirms attention
                    context.showIncognitoWarning { isManualDis ->
                        if(!isManualDis){
                            return@showIncognitoWarning
                        }
                        ownerScope.launch(Dispatchers.Main) {
                            sharedPreferences.setCurrentBlankImageCounter(
                                sharedPreferences.getCurrentBlankImageCounter() + 1
                            )
                            sharedPreferences.setCurrentSafeCounter(
                                (sharedPreferences.getCurrentSafeCounter() - 1).coerceAtLeast(0)
                            )
                        }
                    }
                } else {
                    // Silent mode: directly increment counter without dialog
                    sharedPreferences.setCurrentBlankImageCounter(
                        sharedPreferences.getCurrentBlankImageCounter() + 1
                    )
                    sharedPreferences.setCurrentSafeCounter(
                        (sharedPreferences.getCurrentSafeCounter() - 1).coerceAtLeast(0)
                    )
                }
            } catch (e: Exception) {

            }
        }
    }
}
