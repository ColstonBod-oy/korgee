import com.dragonbones.core.*
import korlibs.korge.dragonbones.*
import korlibs.time.*

fun KorgeDbArmatureDisplay.play(animationName: String): KorgeDbArmatureDisplay {
    animation.play(animationName)
    return this
}

fun KorgeDbArmatureDisplay.fadeIn(
    animationName: String, fadeInTime: TimeSpan, playTimes: Int = -1,
    layer: Int = 0, group: String? = null, fadeOutMode: AnimationFadeOutMode = AnimationFadeOutMode.SameLayerAndGroup
): KorgeDbArmatureDisplay? {
    animation.fadeIn(animationName, fadeInTime, playTimes, layer, group, fadeOutMode)
    return this
}
