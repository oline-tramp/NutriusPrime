package com.example.kthealthsync.domain.util

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

object BarcodeScannerHelper {
    fun scanBarcode(
        context: Context,
        onSuccess: (String) -> Unit,
        onFailure: ((Exception) -> Unit)? = null,
        onCanceled: (() -> Unit)? = null
    ) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39
            )
            .enableAutoZoom()
            .build()

        val scanner = GmsBarcodeScanning.getClient(context, options)
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val code = barcode.rawValue ?: barcode.displayValue
                if (!code.isNullOrBlank()) {
                    onSuccess(code)
                }
            }
            .addOnCanceledListener {
                onCanceled?.invoke()
            }
            .addOnFailureListener { e ->
                onFailure?.invoke(e)
            }
    }
}
