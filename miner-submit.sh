#!/bin/bash
SOURCE_DIR=/mnt/ebsmag1/TransactionParser3
SINK_DIR=/mnt/ebsmag1/groups
#SINK_DIR=s3a://wrkronmiller-public/bitcoin/groups
CHECKPOINT_DIR=/mnt/ebsmag1/checkpoints
sbt assembly && clear && time spark-submit --class "coffee.rory.transaction_grouper.MinerMain" \
    --master local[*] \
    --driver-memory 100g \
    --executor-memory 300g \
	--conf "spark.reducer.maxSizeInFlight=10g" \
	--conf "spark.shuffle.file.buffer=30g" \
    target/scala-2.11/Bitcoin-Parser-assembly-0.0.1.jar $CHECKPOINT_DIR $SOURCE_DIR $SINK_DIR 
