package observatory

import com.sksamuel.scrimage.Image
import observatory.Visualization.{interpolateColor, predictTemperature}

import observatory.{Helpers => H}

import scala.math.pow

/**
  * 3rd milestone: interactive visualization
  */
object Interaction {

  /**
    * @param tile Tile coordinates
    * @return The latitude and longitude of the top-left corner of the tile,
    *         as per http://wiki.openstreetmap.org/wiki/Slippy_map_tilenames
    */
  def tileLocation(tile: Tile): Location = tile.toGeoLocation

  /** Generate all Tile locations from the tile coordinates. */
  def generateTileWithLocations(tile: Tile): IndexedSeq[Location] = {

    val imageWidth = 256
    val imageHeight = 256

    val xOffset = tile.x * imageWidth
    val yOffset = tile.y * imageHeight
    val zoom = tile.zoom

    val allLocations = for {
      x <- 0 until imageWidth
      y <- 0 until imageHeight
    } yield Tile(
      x + xOffset,
      y + yOffset,
      8 + zoom // initial zoom = 8 (256 = 2^8)
    ).toGeoLocation

    allLocations
  }

  /**
    * @param temperatures Known temperatures
    * @param colors       Color scale
    * @param tile         Tile coordinates
    * @return A 256×256 image showing the contents of the given tile
    */
  def tile(temperatures: Iterable[(Location, Temperature)],
           colors: Iterable[(Temperature, Color)],
           tile: Tile
          ): Image = {

    val imageWidth = 256
    val imageHeight = 256
    val allLocations = generateTileWithLocations(tile)

    val allColors: Seq[Color] = allLocations.par
      .map(loc => (loc, predictTemperature(temperatures, loc))) //interpolate temperatures
      .map(tp => (tp._1, interpolateColor(colors, tp._2))) //interpolate colors
      .seq
      .sortBy(-_._1.lat) // latitude in reverse order: (90, -180), (90, -179), ...
      .map(_._2)

    Image(
      imageWidth,
      imageHeight,
      allColors.map(H.toPixel(_, 127)).toArray
    )
  }

  /**
    * Generates all the tiles for zoom levels 0 to 3 (included), for all the given years.
    *
    * @param yearlyData    Sequence of (year, data), where `data` is some data associated with
    *                      `year`. The type of `data` can be anything.
    * @param generateImage Function that generates an image given a year, a zoom level, the x and
    *                      y coordinates of the tile and the data to build the image from
    */
  def generateTiles[Data](yearlyData: Iterable[(Year, Data)],
                          generateImage: (Year, Tile, Data) => Unit
                         ): Unit = {
    val tiles = for {
      zoom <- 0 until 4
      x <- 0 until pow(2, zoom).toInt
      y <- 0 until pow(2, zoom).toInt
    } yield Tile(x, y, zoom)

    yearlyData.par.foreach(yearDataPair =>
      tiles.par.foreach(tile =>
        generateImage(yearDataPair._1, tile, yearDataPair._2)
      )
    )
  }

}
