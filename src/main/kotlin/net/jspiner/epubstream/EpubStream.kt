package net.jspiner.epubstream

import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.functions.BiFunction
import net.jspiner.epubstream.dto.Container
import net.jspiner.epubstream.dto.MimeType
import net.jspiner.epubstream.dto.Package
import net.jspiner.epubstream.parser.parseContainer
import net.jspiner.epubstream.parser.parsePackage
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream


class EpubStream(val file: File) {

    private val MIMETYPE_FILE_NAME = "mimetype"
    private val CONTAINER_FILE_NAME = "META-INF/container.xml"

    private var extractedDirectory: File? = null

    private var mimeType: MimeType? = null
    private var container: Container? = null
    private var opf: Package? = null

    fun unzip(outputPath: String = "./" + file.nameWithoutExtension): Completable {
        if (!file.exists()) return Completable.error(NoSuchFileException(file))

        return Completable.create { emitter ->
            val inputStream = ZipInputStream(FileInputStream(file))
            while (true) {
                val entry: ZipEntry = inputStream.nextEntry ?: break
                val newFile = File(outputPath + File.separator + entry.name)

                File(newFile.parent).mkdirs()
                extractFile(inputStream, newFile)
                inputStream.closeEntry()
            }
            inputStream.close()

            extractedDirectory = File(outputPath)
            emitter.onComplete()
        }
    }

    private fun extractFile(zipIn: ZipInputStream, newFile: File) {
        val outputStream = BufferedOutputStream(FileOutputStream(newFile))
        val bytesIn = ByteArray(2048)
        var read = 0
        while ((zipIn.read(bytesIn).also { read = it }) != -1) {
            outputStream.write(bytesIn, 0, read)
        }
        outputStream.close()
    }

    fun getExtractedDirectory(): Single<File> {
        return if (extractedDirectory != null) {
            Single.just(extractedDirectory)
        } else {
            unzip().toSingle { extractedDirectory }
        }
    }

    fun getMimeType(): Single<MimeType> {
        return if (mimeType != null) {
            Single.just(mimeType)
        } else {
            getExtractedDirectory()
                    .map { it.toPath().resolve(MIMETYPE_FILE_NAME) }
                    .map { Files.readAllLines(it).joinToString() }
                    .map { MimeType(it) }
                    .doOnSuccess { this@EpubStream.mimeType = it }
        }
    }

    fun getContainer(): Single<Container> {
        return if (container != null) {
            Single.just(container)
        } else {
            getMimeType()
                    .flatMap { getExtractedDirectory() }
                    .map { it.resolve(CONTAINER_FILE_NAME) }
                    .map { parseToDocument(it) }
                    .map { parseContainer(it) }
                    .doOnSuccess { this@EpubStream.container = it }
        }
    }

    fun getOpf(): Single<Package> {
        return if (opf != null) {
            Single.just(opf)
        } else {
            getContainer()
                    .zipWith(getExtractedDirectory(), BiFunction { container: Container, file: File -> container to file })
                    .map { pair -> pair.second.resolve(pair.first.rootFiles[0].fullPath) }
                    .map { parseToDocument(it) }
                    .map { parsePackage(it) }
        }
    }

    fun getToc(): Single<Any> {
        return Single.just(1)
    }

    fun getFile(): Single<Any> {
        return Single.just(1)
    }

}