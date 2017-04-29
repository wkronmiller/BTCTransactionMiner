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
  type TxnId = Long
  type TxnGroupId = Long
  type Address = String
  type InputAddress = Address
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder().appName(SPARK_APP_NAME).getOrCreate()
    val sc = spark.sparkContext
    sc.setLogLevel("ERROR")
    val Array(checkpoint_dir, source_dir, sink_dir) = args
    sc.setCheckpointDir(checkpoint_dir)
    val transactions: RDD[((StringArray, StringArray), TxnId)] = sc.textFile(source_dir)
      .map{transaction =>
          val Array(inputs, outputs) = transaction.split(";").map(_.split(","))
        (inputs, outputs)
      }
      .zipWithUniqueId()
      .cache()
    // Transactions associated on a per-input basis
    val partialGroups = transactions
      // Mapping from input to transactions it appears in
      .flatMap{case ((inputs, _), txnId) =>
        inputs.map(input => (input, Set(txnId)))
      }
      .reduceByKey(_ ++ _)
      .values

    //NOTE: I think this works as I cannot find a counter-example, but it feels icky
    val transactionGroups = partialGroups
      .cartesian(partialGroups)
      .filter{case (a,b) => a.intersect(b).size > 0}
      .map{case(a,b) =>
        val union = a.union(b)
        (union.min, union)
      }
      .reduceByKey(_ union _)
      .values
      // Just to be safe
      .map(values => (values.min, values))
      .reduceByKey(_ union _)
      .values
      .zipWithUniqueId()

    val flippedTransactions: RDD[(TxnId, (StringArray, StringArray))] = transactions.map{case(addrs, txnId) => (txnId, addrs)}

    val groupedTransactions = transactionGroups
      .flatMap{case (txnIds, groupId) =>
        txnIds.map(txnId => (txnId, groupId))
      }
      .join(flippedTransactions)
      .map{case (_, (groupId, (inAddrs, outAddrs))) => (groupId, (inAddrs.toSet, outAddrs.toSet))}
      // Combine all addresses for group
      .reduceByKey{case ((inOne, outOne), (inTwo, outTwo)) => (inOne ++ inTwo, outOne ++ outTwo)}
      // Remove self-references
      .mapValues{case(inAddrs, outAddrs) => (inAddrs diff outAddrs, outAddrs)}

    // Count group-group references
    val inGroups = groupedTransactions.flatMap{case (groupId, (inputs, _)) => inputs.map(input => (input, groupId))}
    val outGroups = groupedTransactions.flatMap{case (groupId, (_, outputs)) => outputs.map(output => (output, groupId))}

    val groupReferences = outGroups
      .join(inGroups)
      .map{case (_, (outGroup, inGroup)) => (outGroup, Seq((inGroup, 1)))}
      .reduceByKey(_ ++ _)
      .mapValues(_.groupBy(_._1).mapValues(_.map(_._2).reduce(_ + _)).map(identity)) //TODO: normalize counts

    println(s"Group referneces: ${groupReferences.take(100).toList}") //TODO: save results
    sc.stop()
  }
}
