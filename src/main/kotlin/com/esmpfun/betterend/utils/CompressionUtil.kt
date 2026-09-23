package com.esmpfun.betterend.utils

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Gzip helpers for snapshot files. */
object CompressionUtil {

    fun compress(data: ByteArray): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        GZIPOutputStream(byteArrayOutputStream).use { gzipOutputStream ->
            gzipOutputStream.write(data)
        }
        return byteArrayOutputStream.toByteArray()
    }

    fun decompress(compressedData: ByteArray): ByteArray {
        val byteArrayInputStream = ByteArrayInputStream(compressedData)
        val byteArrayOutputStream = ByteArrayOutputStream()

        GZIPInputStream(byteArrayInputStream).use { gzipInputStream ->
            val buffer = ByteArray(1024)
            var len: Int
            while (gzipInputStream.read(buffer).also { len = it } != -1) {
                byteArrayOutputStream.write(buffer, 0, len)
            }
        }

        return byteArrayOutputStream.toByteArray()
    }

    fun <T> compressObject(obj: T): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        ObjectOutputStream(byteArrayOutputStream).use { objectOutputStream ->
            objectOutputStream.writeObject(obj)
        }
        return compress(byteArrayOutputStream.toByteArray())
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> decompressObject(compressedData: ByteArray): T {
        val decompressed = decompress(compressedData)
        val byteArrayInputStream = ByteArrayInputStream(decompressed)
        ObjectInputStream(byteArrayInputStream).use { objectInputStream ->
            return objectInputStream.readObject() as T
        }
    }

    fun formatSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0

        return when {
            gb >= 1 -> String.format("%.2f GB", gb)
            mb >= 1 -> String.format("%.2f MB", mb)
            kb >= 1 -> String.format("%.2f KB", kb)
            else -> "$bytes bytes"
        }
    }
}
