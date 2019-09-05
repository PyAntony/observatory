# Functional Programming in Scala Capstone (Coursera)

Repository with implementation to all 6 modules for final capstone (https://www.coursera.org/learn/scala-capstone). Some tips and code snippets are shown here.

### Module 1 - Extraction
Implemented with Spark. I wouldn't recommend trying to implement any other module with Spark. Next modules require highly efficient implementations (a few milliseconds per iteration) to pass the grader tests. Spark creates too much overhead for non-distributed data. Also, be very careful to follow all the requirements when preparing both data sets and specially in your JOIN logic (null keys should also be compared):
 ```scala
 val joined = stations.joinWith(temperatures,
      stations("stnId") <=> temperatures("stnId") &&
        stations("wbanId") <=> temperatures("wbanId")
    )

    /*
    Alternative using string literals:
    import org.apache.spark.sql.Column
    import org.apache.spark.sql.functions._

    def compareColumns(c: String): Column =
       coalesce(stations(c), lit("*")) === coalesce(temperatures(c), lit("*"))

    val joined = stations.joinWith(temperatures,
     compareColumns("stnId") && compareColumns("wbanId")
    )
     */
 ```

### Module 2 - Visualization
Probably the hardest module. You need to implement an algorithm to interpolate Temperatures for every single Location in the map and it has to be FAST; you need to reduce your time to a few (in single digits) milliseconds per temperature interpolated or the grader will throw an annoying Timeout error. Most people suggest (in the forums) to use the closes data points only, not all data points in a single year (using "par" is good enough as long as your implementation is correct). To reduce the number of data points you can use Java ***quickSort*** (fastest way to sort by far) and ***take***. My implementation (without helper methods) is:
```scala
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
 ```
 I am using all distances and still passing all test by just using parallel computation.

### Module 3 - Interaction
Not too difficult once you understand how to generate Location coordinates from Tile coordinates.

### Module 4 - Manipulation
For this module I use memoization at the Grid level only. I use the following helper method as a wrapper:
```scala
def memoizeFnc[K, V](dict: MMAP[K, V])(f: K => V): K => V = {
    k =>
      dict.getOrElse(k, {
        dict.update(k, f(k))
        dict(k)
      })
  }
```
This wrapper is used together with a local Map to cache all computed temperatures:
```scala
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
```
You can use this approach or you could just pre-compute all temperatures.

### Module 5 - Visualization2
The hardest part is the conversion of Location to CellPoint. Other than that the implementation is similar to Interaction. My approach in ***visualizeGrid***:
```scala
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
```
### Module 6 - Interaction2
Implemented using Reactive Programming. Probably the easiest module.
