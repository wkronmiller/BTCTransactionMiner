package coffee.rory.datparser

import java.io.File

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.bitcoinj.core.{Address, Base58, Block, Context, NetworkParameters, ScriptException, Transaction => JTransaction}
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.utils.BlockFileLoader
import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization

import scala.collection.JavaConverters._

case class Transaction(inputs: Set[Address], outputs: Set[Address]) {
  private def extractAddress(address: Address): String = {
    Base58.decodeToBigInteger(address.toBase58).toString(16)
  }
  def toJSON: String = {
    implicit val formats = Serialization.formats(NoTypeHints)
    val map = ("inputs" -> inputs.map(extractAddress).toList, "outputs" -> outputs.map(extractAddress).toList)
    Serialization.write(map)
  }
	def toIOString: String = {
		s"${inputs.map(extractAddress).toList.mkString(",")};${outputs.map(extractAddress).toList.mkString(",")}"
	} 
}

/**
  * Extract useeful transaction information from a BTC Block
  * @param blockIterator
  * @param netParams
  */
class TransactionIterator(blockIterator: Iterator[Block], netParams: NetworkParameters) extends Iterator[Set[Transaction]] {
  override def hasNext: Boolean = blockIterator.hasNext

  private def extractInputs(txn: JTransaction): Iterable[Address] = {
    txn.getInputs().asScala
      .map{input =>
        try{
          Some(input.getFromAddress)
        } catch {
          case e: ScriptException => None
        }
      }
      .filter(_.isDefined)
      .map(_.get)
  }

  private def extractOutputs(txn: JTransaction) = {
    txn.getOutputs.asScala.flatMap{output=>
      try {
        Seq(output.getAddressFromP2SH(netParams), output.getAddressFromP2PKHScript(netParams))
      } catch {
        case e: Exception => {
          System.err.println(s"Failed to process transaction $txn")
          null
        }
      }
    }.filterNot(_ == null)
  }

  override def next(): Set[Transaction] = {
    val block = blockIterator.next()
    block
      .getTransactions.asScala
      .filterNot(_ == null)
      .map{ txn =>
        try {
          val inAddrs = extractInputs(txn)
          val outAddrs = extractOutputs(txn)
          Transaction(inAddrs.toSet, outAddrs.toSet)
        } catch {
          case e: Exception => {
            System.err.println(s"Failed to process transaction $txn")
            null
          }
        }
      }
      .filterNot(_ == null)
      .toSet
  }
}

object Main {
  val SPARK_APP_NAME="TransactionParser3"
  //val DRIVE_PATH="/Volumes/Seagate Backup Plus Drive"
  val DRIVE_PATH="/root/Bitcoin"
  val DAT_DIR=s"$DRIVE_PATH/datfiles"
  val OUT_DIR = s"$DRIVE_PATH/json/$SPARK_APP_NAME"
  val CHECKPOINT_DIR = s"$DRIVE_PATH/checkpoints"
  val DAT_EXTENSION=".dat"
  def getBlockFilePaths: Array[String] = {
    new File(DAT_DIR)
      .listFiles()
      .map(_.getAbsolutePath)
      .filter(_.endsWith(DAT_EXTENSION))
  }
  /**
    * Runs on every spark partition
    * @param blockPaths
    * @return
    */
  def loadBlocks(blockPaths: Iterator[String]): Iterator[Transaction] = {
    val netParams = new MainNetParams()
    val _ = new Context(netParams)
    val blockLoader = new BlockFileLoader(netParams, blockPaths.map(new File(_)).toList.asJava)
    val transactionIterator = new TransactionIterator(blockLoader.iterator().asScala, netParams)
    transactionIterator.flatten
  }
  def main(args: Array[String]) = {
    val spark = SparkSession.builder().appName(SPARK_APP_NAME).getOrCreate()
    val sc = spark.sparkContext
    sc.setCheckpointDir(CHECKPOINT_DIR)
    val blockFilePaths: RDD[String] = sc.parallelize(getBlockFilePaths)

    blockFilePaths.checkpoint()
    val jsonTransactions = blockFilePaths
      .repartition(10)
      .mapPartitions(loadBlocks)
      .map(_.toIOString)

    jsonTransactions.checkpoint()
    jsonTransactions
    .saveAsTextFile(OUT_DIR)

    spark.stop()
  }
}
