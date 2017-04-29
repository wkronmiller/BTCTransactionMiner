package coffee.rory.transaction_grouper

import java.math.BigInteger

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession

/**
  * Created by wrkronmiller on 4/28/17.
  */
object MinerMain {
  val SPARK_APP_NAME = "TransactionGrouper"
  type StringArray=Array[String]
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder().appName(SPARK_APP_NAME).getOrCreate()
    val sc = spark.sparkContext
    sc.setLogLevel("ERROR")
    val Array(checkpoint_dir, source_dir, sink_dir) = args
    sc.setCheckpointDir(checkpoint_dir)
    val transactions: RDD[((StringArray, StringArray), Long)] = sc.textFile(source_dir)
      .map{transaction =>
          val Array(inputs, outputs) = transaction.split(";").map(_.split(","))
        (inputs, outputs)
      }
      .zipWithUniqueId()
    transactions.take(1).foreach(println)
    sc.stop()
  }
}
