import korlibs.datastructure.*
import korlibs.memory.*
import korlibs.korge.view.*
import korlibs.korge.view.tiles.*
import korlibs.image.bitmap.*
import korlibs.image.color.*
import korlibs.image.format.*
import korlibs.image.tiles.*
import korlibs.io.file.*
import korlibs.korge.ldtk.*
import korlibs.korge.ldtk.view.*
import korlibs.korge.view.filter.*
import korlibs.math.geom.*

//private val DO_EXTRUSION = false
private val DO_EXTRUSION = true

private fun IStackedIntArray2.getFirst(pos: PointInt): Int = getFirst(pos.x, pos.y)
private fun IStackedIntArray2.getLast(pos: PointInt): Int = getLast(pos.x, pos.y)

class LDTKCollisions(val world: LDTKWorld, val stack: IStackedIntArray2) {
    fun tileToPixel(tilePos: PointInt): PointInt = (tilePos.toFloat() * world.ldtk.defaultGridSize).toIntFloor()
    fun pixelToTile(pixelPos: PointInt): PointInt = (pixelPos.toFloat() / world.ldtk.defaultGridSize).toIntFloor()

    fun getTile(tilePos: PointInt): Int = stack.getLast(tilePos)
    fun getPixel(pixelPos: PointInt): Int = getTile(pixelToTile(pixelPos))
    fun getPixel(pixelPos: Point): Int = getPixel(pixelPos.toIntFloor())
}

fun LDTKWorld.createCollisionMaps(layerId: String = "Collisions"): LDTKCollisions {
    val ldtk = this.ldtk
    val world = SparseChunkedStackedIntArray2()
    for (level in ldtk.levels) {
        //println("## level: ${level.identifier}")
        for (layer in (level.layerInstances ?: emptyList()).asReversed()) {
            if (layer.identifier != layerId) continue
            val intGrid = IntArray2(layer.cWid, layer.cHei, layer.intGridCSV.copyOf(layer.cWid * layer.cHei))
            //println("intGrid=$intGrid")
            //println(" - layer=${layer.identifier}, level.worldX=${level.worldX}, level.worldY=${level.worldY}")
            world.putChunk(
                StackedIntArray2(intGrid, startX = level.worldX / ldtk.defaultGridSize, startY = level.worldY / ldtk.defaultGridSize)
            )
        }
    }
    return LDTKCollisions(this, world)
}

class LDTKViewExt(
    val world: LDTKWorld,
    val showCollisions: Boolean = false
) : Container() {
    init {
        val ldtk = world.ldtk
        val layersDefsById = world.layersDefsById
        val tilesetDefsById = world.tilesetDefsById

        val colors = Bitmap32((ldtk.defaultGridSize + 4) * 16, ldtk.defaultGridSize)
        val intsTileSet = TileSet(
            (0 until 16).map { TileSetTileInfo(it, colors.slice(RectangleInt((ldtk.defaultGridSize + 4) * it, 0, ldtk.defaultGridSize, ldtk.defaultGridSize))) }
        )

        // @TODO: Do this for each layer, since we might have several IntGrid layers
        for (layer in ldtk.defs.layers) {
            for (value in layer.intGridValues) {
                colors.fill(Colors[value.color], (ldtk.defaultGridSize + 4) * value.value)
                //println("COLOR: ${value.value} : ${value.color}")
            }
        }
        container {
            for (level in ldtk.levels) {
                container {
                    val color = Colors[level.levelBgColor ?: ldtk.bgColor]
                    solidRect(level.pxWid, level.pxHei, color)
                    for (layer in (level.layerInstances ?: emptyList()).asReversed()) {
                        //for (layer in (level.layerInstances ?: emptyList())) {
                        val layerDef = layersDefsById[layer.layerDefUid] ?: continue
                        val tilesetExt = tilesetDefsById[layer.tilesetDefUid] ?: continue
                        val intGrid = IntArray2(layer.cWid, layer.cHei, layer.intGridCSV.copyOf(layer.cWid * layer.cHei))
                        val tileData = StackedIntArray2(layer.cWid, layer.cHei, -1)
                        val tileset = tilesetExt.def
                        val gridSize = tileset.tileGridSize

                        //val fsprites = FSprites(layer.autoLayerTiles.size)
                        //val view = fsprites.createView(bitmap).also { it.scale(2) }
                        //addChild(view)
                        for (tile in layer.autoLayerTiles) {
                            val (px, py) = tile.px
                            val (tileX, tileY) = tile.src
                            val x = px / gridSize
                            val y = py / gridSize
                            val dx = px % gridSize
                            val dy = py % gridSize
                            val tx = tileX / gridSize
                            val ty = tileY / gridSize
                            val cellsTilesPerRow = tileset.pxWid / gridSize
                            val tileId = ty * cellsTilesPerRow + tx
                            val flipX = tile.f.hasBitSet(0)
                            val flipY = tile.f.hasBitSet(1)
                            tileData.push(x, y, TileInfo(tileId, flipX = flipX, flipY = flipY, offsetX = dx, offsetY = dy).data)
                        }
                        if (tilesetExt.tileset != null) {
                            tileMap(tileData, tilesetExt.tileset!!, smoothing = false)
                                .alpha(layerDef.displayOpacity)
                                .also { if (!DO_EXTRUSION) it.filters(IdentityFilter.Nearest) }
                                .also { it.overdrawTiles = 1 }
                            tileMap(intGrid, intsTileSet, smoothing = false)
                                .visible(showCollisions)
                                .also { if (!DO_EXTRUSION) it.filters(IdentityFilter.Nearest) }
                                .also { it.overdrawTiles = 1 }
                        }
                        //tileset!!.
                        //println(intGrid)
                    }
                }.xy(level.worldX, level.worldY)
                //break // ONLY FIRST LEVEL
                //}.filters(IdentityFilter.Nearest).scale(2)
            }
            //}.xy(300, 300)
        }
    }
}


class ExtTileset(val def: TilesetDefinition, val tileset: TileSet?)

class LDTKWorld(
    val ldtk: LDTKJson,
    val tilesetDefsById: Map<Int, ExtTileset>
) {
    val layersDefsById: Map<Int, LayerDefinition> = ldtk.defs.layers.associateBy { it.uid }
}

suspend fun VfsFile.readLDTKWorldExt(): LDTKWorld {
    val file = this
    val json = file.readString()
    val ldtk = LDTKJson.load(json)
    val tilesetDefsById: Map<Int, ExtTileset> = ldtk.defs.tilesets.associate { def ->
        val bitmap = def.relPath?.let {
            val bmp = file.parent[it].readBitmap()
            if (DO_EXTRUSION) bmp.toBMP32() else bmp
        }
        val tileSet = bitmap?.let {
            val tset = TileSet(bitmap.slice(), def.tileGridSize, def.tileGridSize)
            if (DO_EXTRUSION) tset.extrude(border = 2) else tset
        }
        def.uid to ExtTileset(def, tileSet)
    }
    return LDTKWorld(ldtk, tilesetDefsById)
}

fun TileSet.extrude(border: Int = 1, mipmaps: Boolean = false): TileSet {
    val bitmaps = this.textures.map { (it as BmpSlice).extract().toBMP32() }
    return TileSet.fromBitmaps(width, height, bitmaps, border, mipmaps = mipmaps)
}
