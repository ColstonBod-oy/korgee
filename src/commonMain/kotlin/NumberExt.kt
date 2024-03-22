import kotlin.math.*

fun Float.normalizeAlmostZero(epsilon: Float) = if (this.absoluteValue < epsilon) 0f else this
