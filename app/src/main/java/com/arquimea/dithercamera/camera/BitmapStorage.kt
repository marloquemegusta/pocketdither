package com.arquimea.dithercamera.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.MediaStore.Images.ImageColumns
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BitmapStorage {
    private val RELATIVE_DIR = "${Environment.DIRECTORY_PICTURES}/DitherCamera"

    fun save(context: Context, bitmap: Bitmap): Uri? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "dither_$timestamp.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, RELATIVE_DIR)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: return null

        return try {
            resolver.openOutputStream(uri)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 96, stream)
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (_: IOException) {
            resolver.delete(uri, null, null)
            null
        }
    }

    fun findLatestSavedImageUri(context: Context): Uri? {
        val projection = arrayOf(ImageColumns._ID, ImageColumns.RELATIVE_PATH)
        val selection = "${ImageColumns.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf("$RELATIVE_DIR/")
        val sortOrder = "${ImageColumns.DATE_ADDED} DESC"
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idIndex = cursor.getColumnIndexOrThrow(ImageColumns._ID)
                val id = cursor.getLong(idIndex)
                return Uri.withAppendedPath(collection, id.toString())
            }
        }

        return null
    }
}
