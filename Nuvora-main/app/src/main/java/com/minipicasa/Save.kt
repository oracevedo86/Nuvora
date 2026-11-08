package com.minipicasa

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

fun saveBitmap(context: Context, bmp: Bitmap): Boolean {
    if (Build.VERSION.SDK_INT < 29) return false
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "MiniPicasa_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(
            MediaStore.Images.Media.RELATIVE_PATH,
            Environment.DIRECTORY_PICTURES + "/MiniPicasa"
        )
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: return false
    return try {
        val ok = resolver.openOutputStream(uri)?.use {
            bmp.compress(Bitmap.CompressFormat.JPEG, 95, it)
        } ?: false
        if (!ok) throw RuntimeException("No se pudo escribir")
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        true
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        false
    }
}

// Carga la foto grande, aplica la receta y guarda una copia nueva.
fun saveEdited(context: Context, uri: Uri, recipe: Recipe): Boolean {
    return try {
        val full = loadScaledBitmap(context, uri, 4096) ?: return false
        val edited = applyRecipe(full, recipe)
        saveBitmap(context, edited)
    } catch (e: Throwable) {
        false
    }
}
