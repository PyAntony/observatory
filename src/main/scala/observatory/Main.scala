package observatory

import org.apache.spark.sql.{Dataset, SparkSession}
import org.apache.log4j.{Level, Logger}

object Main {
  // Set spark Logger level
  Logger.getLogger("org.apache.spark").setLevel(Level.WARN)

  val EXTRACTION_DEBUG = false

  val spark: SparkSession =
    SparkSession
      .builder()
      .appName("Scala-Capstone")
      .config("spark.master", "local[*]")
      .getOrCreate()

  /** Main function */
  def main(args: Array[String]): Unit = {

    // TODO: Generate all tiles

    spark.stop()
  }

}
