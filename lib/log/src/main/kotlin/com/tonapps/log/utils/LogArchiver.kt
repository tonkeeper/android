package com.tonapps.log.utils

import java.io.*
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher

interface LogArchiver {
    fun archive(files: List<File>): Boolean
}

internal class LogArchiverEncoder(
    private val output: File,
    private val outputPub: File,
    private val pubKey: ByteArray?,
) : LogArchiver {

    // Move files to archive folder and create Zip
    override fun archive(files: List<File>): Boolean {
        // get list of all logs
        val archivedFiles = archivingFiles(files)

        // create output file
        FileManager.recreateFile(output)
        FileManager.recreateFile(outputPub)

        if (!zip(output, archivedFiles)) {
            return false
        }

        // Encrypt logs
        try {
            if (pubKey == null) {
                copyFiles(output, outputPub)
            } else {
                encryptFileWithRsa(pubKey, output, outputPub)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            return false
        } finally {
            FileManager.deleteFile(output)
        }

        return true
    }

    private fun archivingFiles(files: List<File>): List<File> {
        return buildList {
            files.forEach { file ->
                if (file.exists()) {
                    if (file.isDirectory) {
                        val list = file.listFiles()?.toList() ?: emptyList()
                        addAll(archivingFiles(list))
                    } else {
                        add(file.apply { setExecutable(false) })
                    }
                }
            }
        }
    }

    private fun zip(output: File, files: List<File>): Boolean {
        if (files.isEmpty()) {
            FileManager.deleteFile(output)
            return false
        }

        var out: ZipOutputStream? = null
        var fos: FileOutputStream? = null

        val outputName = output.nameWithoutExtension

        try {
            val buffer = ByteArray(1024)
            for (file in files) {
                if (!file.exists() || !file.isFile || file.length() < 4) {
                    continue
                }

                if (fos == null) {
                    fos = FileOutputStream(output)
                    out = ZipOutputStream(fos)
                }

                if (out != null) {
                    val fis = FileInputStream(file.absolutePath)
                    out.putNextEntry(ZipEntry("${outputName}${File.separator}${file.name}"))

                    var length: Int
                    while (true) {
                        length = fis.read(buffer)
                        if (length > 0) {
                            out.write(buffer, 0, length)
                        } else {
                            break
                        }
                    }

                    out.closeEntry()
                    fis.close()
                    FileManager.deleteFile(file)
                }
            }

            return output.length() != 0L
        } catch (e: Exception) {
            return false
        } finally {
            FileManager.closeAndFlush(out)
            FileManager.closeAndFlush(fos)
        }
    }

    private fun copyFiles(inFile: File, outFile: File) {
        val arrIn = ByteArray(501)
        BufferedInputStream(FileInputStream(inFile))
            .use { input ->
                BufferedOutputStream(FileOutputStream(outFile))
                    .use { out ->
                        while (input.read(arrIn) > 0) {
                            out.write(arrIn)
                        }
                    }
            }
    }

    private fun encryptFileWithRsa(pubKey: ByteArray, inFile: File, outFile: File) {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        val factory = KeyFactory.getInstance("RSA")
        val publicKeySpec = X509EncodedKeySpec(pubKey)
        val publicKey = factory.generatePublic(publicKeySpec)

        cipher.init(Cipher.ENCRYPT_MODE, publicKey)

        val arrIn = ByteArray(245)
        BufferedInputStream(FileInputStream(inFile))
            .use { input ->
                BufferedOutputStream(FileOutputStream(outFile))
                    .use { out ->
                        while (input.read(arrIn) > 0) {
                            out.write(cipher.doFinal(arrIn))
                        }
                    }
            }
    }
}
