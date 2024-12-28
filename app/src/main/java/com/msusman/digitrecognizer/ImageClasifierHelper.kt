package com.msusman.digitrecognizer


import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifier
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifierResult
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class ImageClassifierHelper(private val context: Context) {

    private var threshold: Float = THRESHOLD_DEFAULT
    private var maxResults: Int = MAX_RESULTS_DEFAULT
    private var imageClassifier: ImageClassifier? = null

    init {
        inspectModel()
        setupImageClassifier()
    }

    fun clearImageClassifier() {
        imageClassifier?.close()
        imageClassifier = null
    }

    fun isClosed(): Boolean {
        return imageClassifier == null
    }

    fun loadMappedModel(context: Context, modelName: String): MappedByteBuffer {
        val assetManager = context.assets
        val fileDescriptor = assetManager.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength

        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun inspectModel() {
        val modelName = "mnist.tflite"
        val modelBuffer = loadMappedModel(context, modelName)
        val options = Interpreter.Options()
//        options.addDelegate(GpuDelegate())
        val interpreter = Interpreter(modelBuffer, options)
        val inputShape = interpreter.getInputTensor(0).shape()
        val outputShape = interpreter.getOutputTensor(0).shape()

        Log.d(TAG, "Model Input Shape: ${inputShape.contentToString()}")
        Log.d(TAG, "Model Output Shape: ${outputShape.contentToString()}")
    }

    fun setupImageClassifier() {
        val baseOptionsBuilder = BaseOptions.builder()
//        baseOptionsBuilder.setDelegate(Delegate.CPU)

        val modelName = "mnist.tflite"
        baseOptionsBuilder.setModelAssetPath(modelName)
        Log.d(TAG, "Model Path: mnist.tflite")

        try {
            val baseOptions = baseOptionsBuilder.build()
            val optionsBuilder =
                ImageClassifier.ImageClassifierOptions.builder()
                    .setScoreThreshold(threshold)
                    .setMaxResults(maxResults)
                    .setRunningMode(RunningMode.IMAGE)
                    .setBaseOptions(baseOptions)


            val options = optionsBuilder.build()
            imageClassifier =
                ImageClassifier.createFromOptions(context, options)
        } catch (e: IllegalStateException) {
            Log.e(
                TAG,
                "IllegalStateException Image classifier failed to load model with error: " + e.message
            )
        } catch (e: RuntimeException) {
            Log.e(
                TAG,
                "RuntimeException Image classifier failed to load model with error: " + e.message
            )
        }
    }


    fun classifyImage(image: Bitmap): ResultBundle? {
        if (imageClassifier == null) return null

        // Inference time is the difference between the system time at the start and finish of the
        // process
        val startTime = SystemClock.uptimeMillis()

        // Convert the input Bitmap object to an MPImage object to run inference
        val mpImage = BitmapImageBuilder(image).build()

        // Run image classification using MediaPipe Image Classifier API
        imageClassifier?.classify(mpImage)?.also { classificationResults ->
            val inferenceTimeMs = SystemClock.uptimeMillis() - startTime
            return ResultBundle(listOf(classificationResults), inferenceTimeMs)
        }


        // If imageClassifier?.classify() returns null, this is likely an error. Returning null
        // to indicate this.
        Log.d(TAG, "classifyImage error: Image classifier failed to classify.")
        return null
    }


    data class ResultBundle(
        val results: List<ImageClassifierResult>,
        val inferenceTime: Long,
    )

    companion object {
        const val MAX_RESULTS_DEFAULT = 3
        const val THRESHOLD_DEFAULT = 0.5F
        private const val TAG = "ImageClassifierHelper"
    }


}