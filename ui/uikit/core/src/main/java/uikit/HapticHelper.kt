package uikit

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object HapticHelper {

    fun selection(context: Context) {
        play(context, 30, 30)
    }

    fun error(context: Context) {
        play(context, 0, 10, 30, 20, 30, 30)
    }

    fun warning(context: Context) {
        play(context, 0, 10, 50, 30)
    }

    fun success(context: Context) {
        play(context, 0, 30, 50, 10)
    }

    fun impactLight(context: Context) {
        play(context, 0, 10, 20, 10)
    }

    private fun play(context: Context, vararg pattern: Long) {
        val vibrator = getVibrator(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            vibrator.vibrate(pattern, -1)
        }
    }

    private fun getVibrator(context: Context): Vibrator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }
}