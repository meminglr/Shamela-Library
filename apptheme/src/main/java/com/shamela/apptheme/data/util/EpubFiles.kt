package com.shamela.apptheme.data.util

import org.readium.r2.streamer.container.ZipArchiveContainer
import org.readium.r2.streamer.parser.EpubParser
import org.readium.r2.streamer.parser.PubBox

/**
 * Parses the EPUB at [path], hands the result to [block], then closes the zip file the parser
 * opened. The parser never closes it itself: the open file was then closed later by the garbage
 * collector, and if the book had been deleted meanwhile that close failed with an I/O error,
 * which crashed the app (seen when deleting a section's books right after listing them).
 */
inline fun <T> parseEpubAndClose(path: String, block: (PubBox?) -> T): T {
    val box = EpubParser().parse(path)
    try {
        return block(box)
    } finally {
        runCatching { (box?.container as? ZipArchiveContainer)?.zipFile?.close() }
    }
}
