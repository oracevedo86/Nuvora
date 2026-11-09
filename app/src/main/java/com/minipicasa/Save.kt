// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 oracevedo86
package com.minipicasa

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

fun saveBitmap(context: Context, bmp: Bitmap, quality: Int = 95): Boolean {
    if (Build.VERSION.SDK_INT < 29) return false
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "Nuvora_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Nuvora")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val outputUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    return try {
        val ok = resolver.openOutputStream(outputUri)?.use {
            bmp.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(50, 100), it)
        } ?: false
        if (!ok) throw RuntimeException("No se pudo escribir")
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(outputUri, values, null, null)
        true
    } catch (e: Exception) {
        resolver.delete(outputUri, null, null)
        false
    }
}

// Decodifica la imagen completa y aplica la orientación EXIF sin reducir resolución.
private fun loadOriginalBitmap(context: Context, uri: Uri): Bitmap? {
    val orientation = context.contentResolver.openInputStream(uri)?.use {
        ExifInterface(it).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )
    } ?: ExifInterface.ORIENTATION_NORMAL

    val options = BitmapFactory.Options().apply {
        inMutable = true
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    } ?: return null

    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f); matrix.preScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f); matrix.preScale(-1f, 1f)
        }
    }
    if (matrix.isIdentity) return decoded
    val oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    if (oriented !== decoded) decoded.recycle()
    return oriented
}

// Exporta a resolución original. El procesamiento de color trabaja por filas
// para limitar la memoria temporal; si el dispositivo no dispone de memoria,
// se informa del fallo sin dejar una imagen parcial en la galería.
fun saveEdited(context: Context, uri: Uri, recipe: Recipe, quality: Int = 95): Boolean {
    var original: Bitmap? = null
    var edited: Bitmap? = null
    return try {
        original = loadOriginalBitmap(context, uri) ?: return false
        edited = applyRecipe(original!!, recipe, inPlaceColor = true)
        saveBitmap(context, edited!!, quality)
    } catch (_: OutOfMemoryError) {
        false
    } catch (_: Exception) {
        false
    } finally {
        if (edited != null && edited !== original && !edited!!.isRecycled) edited!!.recycle()
        if (original != null && !original!!.isRecycled) original!!.recycle()
    }
}
