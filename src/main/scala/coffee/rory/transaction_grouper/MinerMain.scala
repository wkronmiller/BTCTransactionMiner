package coffee.rory.transaction_grouper

import java.math.BigInteger

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession

/**
  * Created by wrkronmiller on 4/28/17.
  */
object MinerMain {
  val SPARK_APP_NAME = "TransactionGrouper"
  type AddressArray = Array[BigInteger]
  type TxnId = Long
  type TxnGroupId = Long
  type Address = String
  type InputAddress = Address
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder().appName(SPARK_APP_NAME).getOrCreate()
    val sc = spark.sparkContext
    sc.setLogLevel("ERROR")
    val Array(checkpointDir, sourceDir, sinkDir) = args
    sc.setCheckpointDir(checkpointDir)
    val transactions: RDD[((Array[BigInteger], Array[BigInteger]), TxnGroupId)] = sc.parallelize(sc.textFile(sourceDir).take(200000))
      .map(_.trim).filter(_.size > 0).filter(_.contains(";"))
      .map{transaction =>
        try {
          val Array(inputs, outputs) = s" $transaction ".split(";").map(_.trim.split(",").filter(_.size > 0).map(hex => new BigInteger(hex, 16)))
          Some((inputs, outputs))
        } catch {
          case e: Exception => {
            System.err.println(s"Failed to parse: '$transaction', $e")
            None
          }
        }
      }.filter(_.isDefined).map(_.get)
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

    val transactionGroups = partialGroups
      .flatMap(txns => txns.map(txn =>(txn, txns)))
      .reduceByKey{case (a,b) => a union b}
      //TODO: might need another set of flatMap and reduceByKey operations
      .filter{case (k, v) => k == v.min}

    val flippedTransactions: RDD[(TxnId, (AddressArray, AddressArray))] = transactions.map{case(addrs, txnId) => (txnId, addrs)}

    val groupedTransactions = transactionGroups
      .flatMap{case (groupId, txnIds) =>
        txnIds.map(txnId => (txnId, groupId))
      }
      .rightOuterJoin(flippedTransactions)
      .map{case (txnId, (groupId, (inAddrs, outAddrs))) => (groupId.getOrElse(txnId), (inAddrs.toSet, outAddrs.toSet))}
      // Combine all addresses for group
      .reduceByKey{case ((inOne, outOne), (inTwo, outTwo)) => (inOne ++ inTwo, outOne ++ outTwo)}
      // Remove self-references
      .mapValues{case(inAddrs, outAddrs) => (inAddrs diff outAddrs, outAddrs)}

    // Count group-group references
    val inGroups = groupedTransactions.flatMap{case (groupId, (inputs, _)) => inputs.map(input => (input, groupId))}
    val outGroups = groupedTransactions.flatMap{case (groupId, (_, outputs)) => outputs.map(output => (output, groupId))}

    val groupReferences = outGroups
      .fullOuterJoin(inGroups)
      .map{
        case (_, (Some(outGroup), Some(inGroup))) => (outGroup, Seq((inGroup, 1)))
        case (_, (Some(outGroup), None)) => (outGroup, Seq())
        case (_, (None, Some(inGroup))) => (inGroup, Seq())
      }
      .reduceByKey(_ ++ _)
      .mapValues(_.groupBy(_._1).mapValues(_.map(_._2).reduce(_ + _)).map(identity))
      .mapValues{countMap =>
        val countSum = countMap.values.sum
        countMap.mapValues(_.toDouble / countSum).map(identity)
      }
    .map{case (groupId, countMap) =>
        val countString = countMap.map{case (otherGroup, normedCount) => s"$otherGroup:$normedCount"}.mkString(",")
        s"$groupId;$countString"
    }

    groupReferences.saveAsTextFile(sinkDir)
    sc.stop()
  }
}
