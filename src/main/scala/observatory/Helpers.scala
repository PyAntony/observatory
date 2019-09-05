package observatory

import com.sksamuel.scrimage.Pixel

import scala.collection.mutable.{Map => MMAP}

object Helpers {
  /** Remove first character if found. */
  def removeFirst(s: String, first: String): String =
    if (s.startsWith(first)) s.tail else s

  /** Round to 2 decimal places. */
  def round(d: Double): Double = (d * 100).round / 100.0

  /** Change a value 'x' between a range to a geographic coordinate. */
  def toCoordinate(x: Int, oldMin: Double, oldMax: Double, axis: String): Double = {
    val newMin = if (axis == "lat") -90 else -180
    val newMax = if (axis == "lat") 90 else 180

    val oldPercent = (x - oldMin) / (oldMax - oldMin)
    (newMax - newMin) * oldPercent + newMin
  }

  /** Generic Memoization API from:
    * https://medium.com/musings-on-functional-programming/
    * scala-optimizing-expensive-functions-with-memoization-c05b781ae826
    */
  def memoizeFnc[K, V](dict: MMAP[K, V])(f: K => V): K => V = {
    k =>
      dict.getOrElse(k, {
        dict.update(k, f(k))
        dict(k)
      })
  }

  /** Convert Color class to a Pixel (com.sksamuel.scrimage.Pixel) */
  def toPixel(c: Color, alpha: Int) = Pixel(c.red, c.green, c.blue, alpha)
}

/** Utility object */
object Timer {
  /** Get the average execution time of a code block.
    * Example: Timer.timeIt(5) { <YOUR_CODE_BLOCK_HERE> }
    *
    * @param reps  number of repetitions. Time average will be computed
    * @param block the code block to wrap with brackets {}
    * @tparam R generic type
    * @return the result of the code block passed
    */
  def timeIt[R](reps: Int)(block: => R): R = {
    val t0 = System.currentTimeMillis()
    // call-by-name
    (1 until reps).foreach(_ => block)
    val result = block

    val t1 = System.currentTimeMillis()
    val elapsed = ((t1 - t0) / reps) * 1.0
    println(f"===> Elapsed time (avg): $elapsed%.6f ms.")
    result
  }
}