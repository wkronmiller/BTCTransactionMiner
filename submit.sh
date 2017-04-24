#!/bin/bash
spark-submit --class "coffee.rory.Main" --master local[*] --driver-memory 5g --executor-memory 6g /root/BTCTransactionMiner/target/scala-2.11/Bitcoin-Parser-assembly-0.0.1.jar
