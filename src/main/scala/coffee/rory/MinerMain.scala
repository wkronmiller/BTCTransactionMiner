package coffee.rory

import org.apache.spark.sql.SparkSession

/**
  * Created by wrkronmiller on 4/28/17.
  */
object MinerMain {
  val SPARK_APP_NAME = "TransactionGrouper"
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder().appName(SPARK_APP_NAME).getOrCreate()
    val sc = spark.sparkContext
    val Array(checkpoint_dir, source_dir, sink_dir) = args
    sc.setCheckpointDir(checkpoint_dir)
    sc.textFile(source_dir).take(2).foreach(println)
    sc.stop()
  }
}
