package com.tonkeeper

import android.app.Application
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.annotation.Dimension
import androidx.core.content.ContextCompat
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.core.ImagePipelineConfig

class App: Application() {

    companion object {
        lateinit var instance: App

        @ColorInt
        fun getColor(@ColorRes resId: Int): Int {
            return ContextCompat.getColor(instance, resId)
        }

        @Dimension
        fun getDimension(@DimenRes resId: Int): Float {
            return instance.resources.getDimension(resId)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        initFresco()
    }

    private fun initFresco() {
        val config = ImagePipelineConfig.newBuilder(this)
            .experiment().setNativeCodeDisabled(true)
        Fresco.initialize(this, config.build())
    }

}