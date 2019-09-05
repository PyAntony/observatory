package observatory

import java.nio.file.Paths
import java.time.LocalDate

import observatory.{ExtractionHelpers => EH}
import observatory.{Helpers => H}

import org.apache.spark.sql.Dataset
import org.apache.spark.sql.expressions.scalalang.typed
import org.apache.spark.sql.types.StructType
import org.apache.spark.sql.types._

import observatory.Main.spark
import spark.implicits._

/**
  * 1st milestone: data extraction
  */
object Extraction {
  val DEBUG: Boolean = Main.EXTRACTION_DEBUG

  /** Used for testing with grader only!
    *
    * @param year             Year number
    * @param stationsFile     Path of the stations resource file to use (e.g. "/stations.csv")
    * @param temperaturesFile Path of the temperatures resource file to use (e.g. "/1975.csv")
    * @return A sequence containing triplets (date, location, temperature)
    */
  def locateTemperatures(year: Year, stationsFile: String, temperaturesFile: String):
  Iterable[(LocalDate, Location, Temperature)] = {

    val sparkDS = sparkLocateTemps(year, stationsFile, temperaturesFile)
    sparkDS.collect().par.map {
      case (date: MyLocalDate, loc: Location, temp: Temperature) => (date.toJavaLocalDate, loc, temp)
    }.seq
  }

  /** Used for testing with grader only!
    *
    * @param records A sequence containing triplets (date, location, temperature)
    * @return A sequence containing, for each location, the average temperature over the year.
    */
  def locationYearlyAverageRecords(records: Iterable[(LocalDate, Location, Temperature)]):
  Iterable[(Location, Temperature)] = {

    records.par
      .groupBy(_._2)
      .mapValues { iterable =>
        iterable.map(_._3).sum / iterable.size //get the average
      }.seq
  }

  /* ======================================
   *      ^^ SPARK IMPLEMENTATION
   * ======================================
   */

  /** Spark Functional approach implementation. Actions:
    * 1) Read 'stations' and 'temperatures' csv files with schemas.
    * 2) Transform Datasets according to specifications.
    * 3) Join Datasets.
    */
  def sparkLocateTemps(year: Year, stationsFile: String, temperaturesFile: String):
  Dataset[(MyLocalDate, Location, observatory.Temperature)] = {

    val (stationsDs, temperaturesDs) =
      EH.readCsvFiles(stationsFile, temperaturesFile)

    // (1) Stations prep
    val stationsDsFinal: Dataset[Station] = stationsDs
      // a. filter stations WITH latitude and longitude
      .filter((s: Station) => s.latitude.isDefined && s.longitude.isDefined)
      // b. remove the "+" sign from coordinates
      .map { station =>
      val lat = H.removeFirst(station.latitude.get, "+")
      val long = H.removeFirst(station.longitude.get, "+")
      station.copy(latitude = Option(lat), longitude = Option(long))
    }

    // (2) Temperatures prep
    val temperaturesDsFinal: Dataset[TemperatureRead] =
      temperaturesDs
        // a. filter reads WITH temperatures
        .filter((read: TemperatureRead) => read.temp.isDefined && !read.temp.contains(9999.9))
        // b. convert temperatures to Celsius
        .map(read => read.copy(temp = Option(EH.asCelsius(read.temp.get))))
        // c. filter wrong temperatures
        .filter((read: TemperatureRead) => read.temp.get >= -200 && read.temp.get <= 200)

    // (3) Join Datasets
    joinDatasets(stationsDsFinal, temperaturesDsFinal)
      .map {
        // prepare final triplets
        case (station: Station, read: TemperatureRead) => (
          MyLocalDate(year, read.month, read.day),
          Location(station.latitude.get.toDouble, station.longitude.get.toDouble),
          read.temp.get
        )
      }
  }

  /** Join Datasets on ''stnId'' and ''wbanId''. */
  def joinDatasets(stations: Dataset[Station], temperatures: Dataset[TemperatureRead]):
  Dataset[(Station, TemperatureRead)] = {
    // using NULL safe operator <=>
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

    if (DEBUG) {
      joined.persist()
      println("===> Dataset[Station] && Dataset[TemperatureRead] post JOIN:")
      joined.printSchema()
      joined.show()
    }
    joined
  }

  /** Gets the average temperature by location. */
  def sparkAvgTemps(ds: Dataset[(MyLocalDate, Location, Temperature)]):
  Dataset[(Location, Temperature)] = {

    ds.groupByKey(_._2).agg(typed.avg(_._3))
  }

  def sparkExtraction(year: Year, stationsF: String, temperaturesF: String):
  Dataset[(Location, Temperature)] = {

    if (DEBUG) println("\nSTEP -> EXTRACTION...\n")
    sparkAvgTemps(
      sparkLocateTemps(year, stationsF, temperaturesF)
    )
  }
}

/** Class containing helper methods. */
object ExtractionHelpers {
  /** Schema for stations DataFrame. */
  val stationsSchema: StructType =
    new StructType()
      .add("stnId", IntegerType, nullable = true)
      .add("wbanId", IntegerType, nullable = true)
      .add("latitude", StringType, nullable = true)
      .add("longitude", StringType, nullable = true)

  /** Schema for temperatures DataFrame. */
  val tempSchema: StructType =
    new StructType()
      .add("stnId", IntegerType, nullable = true)
      .add("wbanId", IntegerType, nullable = true)
      .add("month", IntegerType, nullable = true)
      .add("day", IntegerType, nullable = true)
      .add("temp", DoubleType, nullable = true)

  /** Read required csv files using the SparkSession.
    *
    * @param stationsFile     resource path. Example: "/stations.csv".
    * @param temperaturesFile resource path. Example: "/1975.csv".
    * @return tuple with both Datasets
    */
  def readCsvFiles(stationsFile: String, temperaturesFile: String):
  (Dataset[Station], Dataset[TemperatureRead]) = {

    if (Extraction.DEBUG) println("Reading csv files...")

    val stationsDs: Dataset[Station] =
      spark.read
        .schema(stationsSchema)
        .csv(fsPath(stationsFile))
        .as[Station]

    val temperaturesDs: Dataset[TemperatureRead] =
      spark.read
        .schema(tempSchema)
        .csv(fsPath(temperaturesFile))
        .as[TemperatureRead]

    if (Extraction.DEBUG) {
      println("CSV files read as Datasets (.as[Case Class])...")
      println(s"====> ($stationsFile) stationsDs Info:")
      stationsDs.printSchema()
      stationsDs.show()
      println(s"====> ($temperaturesFile) temperaturesDs Info:")
      temperaturesDs.printSchema()
      temperaturesDs.show()
    }

    (stationsDs, temperaturesDs)
  }

  /** @return The filesystem path of the given resource */
  def fsPath(resource: String): String =
    Paths.get(getClass.getResource(resource).toURI).toString

  def asCelsius(f: Double): Double = (f - 32) / 1.8
}