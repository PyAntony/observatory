package observatory

import com.sksamuel.scrimage.Image

import scala.collection.parallel.ParIterable
import scala.math._

import observatory.{Helpers => H}

/**
  * 2nd milestone: basic visualization
  */
object Visualization {
  val EARTH_RADIUS: Int = 6356 // in Km

  /*
   * predictTemperature method helpers
   */

  /** Get the antipodal of a geo coordinate. */
  def getAntipodal(x: Location): Location = {
    val lowerBound = x.lon - 180
    val newLongitude = if (lowerBound >= -180) lowerBound else lowerBound + 360

    x.copy(lat = -1 * x.lat, lon = newLongitude)
  }

  /** Implements the Great-circle distance formula to find the
    * distance between 2 Locations in kilometers. First formula in
    * https://en.wikipedia.org/wiki/Great-circle_distance.
    */
  def getDistance(x: Location, x_i: Location): Double = {

    if (getAntipodal(x_i) == x)
      Pi * EARTH_RADIUS
    else {
      val λ1 = toRadians(x.lon)
      val ϕ1 = toRadians(x.lat)
      val λ2 = toRadians(x_i.lon)
      val ϕ2 = toRadians(x_i.lat)
      val Δλ = abs(λ1 - λ2)

      val Δσ = acos(sin(ϕ1) * sin(ϕ2) + cos(ϕ1) * cos(ϕ2) * cos(Δλ))
      Δσ * EARTH_RADIUS
    }
  }

  /** Inverse Distance Weighting:
    * w_i(location) = 1 / distance(location, location_i)**p
    */
  def getWeight(dist: Double, p: Int = 2): Double = pow(1 / dist, p)

  /** Implements the IDW formula from
    * https://en.wikipedia.org/wiki/Inverse_distance_weighting
    */
  def getInverseDistanceWeight(pairs: ParIterable[(Double, Temperature)]): Double = {
    val reduced: (Double, Double) = pairs.map {
      case (d: Double, t: Temperature) => (getWeight(d) * t, getWeight(d))
    }.aggregate((0.0, 0.0))(
      (accTp, tp) =>
        (accTp._1 + tp._1, accTp._2 + tp._2),
      (accTp1, accTp2) =>
        (accTp1._1 + accTp2._1, accTp1._2 + accTp2._2)
    )

    reduced._1 / reduced._2
  }

  /** Interpolates a single temperature given all known temperatures in map.
    *
    * @param temperatures Known temperatures: pairs containing a location and the temperature at this location
    * @param location     Location where to predict the temperature
    * @return The predicted temperature at `location`
    */
  def predictTemperature(temperatures: Iterable[(Location, Temperature)], location: Location):
  Temperature = {

    // calculate distances
    val distanceTempPairs: ParIterable[(Double, Temperature)] =
      temperatures.par.map {
        case (l: Location, t: Temperature) => (getDistance(location, l), t)
      }

    /*
    Optional implementation using quicksort and fewer distances:
    val arrTest = distanceTempPairs.toArray
    scala.util.Sorting.quickSort(arrTest)
    val arrTest2 = arrTest.take(40)
     */

    // No interpolation needed if distance < 1km.
    val closeP = distanceTempPairs.filter(_._1 < 1).take(1)
    if (closeP.nonEmpty) closeP.head._2
    // else implement the Inverse Distance Weighting formula
    else getInverseDistanceWeight(distanceTempPairs)
  }

  /** Interpolate color given a temperature and a temperature-color scale.
    *
    * @param points Pairs containing a value and its associated color
    * @param value  The value to interpolate
    * @return The color that corresponds to `value`, according to the color scale defined by `points`
    */
  def interpolateColor(points: Iterable[(Temperature, Color)], value: Temperature): Color = {
    type TempColorPair = (Temperature, Color)
    val sortedPoints = points.toSeq.sortBy(_._1).distinct

    def findRange(previous: TempColorPair, ls: Iterable[TempColorPair]):
    (TempColorPair, TempColorPair) =
      ls match {
        case Nil => (previous, previous)
        case h :: _ if value == h._1 => (h, h)
        case h :: _ if value < h._1 => (previous, h)
        case _ => findRange(ls.head, ls.tail)
      }

    def getFactoredVal(l: Int, r: Int, factor: Double): Int = {
      if (l == r) l
      else if (l < r) Math.round(((r - l) * factor) + l).toInt
      else Math.round((l - r) - (factor * l)).toInt
    }

    //-----------------------------------------------------------
    // find the range where the temperature is found
    val ((leftTmp, leftRgb), (rightTmp, rightRgb)) =
    findRange(sortedPoints.head, sortedPoints) match {
      case (t1: TempColorPair, t2: TempColorPair) =>
        ((t1._1, t1._2), (t2._1, t2._2))
    }
    // upper and lower boundaries are the same
    if (leftTmp == rightTmp) leftRgb
    else {
      val factor = ((value - leftTmp) / (rightTmp - leftTmp)) * 1.0
      Color(
        red = getFactoredVal(leftRgb.red, rightRgb.red, factor),
        green = getFactoredVal(leftRgb.green, rightRgb.green, factor),
        blue = getFactoredVal(leftRgb.blue, rightRgb.blue, factor)
      )
    }
  }

  /**
    * @param temperatures Known temperatures
    * @param colors       Color scale
    * @return A 360×180 image where each pixel shows the predicted temperature at its location
    */
  def visualize(temperatures: Iterable[(Location, Temperature)],
                colors: Iterable[(Temperature, Color)]): Image = {
    val imageWidth = 360
    val imageHeight = 180

    def toLocation(p: Int): Location =
      Location(90 - (p / imageWidth), (p % imageWidth) - imageHeight)

    val allLocations = (0 until (imageHeight * imageWidth)).par.map(toLocation)

    val allColors: Seq[Color] = allLocations
      .map(loc => (loc, predictTemperature(temperatures, loc))) //interpolate temperatures
      .map(tp => (tp._1, interpolateColor(colors, tp._2))) //interpolate colors
      .seq
      .sortBy(-_._1.lat) // latitude in reverse order: (90, -180), (90, -179), ...
      .map(_._2)

    Image(
      imageWidth,
      imageHeight,
      allColors.map(H.toPixel(_, 255)).toArray
    )
  }

}

