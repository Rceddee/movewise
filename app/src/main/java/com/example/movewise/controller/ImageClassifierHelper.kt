package com.example.movewise.controller

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class ImageClassifierHelper(context: Context) {
    private var interpreter: Interpreter? = null
    private var labels = listOf<String>()

    init {
        try {
            val modelBuffer = loadModelFile(context, "food_model.tflite")
            val options = Interpreter.Options()
            options.numThreads = 2
            interpreter = Interpreter(modelBuffer, options)
            labels = context.assets.open("labels.txt").bufferedReader().use { it.readLines() }
        } catch (e: Exception) {
            e.printStackTrace()
            interpreter = null
            labels = listOf("Unknown")
        }
    }

    private fun loadModelFile(context: Context, filename: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(filename)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    @Synchronized
    fun classify(imageProxy: ImageProxy, rotationDegrees: Int): String {
        // Guard: if the interpreter was already closed (e.g., user hit Capture), skip quietly
        val localInterpreter = interpreter ?: run {
            imageProxy.close()
            return "Unknown"
        }

        return try {
            val bitmap = imageProxy.toBitmap()
            imageProxy.close()

            val rotatedBitmap = rotateBitmap(bitmap, rotationDegrees)

            val imageProcessor = ImageProcessor.Builder()
                .add(ResizeOp(224, 224, ResizeOp.ResizeMethod.BILINEAR))
                .build()

            var tensorImage = TensorImage(DataType.UINT8)
            tensorImage.load(rotatedBitmap)
            tensorImage = imageProcessor.process(tensorImage)

            val probabilityBuffer = Array(1) { ByteArray(labels.size) }
            localInterpreter.run(tensorImage.buffer, probabilityBuffer)

            val probabilities = probabilityBuffer[0]
            var maxIdx = -1
            var maxProb = -1

            for (i in probabilities.indices) {
                val unsignedProb = probabilities[i].toInt() and 0xFF
                if (unsignedProb > maxProb) {
                    maxProb = unsignedProb
                    maxIdx = i
                }
            }

            if (maxIdx != -1 && maxIdx < labels.size && maxProb > 80) {
                labels[maxIdx]
            } else {
                "Unknown"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Unknown"
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return bitmap
        val matrix = Matrix()
        matrix.postRotate(rotationDegrees.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    @Synchronized
    fun close() {
        interpreter?.close()
        interpreter = null
    }
}

