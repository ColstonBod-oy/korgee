import korlibs.event.*
import korlibs.image.atlas.*
import korlibs.image.color.*
import korlibs.image.format.*
import korlibs.io.file.std.*
import korlibs.korge.*
import korlibs.korge.animate.*
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
    private const val OUTSIDE = -1
    private const val EMPTY = 0
    private const val DIRT = 1
    private const val LADDER = 2
    private const val STONE = 3

    fun isSolid(type: Int, direction: Vector2D): Boolean {
        return type == DIRT || type == STONE || type == OUTSIDE
    }

    fun isLadder(type: Int, direction: Vector2D): Boolean {
        return type == LADDER
    }
}

class MyScene : Scene() {
    @KeepOnReload
    var currentPlayerPos = Point(200, 150)

    @KeepOnReload
    var initZoom = 32.0

    @KeepOnReload
    var zoom = 128.0

    @ViewProperty
    var gravity = Vector2D(0, 10)

    private lateinit var characters: ImageDataContainer
    lateinit var player: ImageDataView

    @ViewProperty
    fun teleportInitialPos() {
        currentPlayerPos = Point(200, 150)
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
            anchor(0.5f, 0f)
            xy(currentPlayerPos)
        }

        val camera = camera {
            this += mapView
            this += player
        }

        text(
            """
                Use the arrow keys '<-' '->' to move Chumbo
                Space for jumping
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

        fun tryMoveDeltaX(delta: Point): Boolean {
            val newPos = player.pos + delta

            val collisionPoints = listOf(
                // This causes the collision point to be equal
                // to the player's new position, which is always
                // at the top of the sprite when jumping
                //newPos,

                // This does the exact same thing as above
                // because "y = 0" is the top of the sprite
                // and moves down with a positive value
                //newPos + Point(-12, 0),
                //newPos + Point(+12, 0),

                newPos + Point(-12, +14),
                newPos + Point(+12, +14),
                newPos + Point(-12, +28.5),
                newPos + Point(+12, +28.5),
            )

            val set = collisionPoints.all { !COLLISIONS.isSolid(collisions.getPixel(it), delta) }
            if (set) {
                player.pos = newPos
                currentPlayerPos = newPos
            }
            return set
        }

        var climbing = false

        fun tryMoveDeltaY(delta: Point): Boolean {
            val newPos = player.pos + delta

            val collisionPoints = listOf(
                newPos + Point(-6, +29),
                newPos + Point(+6, +29),
            )

            val set = collisionPoints.all { COLLISIONS.isLadder(collisions.getPixel(it), delta) }
            if (set) {
                player.pos = newPos
                currentPlayerPos = newPos
                climbing = true
            }
            return set
        }

        var playerState = "idle"
        fun setState(name: String, time: TimeSpan) {
            if (playerState != name) {
                playerState = name

                animator {
                    tween(time = time, easing = Easing.EASE_IN)
                    block {
                        player.animation = playerState
                    }
                }
            }
        }

        var jumping = false
        var walking = false

        fun updateState() {
            when {
                jumping -> setState("jumping", 0.1.seconds)
                climbing -> setState("climbing", 0.1.seconds)
                walking -> setState("walking", 0.1.seconds)
                else -> setState("idle", 0.3.seconds)
            }
        }

        fun updated(left: Boolean, right: Boolean, up: Boolean, down: Boolean, midAir: Boolean, scale: Float = 1f) {
            if (!midAir) {
                if (left || right) {
                    player.scaleX = player.scaleX.absoluteValue * if (right) +1f else -1f
                    tryMoveDeltaX(Point(2.0, 0) * (if (right) +1 else -1) * scale)
                    player.speed = 2.0 * scale
                    climbing = false
                    walking = true
                } else if (up || down) {
                    tryMoveDeltaY(Point(0, 0.7) * (if (up) -1 else +1) * scale)
                    player.speed = 0.7 * scale
                    walking = false
                }
            } else {
                player.speed = 1.0
                climbing = false
                walking = false
            }
            updateState()
            //updateTextContainerPos()
        }

        virtualController.apply {
            down(GameButton.BUTTON_SOUTH) {
                val isInGround = playerSpeed.y.isAlmostZero()
                if (isInGround) {
                    if (!jumping) {
                        jumping = true
                        updateState()
                    }
                    playerSpeed += Vector2D(0, -5.5)
                }
            }

            // Changes where the player is looking while in midair
            changed(GameButton.LX) {
                if (it.new.normalizeAlmostZero(.075f) == 0f) {
                    updated(
                        left = it.new < 0f,
                        right = it.new > 0f,
                        up = false,
                        down = false,
                        midAir = true,
                        scale = 1f
                    )
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
                val ly = virtualController.ly.normalizeAlmostZero(.075f)
                when {
                    lx < 0f -> {
                        updated(
                            left = true,
                            right = false,
                            up = false,
                            down = false,
                            midAir = false,
                            scale = lx.absoluteValue
                        )
                    }

                    lx > 0f -> {
                        updated(
                            left = false,
                            right = true,
                            up = false,
                            down = false,
                            midAir = false,
                            scale = lx.absoluteValue
                        )
                    }

                    ly < 0f -> {
                        updated(
                            left = false,
                            right = false,
                            up = true,
                            down = false,
                            midAir = false,
                            scale = ly.absoluteValue
                        )
                    }

                    ly > 0f -> {
                        updated(
                            left = false,
                            right = false,
                            up = false,
                            down = true,
                            midAir = false,
                            scale = ly.absoluteValue
                        )
                    }
                }
            }

            // Apply gravity
            run {
                // Only apply gravity if player is not climbing
                if (!tryMoveDeltaY(playerSpeed)) {
                    playerSpeed += gravity * FREQ.timeSpan.seconds
                }

                if (!tryMoveDeltaX(playerSpeed) && !tryMoveDeltaY(playerSpeed)) {
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
