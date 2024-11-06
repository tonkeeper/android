package com.tonapps.blockchain.ton

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class EntropyHelper(private val context: Context): SensorEventListener {

    private val isStarted = AtomicBoolean(false)
    private val byteArrayOutputStream = ByteArrayOutputStream()

    private val sensorManager: SensorManager by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    private val accelerometer: Sensor? by lazy {
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    fun start() {
        if (isStarted.get()) {
            return
        }
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        isStarted.set(true)
    }

    fun stop() {
        if (!isStarted.get()) {
            return
        }
        sensorManager.unregisterListener(this)
        byteArrayOutputStream.close()
        isStarted.set(false)
    }

    fun getSeed(size: Int): ByteArray {
        val byteArray = byteArrayOutputStream.toByteArray()
        if (size >= byteArray.size) {
            return byteArray
        }
        return byteArray.copyOfRange(byteArray.size - size, byteArray.size)
    }

    override fun onSensorChanged(event: SensorEvent) {
        for (value in event.values) {
            val bytes = ByteBuffer.allocate(4).putFloat(value).array()
            byteArrayOutputStream.write(bytes)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) { }
}