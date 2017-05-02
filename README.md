# How to Run

0. Install apche spark (e.g. `brew install apache-spark`)
1. Run `sbt assembly` to generate a jar file
2. Run `./parser-submit.sh` to parse the block files
    - Note: the paths to the block files are hard-coded in the coffee.rory.datparser.Main.scala file and will probably need to be udpated
3. Update `./miner-submit.sh`  with the new paths and run it
