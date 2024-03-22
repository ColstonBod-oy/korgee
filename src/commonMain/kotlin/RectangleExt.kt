import korlibs.math.geom.*
import korlibs.math.interpolation.*

fun Ratio.interpolate(l: Rectangle, r: Rectangle): Rectangle = Rectangle.interpolated(l, r, this)

fun RectangleD.Companion.getRectWithAnchor(pos: Point, size: Size, anchor: Anchor = Anchor.CENTER): Rectangle {
    val topLeft = pos - size * anchor
    return Rectangle(topLeft, size)
}

fun RectangleD.Companion.getRectWithAnchorClamped(pos: Point, size: Size, anchor: Anchor = Anchor.CENTER, clampBounds: Rectangle, keepProportions: Boolean = true): Rectangle {
    var rect = getRectWithAnchor(pos, size, anchor)
    if (rect.right > clampBounds.right) rect = rect.copy(x = rect.x - (rect.right - clampBounds.right))
    if (rect.bottom > clampBounds.bottom) rect = rect.copy(y = rect.y - (rect.bottom - clampBounds.bottom))
    if (rect.left < clampBounds.left) rect = rect.copy(x = rect.x - (rect.left - clampBounds.left))
    if (rect.top < clampBounds.top) rect = rect.copy(y = rect.y - (rect.top - clampBounds.top))

    return when {
        rect.right > clampBounds.right || rect.bottom > clampBounds.bottom ->
            rect.applyScaleMode(clampBounds, ScaleMode.COVER, Anchor.TOP_LEFT)
        else -> rect
    }
}
