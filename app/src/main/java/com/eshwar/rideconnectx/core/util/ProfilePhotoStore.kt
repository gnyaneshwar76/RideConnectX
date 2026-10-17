package com.eshwar.rideconnectx.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * Takes whatever the rider picked from their gallery and turns it into a square
 * avatar the app can use.
 *
 * The important part is the square: photos arrive landscape or portrait, and the
 * avatar is drawn in a circle. Rather than letting the circle chop an arbitrary
 * corner off, the largest centred square is taken first — so a landscape shot
 * keeps its middle rather than its left edge, and a portrait keeps the subject
 * rather than the ceiling. The circular mask is then applied at draw time.
 */
@Singleton
class ProfilePhotoStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private companion object {
        const val TAG = "RCX-Photo"

        /** Output edge in pixels. Comfortably beyond any avatar size on screen. */
        const val OUTPUT_SIZE = 1080

        const val FILE_NAME = "rider_avatar.jpg"
        const val QUALITY = 92

        /** Ceiling for the in-memory working copy while cropping. */
        const val MAX_WORKING_EDGE = 2400

        /** Edge of the copy that travels with the account. See [encodeForCloud]. */
        const val CLOUD_SIZE = 256
        const val CLOUD_QUALITY = 80

        /** Size requested from Google for the account picture. */
        const val GOOGLE_AVATAR_PX = 512
    }

    val photoFile: File get() = File(context.filesDir, FILE_NAME)

    val existingPhotoUri: Uri?
        get() = photoFile.takeIf { it.exists() }?.let(Uri::fromFile)

    /**
     * Reads [source], corrects its orientation, centre-crops to a square and
     * stores it. Returns the stored file's Uri, or null if the image could not
     * be read.
     */
    suspend fun save(source: Uri): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decodeScaled(source) ?: return@runCatching null
            val upright = applyExifRotation(source, bitmap)
            val square = centreCrop(upright)

            FileOutputStream(photoFile).use { out ->
                square.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            }

            if (square !== upright) square.recycle()
            if (upright !== bitmap) upright.recycle()
            bitmap.recycle()

            Uri.fromFile(photoFile)
        }.onFailure { Log.e(TAG, "Could not save profile photo", it) }.getOrNull()
    }

    /**
     * Downloads a remote avatar — the Google account picture — and stores it
     * through the same crop as a gallery pick, so it ends up square and the
     * right size regardless of what Google serves.
     *
     * Returns null on any failure; a missing profile picture is not worth
     * interrupting sign-in over.
     */
    suspend fun saveFromUrl(url: String): Uri? = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://")) return@withContext null
        // Google serves the account picture at 96px ("=s96-c"), which looks
        // blurry in the avatar circle. The same URL gives any size on request.
        val sized = url.replace(Regex("=s\\d+-c$"), "=s$GOOGLE_AVATAR_PX-c")

        runCatching {
            val temp = File(context.cacheDir, "avatar_download.jpg")
            (java.net.URL(sized).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                instanceFollowRedirects = true
            }.inputStream.use { input ->
                FileOutputStream(temp).use(input::copyTo)
            }

            val saved = save(Uri.fromFile(temp))
            temp.delete()
            saved
        }.onFailure { Log.w(TAG, "Could not fetch Google avatar", it) }.getOrNull()
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        runCatching { photoFile.delete() }
        Unit
    }

    /**
     * The avatar as a Base64 JPEG small enough to live inside the rider's
     * Firestore document.
     *
     * The picture used to be a local file and nothing else, so signing in on a
     * new phone — or clearing the app's data — lost it, even though everything
     * else about the profile came back. Storing it in the user document is what
     * makes it part of the account.
     *
     * [CLOUD_SIZE] is deliberately small. A 1080px avatar is ~200 KB and
     * Firestore caps a document at 1 MiB; 256px is more than the largest circle
     * the app ever draws and lands around 20 KB, which leaves the document
     * comfortably inside the limit. The full-size local copy is untouched.
     */
    suspend fun encodeForCloud(): String? = withContext(Dispatchers.IO) {
        runCatching {
            if (!photoFile.exists()) return@runCatching null
            val full = BitmapFactory.decodeFile(photoFile.absolutePath) ?: return@runCatching null
            val small = Bitmap.createScaledBitmap(full, CLOUD_SIZE, CLOUD_SIZE, true)

            val bytes = java.io.ByteArrayOutputStream().also {
                small.compress(Bitmap.CompressFormat.JPEG, CLOUD_QUALITY, it)
            }.toByteArray()

            if (small !== full) small.recycle()
            full.recycle()

            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        }.onFailure { Log.w(TAG, "Could not encode avatar for cloud", it) }.getOrNull()
    }

    /**
     * Writes a cloud-stored avatar back to disk.
     *
     * Never overwrites a picture that is already here: the local copy is the
     * full-resolution one the rider actually cropped, and the cloud copy is a
     * 256px reduction of it. Restore is for when there is nothing to lose.
     */
    suspend fun restoreFromCloud(base64: String): Uri? = withContext(Dispatchers.IO) {
        if (base64.isBlank() || photoFile.exists()) return@withContext existingPhotoUri

        runCatching {
            val bytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
            FileOutputStream(photoFile).use { it.write(bytes) }
            Uri.fromFile(photoFile)
        }.onFailure { Log.w(TAG, "Could not restore avatar from cloud", it) }.getOrNull()
    }

    /**
     * Decodes at roughly the size actually needed. Camera photos are routinely
     * 12 MP; decoding one at full size to draw it 88dp wide is how you get an
     * OutOfMemoryError on a mid-range phone.
     */
    /**
     * Decodes any image the platform can read, at roughly the size needed.
     *
     * `ImageDecoder` on API 28+ covers HEIC/HEIF, WEBP and AVIF, which
     * BitmapFactory either refuses or mangles — and HEIC is the default camera
     * format on a lot of phones now. BitmapFactory remains the fallback.
     */
    suspend fun decodeFull(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = android.graphics.ImageDecoder.createSource(
                    context.contentResolver, uri,
                )
                android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.isMutableRequired = false
                    decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    // Cap the working size; a 50MP HEIC decoded whole is an OOM.
                    val longest = maxOf(info.size.width, info.size.height)
                    if (longest > MAX_WORKING_EDGE) {
                        val f = MAX_WORKING_EDGE.toFloat() / longest
                        decoder.setTargetSize(
                            (info.size.width * f).toInt().coerceAtLeast(1),
                            (info.size.height * f).toInt().coerceAtLeast(1),
                        )
                    }
                }
            } else {
                decodeScaled(uri)?.let { applyExifRotation(uri, it) }
            }
        }.onFailure { Log.e(TAG, "Could not decode picked photo", it) }.getOrNull()
    }

    /**
     * Crops exactly what the rider framed inside the circle.
     *
     * The transform is applied with the same matrix the preview used, scaled up
     * to the output size, so what they positioned is what gets saved — no
     * coordinate guesswork between preview and result.
     *
     * @param scale user pinch-zoom, 1f = fitted
     * @param offsetX/[offsetY] user pan in *preview* pixels
     * @param viewportPx the preview circle's diameter in pixels
     */
    /** Turns the stored picture a quarter turn clockwise, in place (N17). */
    suspend fun rotateClockwise(): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val full = BitmapFactory.decodeFile(photoFile.absolutePath) ?: return@runCatching null
            val turned = Bitmap.createBitmap(
                full, 0, 0, full.width, full.height, Matrix().apply { postRotate(90f) }, true,
            )
            FileOutputStream(photoFile).use { turned.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            if (turned !== full) turned.recycle()
            full.recycle()
            Uri.fromFile(photoFile)
        }.onFailure { Log.e(TAG, "Could not rotate profile photo", it) }.getOrNull()
    }

    suspend fun saveCropped(
        source: Uri,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        viewportPx: Int,
    ): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decodeFull(source) ?: return@runCatching null
            val out = Bitmap.createBitmap(OUTPUT_SIZE, OUTPUT_SIZE, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(out)
            canvas.drawColor(android.graphics.Color.BLACK)

            // preview -> output
            val k = OUTPUT_SIZE.toFloat() / viewportPx

            // "fit inside the circle" baseline, matching the preview.
            val fit = minOf(
                viewportPx.toFloat() / bitmap.width,
                viewportPx.toFloat() / bitmap.height,
            )
            val total = fit * scale * k

            val matrix = Matrix().apply {
                postScale(total, total)
                postTranslate(
                    (OUTPUT_SIZE - bitmap.width * total) / 2f + offsetX * k,
                    (OUTPUT_SIZE - bitmap.height * total) / 2f + offsetY * k,
                )
            }

            canvas.drawBitmap(
                bitmap, matrix,
                android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG),
            )

            FileOutputStream(photoFile).use { out.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }

            out.recycle()
            bitmap.recycle()
            Uri.fromFile(photoFile)
        }.onFailure { Log.e(TAG, "Could not crop photo", it) }.getOrNull()
    }

    private fun decodeScaled(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }

        val shortestEdge = min(bounds.outWidth, bounds.outHeight)
        if (shortestEdge <= 0) return null

        val options = BitmapFactory.Options().apply {
            // lastOrNull: an image already smaller than OUTPUT_SIZE (Google's
            // avatar is 96px) matches nothing, and `.last()` threw on it - which
            // is why the Google picture never arrived and the "E" showed instead.
            inSampleSize = generateSequence(1) { it * 2 }
                .takeWhile { shortestEdge / it >= OUTPUT_SIZE }
                .lastOrNull() ?: 1
        }

        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    /**
     * Phones record orientation in EXIF rather than rotating the pixels, so a
     * photo taken in portrait decodes sideways unless this is applied.
     */
    private fun applyExifRotation(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** Largest centred square, scaled to [OUTPUT_SIZE]. */
    private fun centreCrop(bitmap: Bitmap): Bitmap {
        val edge = min(bitmap.width, bitmap.height)
        val left = (bitmap.width - edge) / 2
        val top = (bitmap.height - edge) / 2

        val square = Bitmap.createBitmap(bitmap, left, top, edge, edge)
        if (edge <= OUTPUT_SIZE) return square

        val scaled = Bitmap.createScaledBitmap(square, OUTPUT_SIZE, OUTPUT_SIZE, true)
        if (scaled !== square) square.recycle()
        return scaled
    }
}
