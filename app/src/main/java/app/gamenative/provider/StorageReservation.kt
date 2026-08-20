package app.gamenative.provider

object StorageReservation {
    const val WINE_PREFIX_HEADROOM_BYTES = 2L * 1024L * 1024L * 1024L
    const val EXTRACT_SAFETY_NUMERATOR = 3L
    const val EXTRACT_SAFETY_DENOMINATOR = 2L

    fun requiredBytes(
        downloadSize: Long,
        uncompressedSize: Long = 0L,
        includeWineHeadroom: Boolean = false,
    ): Long {
        val extract = if (uncompressedSize > 0L) {
            uncompressedSize
        } else {
            downloadSize * EXTRACT_SAFETY_NUMERATOR / EXTRACT_SAFETY_DENOMINATOR
        }
        val wine = if (includeWineHeadroom) WINE_PREFIX_HEADROOM_BYTES else 0L
        return downloadSize + extract + wine
    }

    fun hasSpace(availableBytes: Long, requiredBytes: Long): Boolean =
        availableBytes >= requiredBytes

    fun formatShortage(availableBytes: Long, requiredBytes: Long): String {
        val missing = (requiredBytes - availableBytes).coerceAtLeast(0L)
        return "Need ${requiredBytes} bytes, $missing more required"
    }
}
