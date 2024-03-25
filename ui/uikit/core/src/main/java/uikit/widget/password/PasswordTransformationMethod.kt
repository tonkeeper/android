package uikit.widget.password

import android.view.View

class PasswordTransformationMethod: android.text.method.PasswordTransformationMethod() {

    companion object {
        private const val originalDot = '\u2022'
        private const val newDot = '●'

        private var instance: PasswordTransformationMethod? = null

        fun getInstance(): PasswordTransformationMethod {
            if (instance == null) {
                instance = PasswordTransformationMethod()
            }
            return instance!!
        }
    }

    override fun getTransformation(source: CharSequence?, view: View?): CharSequence {
        return PasswordCharSequence(super.getTransformation(source, view))
    }

    private class PasswordCharSequence(
        val transformation: CharSequence
    ) : CharSequence by transformation {

        override fun get(index: Int): Char = if (transformation[index] == originalDot) {
            newDot
        } else {
            transformation[index]
        }
    }
}