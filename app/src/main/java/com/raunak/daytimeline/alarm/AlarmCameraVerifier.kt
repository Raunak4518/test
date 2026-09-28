package com.raunak.daytimeline.alarm

import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

object AlarmCameraVerifier {
    fun scan(bitmap: Bitmap, onResult: (String?) -> Unit) {
        val image = InputImage.fromBitmap(bitmap, 0)
        BarcodeScanning.getClient().process(image)
            .addOnSuccessListener { codes -> onResult(codes.firstOrNull()?.rawValue) }
            .addOnFailureListener { onResult(null) }
    }
}
