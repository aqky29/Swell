package com.music.swell.playback.smart

import android.media.MediaDataSource

/**
 * Reads [source] a block at a time and keeps the last few blocks.
 *
 * Seeking into a WebM, the extractor walks the file in a great many small
 * reads, and each one that reaches the cache pays that path's whole
 * overhead: a window three minutes into a song took seven seconds, nearly
 * all of it spent getting to the window rather than decoding it.
 *
 * First written for [MixWaveform]; the analyzer's whole-track and model-window
 * decodes read the same cached copies the same way and pay the same overhead,
 * so they share it.
 *
 * Over a partially cached rendition (the head pass) a block simply comes back
 * short where the cached prefix ends, and a read past it is an end-of-stream,
 * exactly as it is without the buffer.
 */
internal class BlockBufferedDataSource(private val source: MediaDataSource) : MediaDataSource() {
    private val blocks = object : LinkedHashMap<Long, ByteArray>(BLOCKS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ByteArray>?) = size > BLOCKS
    }
    private val length by lazy { source.size }

    override fun getSize(): Long = length

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (size <= 0) return 0
        val total = length
        if (total >= 0 && position >= total) return -1
        var done = 0
        while (done < size) {
            val at = position + done
            if (total >= 0 && at >= total) break
            val index = at / BLOCK_BYTES
            val block = blocks[index] ?: load(index) ?: break
            val within = (at - index * BLOCK_BYTES).toInt()
            if (within >= block.size) break
            val n = minOf(size - done, block.size - within)
            System.arraycopy(block, within, buffer, offset + done, n)
            done += n
        }
        return if (done == 0) -1 else done
    }

    private fun load(index: Long): ByteArray? {
        val block = ByteArray(BLOCK_BYTES)
        var filled = 0
        while (filled < BLOCK_BYTES) {
            val n = source.readAt(index * BLOCK_BYTES + filled, block, filled, BLOCK_BYTES - filled)
            if (n <= 0) break
            filled += n
        }
        if (filled == 0) return null
        val loaded = if (filled == BLOCK_BYTES) block else block.copyOf(filled)
        blocks[index] = loaded
        return loaded
    }

    override fun close() = source.close()

    private companion object {
        const val BLOCK_BYTES = 256 * 1024
        const val BLOCKS = 8
    }
}
