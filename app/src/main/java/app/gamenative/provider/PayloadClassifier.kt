package app.gamenative.provider

import java.io.File
import java.io.RandomAccessFile

object PayloadClassifier {
    private val zip = byteArrayOf(0x50, 0x4B)
    private val sevenZ = byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte())
    private val rar = byteArrayOf(0x52, 0x61, 0x72, 0x21)
    private val mz = byteArrayOf(0x4D, 0x5A)
    private val ole = byteArrayOf(
        0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(),
        0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte(),
    )

    fun classify(file: File, declaredName: String = file.name): PayloadKind {
        val header = readHeader(file, 8)
        val ext = declaredName.substringAfterLast('.', "").lowercase()
        return when {
            startsWith(header, zip) || startsWith(header, sevenZ) || startsWith(header, rar) ->
                PayloadKind.PORTABLE_ARCHIVE
            startsWith(header, mz) && hasPeSignature(file) -> PayloadKind.WINDOWS_EXE
            startsWith(header, ole) && ext == "msi" -> PayloadKind.WINDOWS_MSI
            ext == "exe" -> throw ProviderException(
                ProviderErrorCode.MALFORMED_RESPONSE,
                "File is not a valid Windows executable",
            )
            else -> PayloadKind.UNKNOWN
        }
    }

    private fun readHeader(file: File, count: Int): ByteArray {
        RandomAccessFile(file, "r").use { raf ->
            val buf = ByteArray(count.coerceAtMost(raf.length().toInt().coerceAtLeast(0)))
            raf.readFully(buf)
            return buf
        }
    }

    private fun hasPeSignature(file: File): Boolean {
        RandomAccessFile(file, "r").use { raf ->
            if (raf.length() < 0x40) return false
            raf.seek(0x3C)
            val peOffset = readLittleInt(raf).toLong() and 0xFFFFFFFFL
            if (peOffset + 4 > raf.length()) return false
            raf.seek(peOffset)
            return raf.read() == 0x50 && raf.read() == 0x45 && raf.read() == 0x00 && raf.read() == 0x00
        }
    }

    private fun readLittleInt(raf: RandomAccessFile): Int {
        val b0 = raf.readUnsignedByte()
        val b1 = raf.readUnsignedByte()
        val b2 = raf.readUnsignedByte()
        val b3 = raf.readUnsignedByte()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    private fun startsWith(header: ByteArray, prefix: ByteArray): Boolean {
        if (header.size < prefix.size) return false
        return prefix.indices.all { header[it] == prefix[it] }
    }
}
