package com.risyan.quickshutdownphone.screen_content_guard.service_utils

import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import com.risyan.quickshutdownphone.MyApp
import kotlin.math.pow
import com.risyan.quickshutdownphone.base.cropBottomSquare
import com.risyan.quickshutdownphone.base.cropCenterShavedSquare
import com.risyan.quickshutdownphone.base.cropCenterSquare
import com.risyan.quickshutdownphone.base.cropTopSquare
import com.risyan.quickshutdownphone.screen_content_guard.services.LockdownAcessibilityService
import com.risyan.quickshutdownphone.base.showToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.ResizeOp.ResizeMethod
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp
import org.tensorflow.lite.support.image.ops.Rot90Op
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel


interface AiNsfwGrader {
    fun runCheckNsfw(
        bitmap: Bitmap,
        onResult: (Boolean) -> Unit,
        onOccurrence: (safe: Int, nsfw: Int, bitmap: Bitmap) -> Unit
    )
}

class NSFWSexyImagePreProcessing() {
    private val INPUT_WIDTH = 224
    private val INPUT_HEIGHT = 224
    private val VGG_MEAN = floatArrayOf(103.939f, 116.779f, 123.68f)

    fun bitmapToByteBufferImageTensor(tflite: Interpreter, bitmap: Bitmap): ByteBuffer {
        val imageType = tflite.getInputTensor(0).dataType()
        val inputImageBuffer = TensorImage(imageType)
        inputImageBuffer.load(bitmap)

        val IMAGE_MEAN = 0f;
        val IMAGE_STD = 255f;

        // Creates processor for the TensorImage.
        val cropSize: Int = Math.min(bitmap.getWidth(), bitmap.getHeight())
        val numRoration: Int = 0 / 90
        val imageShape = tflite.getInputTensor(0).shape() // {1, height, width, 3}
        val imageSizeY = imageShape[1]
        val imageSizeX = imageShape[2]


        val imageProcessor: ImageProcessor =
            ImageProcessor.Builder()
                .add(ResizeWithCropOrPadOp(cropSize, cropSize))
                .add(ResizeOp(imageSizeX, imageSizeY, ResizeMethod.NEAREST_NEIGHBOR))
                .add(Rot90Op(numRoration))
                .add(NormalizeOp(IMAGE_MEAN, IMAGE_STD))
                .build()
        return imageProcessor.process(inputImageBuffer).buffer
    }

    fun bitmapToByteBufferRawProcessing(bitmap: Bitmap): ByteBuffer {
        ByteBuffer.allocateDirect(1 * INPUT_WIDTH * INPUT_HEIGHT * 3 * 4).also { imgData ->
            imgData.order(ByteOrder.LITTLE_ENDIAN)
            SystemClock.uptimeMillis().let { startTime ->
                imgData.rewind()
                IntArray(INPUT_WIDTH * INPUT_HEIGHT).let {
                    //把每个像素的颜色值转为int 存入intValues
                    bitmap.getPixels(
                        it,
                        0,
                        INPUT_WIDTH,
                        Math.max((bitmap.height - INPUT_HEIGHT) / 2, 0),
                        Math.max((bitmap.width - INPUT_WIDTH) / 2, 0),
                        INPUT_WIDTH,
                        INPUT_HEIGHT
                    )
                    for (color in it) {
                        imgData.putFloat(Color.blue(color) - VGG_MEAN[0])
                        imgData.putFloat(Color.green(color) - VGG_MEAN[1])
                        imgData.putFloat(Color.red(color) - VGG_MEAN[2])
                    }
                }
                return imgData
            }
        }
    }

}


/**
 * Image Enhancement Utilities for better NSFW detection in dark/low-contrast images
 */
class ImageEnhancer {

    /**
     * Calculate average brightness of an image (0-255)
     */
    fun calculateBrightness(bitmap: Bitmap): Float {
        var totalBrightness = 0L
        var pixelCount = 0

        // Sample every 10th pixel for performance
        for (y in 0 until bitmap.height step 10) {
            for (x in 0 until bitmap.width step 10) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                // Calculate perceived brightness
                totalBrightness += (0.299 * r + 0.587 * g + 0.114 * b).toLong()
                pixelCount++
            }
        }

        return (totalBrightness / pixelCount).toFloat()
    }

    /**
     * Enhance image brightness and contrast for dark images
     */
    fun enhanceImage(bitmap: Bitmap): Bitmap {
        val avgBrightness = calculateBrightness(bitmap)

        // If image is reasonably bright (>100), return as-is
        if (avgBrightness > 100f) {
            return bitmap
        }

        // Calculate enhancement factor based on darkness
        // Darker images get more enhancement
        val brightnessBoost = when {
            avgBrightness < 40f -> 1.8f  // Very dark - strong boost
            avgBrightness < 70f -> 1.5f  // Dark - moderate boost
            else -> 1.3f                  // Somewhat dark - light boost
        }

        val contrastBoost = when {
            avgBrightness < 40f -> 1.4f
            avgBrightness < 70f -> 1.3f
            else -> 1.2f
        }

        return applyBrightnessContrast(bitmap, brightnessBoost, contrastBoost)
    }

    /**
     * Apply brightness and contrast adjustments using ColorMatrix
     */
    private fun applyBrightnessContrast(bitmap: Bitmap, brightness: Float, contrast: Float): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()

        // Contrast adjustment
        val contrastMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, 0f,
            0f, contrast, 0f, 0f, 0f,
            0f, 0f, contrast, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))

        // Brightness adjustment (translate)
        val brightnessOffset = (brightness - 1f) * 255f * 0.5f
        val brightnessMatrix = ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, brightnessOffset,
            0f, 1f, 0f, 0f, brightnessOffset,
            0f, 0f, 1f, 0f, brightnessOffset,
            0f, 0f, 0f, 1f, 0f
        ))

        // Combine matrices
        val combined = ColorMatrix()
        combined.postConcat(contrastMatrix)
        combined.postConcat(brightnessMatrix)

        paint.colorFilter = ColorMatrixColorFilter(combined)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        return result
    }

    /**
     * Apply gamma correction for dark images
     * Gamma < 1.0 brightens, Gamma > 1.0 darkens
     */
    fun applyGammaCorrection(bitmap: Bitmap, gamma: Float = 0.6f): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)

        // Build gamma lookup table for performance
        val gammaTable = IntArray(256) { i ->
            (255.0 * (i / 255.0).pow(gamma.toDouble())).toInt().coerceIn(0, 255)
        }

        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val a = Color.alpha(pixel)
                val r = gammaTable[Color.red(pixel)]
                val g = gammaTable[Color.green(pixel)]
                val b = gammaTable[Color.blue(pixel)]
                result.setPixel(x, y, Color.argb(a, r, g, b))
            }
        }

        return result
    }

    /**
     * Adaptive histogram equalization for better contrast
     */
    fun applyHistogramEqualization(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)

        // Calculate histogram
        val histogram = IntArray(256)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                // Use luminance
                val luminance = (0.299 * Color.red(pixel) + 0.587 * Color.green(pixel) + 0.114 * Color.blue(pixel)).toInt()
                histogram[luminance]++
            }
        }

        // Calculate cumulative distribution
        val cdf = IntArray(256)
        cdf[0] = histogram[0]
        for (i in 1 until 256) {
            cdf[i] = cdf[i - 1] + histogram[i]
        }

        // Normalize CDF
        val totalPixels = bitmap.width * bitmap.height
        val cdfMin = cdf.firstOrNull { it > 0 } ?: 0
        val lookupTable = IntArray(256) { i ->
            ((cdf[i] - cdfMin).toFloat() / (totalPixels - cdfMin) * 255).toInt().coerceIn(0, 255)
        }

        // Apply equalization
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val r = lookupTable[Color.red(pixel)]
                val g = lookupTable[Color.green(pixel)]
                val b = lookupTable[Color.blue(pixel)]
                result.setPixel(x, y, Color.argb(Color.alpha(pixel), r, g, b))
            }
        }

        return result
    }
}

fun Bitmap.cropWithoutBars(): Bitmap {
    val bitmap = this
    val statusBarPx = 100  // Approx ~24dp at xxhdpi
    val navBarPx = 150     // Approx ~48dp at xxhdpi

    val width = bitmap.width
    val height = bitmap.height - statusBarPx - navBarPx

    return Bitmap.createBitmap(bitmap, 0, statusBarPx, width, height)
}
fun Bitmap.cropSideBarsOnly(): Bitmap {
    val bitmap = this
    val sideBarPx = 150  // Adjust based on your floating icon width

    val width = bitmap.width - (2 * sideBarPx)
    val height = bitmap.height

    return Bitmap.createBitmap(bitmap, sideBarPx, 0, width, height)
}

class AiNsfwGraderImp(
    val owner: LockdownAcessibilityService,
    val ownerScope: CoroutineScope,
    val nfswImagePreprocessor: NSFWSexyImagePreProcessing = NSFWSexyImagePreProcessing(),
    val aiNsfwTriggerTracker: AINfswTriggerTracker = AINfswTriggerTracker(),
    val bodyAreaDetector: BodyAreaDetector = BodyAreaDetector(),
    val skinDetector: SkinDetector = SkinDetector(),
    val imageEnhancer: ImageEnhancer = ImageEnhancer()
) : AiNsfwGrader {


    private val NSFW_FILE = "nsfw.tflite"
    private val SEXY_NSFW_FILE = "open_nsfw.tflite"

    private val OUTPUT_FILE_NFSW_COUNT = 2;
    private val OUTPUT_FILE_SEXY_NFSW_COUNT = 5;

    private val NFSW_THRESHOLD = 0.7

    private val nsfwInterpreter: Interpreter by lazy {
        Interpreter(loadNsfwModel(NSFW_FILE)) // Use the Interpreter class to create an instance
    }
    private val sexyNsfwInterpreter: Interpreter by lazy {
        Interpreter(loadNsfwModel(SEXY_NSFW_FILE)) // Use the Interpreter class to create an instance
    }

    fun loadNsfwModel(
        fileName: String
    ): MappedByteBuffer {
        val fileDescriptor: AssetFileDescriptor = owner.assets.openFd(fileName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
//    GadaiHape3070
    fun gradeImage(
        interpreter: Interpreter,
        input: ByteBuffer,
        outputClasses: Int = OUTPUT_FILE_NFSW_COUNT
    ): FloatArray {
        val output = Array(1) { FloatArray(outputClasses) }
        try {
            interpreter.run(input, output)
        } catch (e: Exception) {
            owner.showToast("Please contact dev, Error grading : ${e.message}")
        }
        return output[0] // Return the output array for further processing
    }

    var nsfwJobResetter : Job? = null
    fun resetNsfwCounterResetter(){
        nsfwJobResetter?.cancel()
        nsfwJobResetter = null
    }

    fun delayedNsfwResetter() {
        resetNsfwCounterResetter()
        nsfwJobResetter = ownerScope.launch(Dispatchers.IO) {
            // 2 minute
            delay(60 * 1000L * 2)
            owner.sharedPrefApi.setCurrentNsfwCounter(0)
        }
    }

    val NSFW_THRESHOLD_COUNTER = 2


    suspend fun trackNsfwOccurence(
        image: Bitmap,
        sexyModelReadings: FloatArray,
        sexyScore : Float =
//            0f,
            sexyModelReadings[4],
        nsfwScore : Float =
//            0f,
            sexyModelReadings[3]
    ): Boolean {
        var nsfwEvent = owner.sharedPrefApi.getCurrentNsfwCounter();
        if(nsfwScore <= NFSW_THRESHOLD){
            owner.sharedPrefApi.setCurrentSafeCounter(
                owner.sharedPrefApi.getCurrentSafeCounter() + 1
            )
            if(!MyApp.getInstance().userSetting.lockBySexy){
                return false
            }
            if(sexyScore <= NFSW_THRESHOLD){
                return false
            }
        }

        val logString = sexyModelReadings.joinToString(prefix = "[", postfix = "]", separator = ", ") { "%.2f".format(it) }
        aiNsfwTriggerTracker.logIssueIfDebugBuild(
            "NSFW Triggered: ai readings $logString",
            image
        )


        nsfwEvent += 1
        owner.sharedPrefApi.setCurrentNsfwCounter(nsfwEvent)
        return true
    }




    suspend fun getIfNsfwNeedLock(
        image: Bitmap,
        sexyModelReadings: FloatArray,
        sexyScore : Float =
//            0f,
            sexyModelReadings[4],
        nsfwScore : Float =
//            0f,
            sexyModelReadings[3]
    ): Boolean {
        if (!trackNsfwOccurence(
                image,
                sexyModelReadings,
                sexyScore,
                nsfwScore
        )) {
            return false
        }

        resetNsfwCounterResetter()
        owner.sharedPrefApi.setCurrentNsfwCounter(0)
        return true
    }

    fun judgeSexyNsfwModelAi(
        scaledBitmap: Bitmap
    ): FloatArray {
        val sexyOutputNsfw = gradeImage(
            sexyNsfwInterpreter,
            nfswImagePreprocessor.bitmapToByteBufferImageTensor(
                sexyNsfwInterpreter,
                scaledBitmap
            ),
            OUTPUT_FILE_SEXY_NFSW_COUNT
        )
        return sexyOutputNsfw
    }



    override fun runCheckNsfw(
        bitmap: Bitmap,
        onResult: (isNsfw: Boolean) -> Unit,
        onOccurrence: (safe: Int, nsfw: Int, bitmap: Bitmap) -> Unit
    ) {
        ownerScope.launch(Dispatchers.IO) {

            // Enhance the original bitmap FIRST for better detection in dark scenes
            val optimizedBitmap = imageEnhancer.enhanceImage(bitmap)

            // Then crop from the enhanced bitmap
            val centerCrop = optimizedBitmap.cropCenterSquare()
            val topCrop = optimizedBitmap.cropTopSquare().cropCenterShavedSquare(0.7f)
            val bottomCrop = optimizedBitmap.cropBottomSquare().cropCenterShavedSquare(0.7f)
//
//            // Body area detection with optimized crops
//            if(
//                !bodyAreaDetector.isNsfwBodyArea(centerCrop) &&
//                !bodyAreaDetector.isNsfwBodyArea(topCrop) &&
//                !bodyAreaDetector.isNsfwBodyArea(bottomCrop)
//            ){
//                owner.sharedPrefApi.setCurrentSafeCounter(
//                    owner.sharedPrefApi.getCurrentSafeCounter() + 1
//                )
//                withContext(Dispatchers.Main) {
//                    onOccurrence(
//                        owner.sharedPrefApi.getCurrentSafeCounter(),
//                        owner.sharedPrefApi.getCurrentNsfwCounter(),
//                        centerCrop
//                    )
//                }
//                return@launch
//            }


            val nsfwCount = owner.sharedPrefApi.getCurrentNsfwCounter()
            val safeCount = owner.sharedPrefApi.getCurrentSafeCounter()

            // Use optimized crops for NSFW model detection
            var sexyJudgement = judgeSexyNsfwModelAi(centerCrop)
            trackNsfwOccurence(centerCrop, sexyJudgement)
            if (nsfwCount == owner.sharedPrefApi.getCurrentNsfwCounter()) {
                owner.sharedPrefApi.setCurrentSafeCounter(safeCount) // TODO Hacky manual hardcode reset
                sexyJudgement = judgeSexyNsfwModelAi(topCrop)
                trackNsfwOccurence(topCrop, sexyJudgement)
            }

            if (nsfwCount == owner.sharedPrefApi.getCurrentNsfwCounter()) {
                owner.sharedPrefApi.setCurrentSafeCounter(safeCount) // TODO Hacky manual hardcode reset
                sexyJudgement = judgeSexyNsfwModelAi(bottomCrop)
                trackNsfwOccurence(bottomCrop, sexyJudgement)
            }

            withContext(Dispatchers.Main) {
                onOccurrence(
                    owner.sharedPrefApi.getCurrentSafeCounter(),
                    owner.sharedPrefApi.getCurrentNsfwCounter(),
                    centerCrop
                )
            }
        }
    }
}
