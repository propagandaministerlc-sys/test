package app.ultragrid

import android.graphics.ColorMatrixColorFilter
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

fun PhotoFilter.androidColorFilter(): android.graphics.ColorFilter? = matrix?.let { ColorMatrixColorFilter(it) }

fun PhotoFilter.composeColorFilter(): ColorFilter? = matrix?.let { ColorFilter.colorMatrix(ColorMatrix(it.copyOf())) }
