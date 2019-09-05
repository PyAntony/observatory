package observatory

import com.sksamuel.scrimage._
import observatory.Visualization.interpolateColor

import observatory.{Helpers => H}

/**
  * 5th milestone: value-added information visualization
  */
object Visualization2 {

  /**
    * @param point (x, y) coordinates of a point in the grid cell
    * @param d00   Top-left value
    * @param d01   Bottom-left value
    * @param d10   Top-right value
    * @param d11   Bottom-right value
    * @return A guess of the value at (x, y) based on the four known values, using bilinear interpolation
    *         See https://en.wikipedia.org/wiki/Bilinear_interpolation#Unit_Square
    */
  def bilinearInterpolation(point: CellPoint,
                            d00: Temperature,
                            d01: Temperature,
                            d10: Temperature,
                            d11: Temperature
                           ): Temperature = {

    val x = point.x
    val y = point.y
    val d00Factor = (1 - x) * (1 - y) // origin
    val d01Factor = (1 - x) * y
    val d10Factor = x * (1 - y)
    val d11Factor = x * y

    d00 * d00Factor + d01 * d01Factor + d10 * d10Factor + d11 * d11Factor
  }

  /**
    * @param grid   Grid to visualize
    * @param colors Color scale to use
    * @param tile   Tile coordinates to visualize
    * @return The image of the tile at (x, y, zoom) showing the grid using the given color scale
    */
  def visualizeGrid(grid: GridLocation => Temperature,
                    colors: Iterable[(Temperature, Color)],
                    tile: Tile
                   ): Image = {

    val imageWidth = 256
    val imageHeight = 256
    val allLocations = Interaction.generateTileWithLocations(tile)

    val allColors: Array[Color] = allLocations.par
      .map { loc =>
        val lat = loc.lat.toInt
        val lon = loc.lon.toInt
        bilinearInterpolation(
          CellPoint(loc.lon - lon, loc.lat - lat),
          grid(GridLocation(lat, lon)),
          grid(GridLocation(lat + 1, lon)),
          grid(GridLocation(lat, lon + 1)),
          grid(GridLocation(lat + 1, lon + 1)))
      }
      .map(temperature => interpolateColor(colors, temperature))
      .toArray

    Image(
      imageWidth,
      imageHeight,
      allColors.map(H.toPixel(_, 255))
    )
  }

}
