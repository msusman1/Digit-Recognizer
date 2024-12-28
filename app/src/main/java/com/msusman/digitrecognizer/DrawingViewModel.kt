package com.msusman.digitrecognizer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Environment
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePaint
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


sealed interface DrawingAction {
    data object OnNewPathStart : DrawingAction
    data class OnDraw(val offset: Offset) : DrawingAction
    data object OnPathEnd : DrawingAction
}

class DrawingViewModel : ViewModel() {
    val TAG = "DrawingViewModel"
    private val _state: MutableStateFlow<List<Offset>> =
        MutableStateFlow(emptyList())
    val state = _state.asStateFlow()


    private val _result: MutableStateFlow<String> = MutableStateFlow("")
    val result = _result.asStateFlow()

    val classifierHelper = DigitClassifier(App.instance.applicationContext)

    init {
        classifierHelper.initialize()
            .addOnFailureListener { e -> Log.e(TAG, "Error to setting up digit classifier.", e) }

    }

    fun onAction(action: DrawingAction) {
        when (action) {
            is DrawingAction.OnDraw -> onDraw(action.offset)
            DrawingAction.OnNewPathStart -> onNewPathStart()
            DrawingAction.OnPathEnd -> onPathEnd()
        }
    }

    private fun dpTOPixle(dp: Dp): Int {
        val density = App.instance.resources.displayMetrics.density
        val pixels = (dp.value * density).toInt()
        return pixels
    }

    private fun onPathEnd() {
        val bitmap = createBitmap()
        extract(bitmap)
        saveBitmapToExternalStorage(bitmap, "number_image.png")
    }

    fun createBitmap(): Bitmap {
        val bitmapWidth = dpTOPixle(400.dp)
        val bitmapHeight = dpTOPixle(400.dp)
        return Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888).apply {
            val canvas = Canvas(this)
            canvas.drawColor(Color.Black.toArgb())
            val path = android.graphics.Path()
            state.value.forEachIndexed { index, offset ->
                if (index == 0) {
                    path.moveTo(offset.x, offset.y)
                } else {
                    path.lineTo(offset.x, offset.y)
                }

                canvas.drawPath(path, linePaint)
            }
        }
    }


    fun loadFromAssets() {
        val bitmp = BitmapFactory.decodeResource(App.instance.resources, R.drawable.nine)
        extract(bitmp)
    }

    private fun extract(bit: Bitmap) {
        classifierHelper.classifyAsync(bit)
            .addOnSuccessListener { resultText ->
                _result.value = resultText
                Log.d(TAG, "Result: $resultText")
            }
            .addOnFailureListener { e ->

                Log.e(TAG, "Error classifying drawing.", e)
            }
    }

    private fun onNewPathStart() {
        _state.update { emptyList() }
    }


    private fun onDraw(offset: Offset) {
        _state.update {
            it + offset
        }
    }

    fun saveBitmapToExternalStorage(bitmap: Bitmap, fileName: String?) {
        val externalStorage = Environment.getExternalStorageDirectory()
        val directory = File(
            App.instance.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "MyAppImages"
        ) // Create a folder for your app
        if (!directory.exists()) {
            directory.mkdirs() // Create directory if not exists
        }

        val file = File(directory, fileName)
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(
                    Bitmap.CompressFormat.PNG, 100, out
                )
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun onCleared() {
        super.onCleared()
//        classifierHelper.clearImageClassifier()
    }
}


val linePaint = Paint().apply {
    color = Color.White.toArgb()
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
    strokeWidth = 70f
    style = Paint.Style.STROKE
    isAntiAlias = true
}

fun Paint.asComposeStyle(): DrawStyle = Stroke(
    width = this.strokeWidth,
    cap = this.asComposePaint().strokeCap,
    join = this.asComposePaint().strokeJoin
)

