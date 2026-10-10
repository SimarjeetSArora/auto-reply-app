package com.simar.autoreply.widget

import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath

/**
 * Widgets can't host Compose, so the app's Material icons are drawn onto bitmaps by walking the
 * icon's vector paths.
 */
internal object WidgetArt {

    /** Draws [icon] filled with [paint] into the square at ([left], [top]) of side [size]. */
    fun drawIcon(canvas: Canvas, icon: ImageVector, paint: Paint, left: Float, top: Float, size: Float) {
        canvas.save()
        canvas.translate(left, top)
        canvas.scale(size / icon.viewportWidth, size / icon.viewportHeight)
        drawGroup(canvas, icon.root, paint)
        canvas.restore()
    }

    private fun drawGroup(canvas: Canvas, group: VectorGroup, paint: Paint) {
        canvas.save()
        canvas.translate(group.translationX + group.pivotX, group.translationY + group.pivotY)
        canvas.rotate(group.rotation)
        canvas.scale(group.scaleX, group.scaleY)
        canvas.translate(-group.pivotX, -group.pivotY)
        for (node in group) {
            when (node) {
                is VectorPath -> {
                    val path = node.pathData.toPath().asAndroidPath()
                    path.fillType = if (node.pathFillType == PathFillType.EvenOdd) {
                        android.graphics.Path.FillType.EVEN_ODD
                    } else {
                        android.graphics.Path.FillType.WINDING
                    }
                    canvas.drawPath(path, paint)
                }
                is VectorGroup -> drawGroup(canvas, node, paint)
            }
        }
        canvas.restore()
    }
}
