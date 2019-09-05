package observatory

import Visualization._
import Helpers.memoizeFnc

import scala.collection.mutable.{Map => MMAP}

/**
  * 4th milestone: value-added information
  */
object Manipulation {

  val GRID_MAPS: MMAP[Int, MMAP[GridLocation, Temperature]] = MMAP.empty

  /**
    * @param temperatures Known temperatures
    * @return A function that, given a latitude in [-89, 90] and a longitude in [-180, 179],
    *         returns the predicted temperature at this location
    */
  def makeGrid(temperatures: Iterable[(Location, Temperature)]):
  GridLocation => Temperature = {

    val tempsHash = temperatures.hashCode()
    if (!GRID_MAPS.contains(tempsHash))
      GRID_MAPS.update(tempsHash, MMAP.empty[GridLocation, Temperature])

    val memoizedGetTemperature = memoizeFnc(GRID_MAPS(tempsHash))(
      (gridLoc: GridLocation) => predictTemperature(temperatures, gridLoc.toLocation)
    )

    memoizedGetTemperature
  }

  /**
    * @param temperaturess Sequence of known temperatures over the years (each element of the collection
    *                      is a collection of pairs of location and temperature)
    * @return A function that, given a latitude and a longitude, returns the average temperature at this location
    */
  def average(temperaturess: Iterable[Iterable[(Location, Temperature)]]):
  GridLocation => Temperature = {

    gridLoc: GridLocation => {
      val yearlyTemps = temperaturess.par.map(yearlyAvgs => makeGrid(yearlyAvgs)(gridLoc)).toList
      yearlyTemps.sum / yearlyTemps.length
    }
  }

  /**
    * @param temperatures Known temperatures
    * @param normals      A grid containing the “normal” temperatures
    * @return A grid containing the deviations compared to the normal temperatures
    */
  def deviation(temperatures: Iterable[(Location, Temperature)],
                normals: GridLocation => Temperature): GridLocation => Temperature = {

    gridLoc: GridLocation =>
      makeGrid(temperatures)(gridLoc) - normals(gridLoc)
  }
}

