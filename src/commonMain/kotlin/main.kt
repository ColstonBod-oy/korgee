import com.dragonbones.core.*
import korlibs.event.*
import korlibs.image.atlas.*
import korlibs.image.color.*
import korlibs.image.format.*
import korlibs.io.file.std.*
import korlibs.korge.*
import korlibs.korge.dragonbones.*
import korlibs.korge.input.*
import korlibs.korge.mascots.*
import korlibs.korge.scene.*
import korlibs.korge.view.*
import korlibs.korge.view.animation.*
import korlibs.korge.view.property.*
import korlibs.korge.virtualcontroller.*
import korlibs.math.*
import korlibs.math.geom.*
import korlibs.math.interpolation.*
import korlibs.time.*
import kotlin.math.*

suspend fun main() = Korge(
    title = "Korge Platformer",
    //windowSize = Size(1280, 720),
    windowSize = Size(512, 512),
    backgroundColor = Colors["#2b2b2b"],
    displayMode = KorgeDisplayMode(ScaleMode.SHOW_ALL, Anchor.TOP_LEFT, clipBorders = false),
) {
    val sceneContainer = sceneContainer()

    sceneContainer.changeTo { MyScene() }
    //sceneContainer.changeTo({ RaycastingExampleScene() })
}

object COLLISIONS {
    val OUTSIDE = -1
    val EMPTY = 0
    val DIRT = 1
    val LADDER = 2
    val STONE = 3

    fun isSolid(type: Int, direction: Vector2D): Boolean {
        return type == DIRT || type == STONE || type == OUTSIDE
    }
}

class MyScene : Scene() {
    @KeepOnReload
    var currentPlayerPos = Point(200, 200)

    @KeepOnReload
    var initZoom = 32.0

    @KeepOnReload
    var zoom = 128.0

    @ViewProperty
    var gravity = Vector2D(0, 10)

    lateinit var characters: ImageDataContainer
    lateinit var player: ImageDataView

    @ViewProperty
    fun teleportInitialPos() {
        currentPlayerPos = Point(200, 200)
        player.pos = currentPlayerPos
    }

    override suspend fun SContainer.sceneMain() {
        var immediateSetCamera = false
        onStageResized { width, height ->
            size = Size(views.actualVirtualWidth, views.actualVirtualHeight)
            immediateSetCamera = true
        }
        val atlas = MutableAtlasUnit()
        val world = resourcesVfs["ldtk/Typical_2D_platformer_example.ldtk"].readLDTKWorldExt()
        val collisions = world.createCollisionMaps()
        //val mapView = LDTKViewExt(world, showCollisions = true)
        val mapView = LDTKViewExt(world, showCollisions = false)
        //println(collisions)
        /*val db = KorgeDbFactory()
        db.loadKorgeMascots()

        player = db.buildArmatureDisplayGest()!!
            .xy(currentPlayerPos)
            .play(KorgeMascotsAnimations.IDLE)
            .scale(0.080)*/

        characters = resourcesVfs["chumbo.ase"].readImageDataContainer(ASE.toProps(), atlas = atlas)
        player = imageDataView(characters["chumbo"], "idle", playing = true, smoothing = false) {
            xy(currentPlayerPos)
        }

        val camera = camera {
            this += mapView
            this += player
        }

        text(
            """
                Use the arrow keys '<-' '->' to move Gest
                'z' for zoom
                Space for jumping
                startPos = $currentPlayerPos
            """.trimIndent()
        ).xy(8, 8)

        //val textCoords = text("-").xy(8, 200)
        //mapView.mouse { move { textCoords.text = collisions.pixelToTile(it.currentPosLocal.toInt()).toString() } }

        val buttonRadius = 110f
        val virtualController = virtualController(
            buttons = listOf(
                VirtualButtonConfig.SOUTH,
                //VirtualButtonConfig(Key.Z, GameButton.START, Anchor.BOTTOM_RIGHT, offset = Point(0, -buttonRadius * 1.5f))
            ),
            buttonRadius = buttonRadius
        ).also { it.container.alpha(0.5f) }

        var playerSpeed = Vector2D(0, 0)
        val mapBounds = mapView.getLocalBounds()

        fun tryMoveDelta(delta: Point): Boolean {
            val newPos = player.pos + delta

            val collisionPoints = listOf(
                newPos,
                newPos + Point(-5, 0),
                newPos + Point(+5, 0),
                newPos + Point(-5, -7),
                newPos + Point(+5, -7),
                newPos + Point(-5, -14),
                newPos + Point(+5, -14),
            )

            var set = collisionPoints.all { !COLLISIONS.isSolid(collisions.getPixel(it), delta) }
            if (set) {
                player.pos = newPos
                currentPlayerPos = newPos
            }
            return set
        }

        var playerState = "idle"
        fun setState(name: String, time: TimeSpan) {
            if (playerState != name) {
                playerState = name
                //player.animation.fadeIn(playerState, time)
            }
        }

        var jumping = false
        var moving = false

        fun updateState() {
            when {
                jumping -> setState("jump", 0.1.seconds)
                moving -> setState("walk", 0.1.seconds)
                else -> setState("idle", 0.3.seconds)
            }
        }

        fun updated(right: Boolean, up: Boolean, scale: Float = 1f) {
            if (!up) {
                player.scaleX = player.scaleX.absoluteValue * if (right) +1f else -1f
                tryMoveDelta(Point(2.0, 0) * (if (right) +1 else -1) * scale)
                player.speed = 2.0 * scale
                moving = true
            } else {
                player.speed = 1.0
                moving = false
            }
            updateState()
            //updateTextContainerPos()
        }

        virtualController.apply {
            down(GameButton.BUTTON_SOUTH) {
                val isInGround = playerSpeed.y.isAlmostZero()
                //if (isInGround) {
                if (true) {
                    if (!jumping) {
                        jumping = true
                        updateState()
                    }
                    playerSpeed += Vector2D(0, -5.5)
                }
            }
            changed(GameButton.LX) {
                if (it.new.normalizeAlmostZero(.075f) == 0f) {
                    updated(right = it.new > 0f, up = true, scale = 1f)
                }
            }
        }

        fun createSize(zoom: Double): Size {
            return Size(zoom * (width / height), zoom)
        }

        var currentRect = Rectangle.getRectWithAnchorClamped(player.pos, createSize(initZoom), Anchor.CENTER, mapBounds)

        /*virtualController.down(GameButton.START) {
            val zoomC = zoom
            val zoomC2 = if (zoomC >= 1024.0) 128.0 else zoomC * 2
            zoom = zoomC2
        }*/

        val FREQ = 60.hz
        addFixedUpdater(FREQ) {
            // Move character
            run {
                val lx = virtualController.lx.normalizeAlmostZero(.075f)
                when {
                    lx < 0f -> {
                        updated(right = false, up = false, scale = lx.absoluteValue)
                    }
                    lx > 0f -> {
                        updated(right = true, up = false, scale = lx.absoluteValue)
                    }
                }
            }

            // Apply gravity
            run {
                playerSpeed += gravity * FREQ.timeSpan.seconds
                if (!tryMoveDelta(playerSpeed)) {
                    playerSpeed = Vector2D.ZERO
                    if (jumping) {
                        jumping = false
                        updateState()
                    }
                }
            }

            // Update camera
            run {
                val newRect = Rectangle.getRectWithAnchorClamped(player.pos, createSize(zoom), Anchor.CENTER, mapBounds)
                if (immediateSetCamera) {
                    immediateSetCamera = false
                    currentRect = newRect
                }
                currentRect = (0.05 * 0.5).toRatio().interpolate(currentRect, newRect)
                //camera.setTo(currentRect.rounded())
                camera.setTo(currentRect)
                initZoom = zoom
            }
        }
    }
}
