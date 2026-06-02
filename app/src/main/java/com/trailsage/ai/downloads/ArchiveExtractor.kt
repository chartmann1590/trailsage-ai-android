package com.charles.trailsage.downloads

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File

object ArchiveExtractor {
    fun extractTarBz2(archive: File, destination: File) {
        destination.mkdirs()
        TarArchiveInputStream(BZip2CompressorInputStream(archive.inputStream().buffered())).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                val target = File(destination, entry.name).canonicalFile
                require(target.path.startsWith(destination.canonicalPath + File.separator)) { "Unsafe archive path" }
                if (entry.isDirectory) target.mkdirs() else { target.parentFile?.mkdirs(); target.outputStream().use { input.copyTo(it) } }
            }
        }
    }
}
