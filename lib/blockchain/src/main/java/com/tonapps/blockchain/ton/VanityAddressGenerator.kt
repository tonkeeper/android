package com.tonapps.blockchain.ton

import android.os.Build
import android.util.Log
import com.tonapps.blockchain.ton.contract.BaseWalletContract
import com.tonapps.blockchain.ton.extensions.toWalletAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.ton.api.pk.PrivateKeyEd25519
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.pow
import kotlin.time.DurationUnit
import kotlin.time.measureTimedValue

object VanityAddressGenerator {
    private val threadCount = Runtime.getRuntime().availableProcessors()

    suspend fun start(word: String = "xyz") {
        val estimatedAttempts = estimateRequiredAttempts(word)
        log("Estimated attempts required: $estimatedAttempts")

        val result = generate(word, timeoutMinutes = 60)
        if (result == null) {
            log("Failed to generate address with word: $word")
        } else {
            val (address, privateKey) = result
            log("Address: $address")
            val key = privateKey.key
            log("PrivateKey: Hex=${key.hex()}")
            log("PrivateKey: Base64=${key.base64()}")
        }
    }

    private suspend fun generate(word: String, timeoutMinutes: Long): Pair<String, PrivateKeyEd25519>? = coroutineScope {
        val result = AtomicReference<Pair<String, PrivateKeyEd25519>?>(null)
        val startTime = System.currentTimeMillis()
        val totalAttempts = AtomicLong(0)

        // Use a shared coroutine scope for launching coroutines
        val job = launch(Dispatchers.IO) {
            // Create a list to hold child coroutines
            val jobs = List(threadCount) {
                launch {
                    var attempts = 0L

                    while (result.get() == null) {
                        val privateKeyMeasureTimed = measureTimedValue {
                            generateOptimizedPrivateKey(word)
                        }

                        val publicKeyMeasureTimed = measureTimedValue {
                            privateKeyMeasureTimed.value.publicKey()
                        }
                        val contractMeasureTimed = measureTimedValue {
                            BaseWalletContract.create(publicKeyMeasureTimed.value, "v5r1", false)
                        }
                        val addressMeasureTimed = measureTimedValue {
                            contractMeasureTimed.value.address.toWalletAddress(false)
                        }
                        val privateKey = privateKeyMeasureTimed.value
                        val address = addressMeasureTimed.value

                        attempts++
                        totalAttempts.incrementAndGet()

                        // Check if the address contains the desired word
                        if (address.contains(word, ignoreCase = true)) {
                            result.compareAndSet(null, Pair(address, privateKey))
                            break
                        }

                        // Optional: Log progress every 100,000 attempts
                        if (attempts % 10 == 0L) {
                            val elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0
                            val aps = totalAttempts.get() / elapsedTime
                            val estimatedAttempts = estimateRequiredAttempts(word)
                            val estimatedTotalTime = estimatedAttempts / aps
                            val estimatedTimeRemaining = estimatedTotalTime - elapsedTime
                            val estimatedHours = estimatedTimeRemaining / 3600.0

                            log(
                                "Attempts: ${totalAttempts.get()}, " +
                                        "APS: ${String.format("%.2f", aps)}, " +
                                        "Estimated Time Remaining: ${String.format("%.2f", estimatedHours)} hours"
                            )


                            val measureTimed = listOf(privateKeyMeasureTimed, publicKeyMeasureTimed, contractMeasureTimed, addressMeasureTimed).sortedBy {
                                it.duration
                            }.reversed()

                            val logText = measureTimed.joinToString("\n") {
                                "${it.duration.toString(DurationUnit.MILLISECONDS)}: ${it.value.javaClass.simpleName}"
                            }

                            log(logText)
                        }
                    }
                }
            }

            // Wait for all child coroutines to complete
            jobs.forEach { it.join() }
        }

        // Wait for the result or timeout
        withTimeoutOrNull(timeoutMinutes * 60 * 1000) {
            job.join()
        }

        // Cancel the job if still active after timeout
        if (job.isActive) {
            job.cancelAndJoin()
        }

        return@coroutineScope result.get()
    }

    private fun log(message: String) {
        Log.d("VanityAddressGenerator", message)
    }

    private fun estimateRequiredAttempts(word: String): Long {
        // Considering case-insensitive matching
        val probabilityPerChar = (2.0 / 64.0)
        val probability = probabilityPerChar.pow(word.length)
        val positions = 48 - word.length + 1 // Assuming address length of 48 characters
        val totalProbability = positions * probability
        return (1 / totalProbability).toLong()
    }

    private fun generateOptimizedPrivateKey(desiredString: String): PrivateKeyEd25519 {
        val random = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            SecureRandom.getInstanceStrong()
        } else {
            SecureRandom()
        }

        val keyBytes = ByteArray(32)
        random.nextBytes(keyBytes)

        val base64Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        var bitIndex = 0

        for (char in desiredString) {
            val value = base64Chars.indexOf(char.uppercaseChar()).takeIf { it != -1 }
                ?: base64Chars.indexOf(char.lowercaseChar())

            for (i in 5 downTo 0) {
                val byteIndex = bitIndex / 8
                val bitInByte = 7 - (bitIndex % 8)
                val bitValue = (value shr i) and 1
                keyBytes[byteIndex] = (keyBytes[byteIndex].toInt() and (1 shl bitInByte).inv()).toByte()
                keyBytes[byteIndex] = (keyBytes[byteIndex].toInt() or (bitValue shl bitInByte)).toByte()
                bitIndex++
            }
        }

        return PrivateKeyEd25519(keyBytes)
    }

    private fun Char.swapCase(): Char = if (this.isUpperCase()) this.lowercaseChar() else this.uppercaseChar()


}