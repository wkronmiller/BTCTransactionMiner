organization := "net.kronmiller.william"
version := "0.0.1"
scalaVersion := "2.11.8"

name := "Bitcoin-Parser"

resolvers += "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"

libraryDependencies += "com.typesafe" % "config" % "1.3.0"
//libraryDependencies += "fr.acinq" %% "bitcoin-lib" % "0.9.10"
libraryDependencies += "org.apache.spark" %% "spark-core" % "2.1.0" % "provided"
libraryDependencies += "org.apache.spark" %% "spark-sql" % "2.1.0" % "provided"
libraryDependencies += "org.bitcoinj" % "bitcoinj-core" % "0.14.4"
libraryDependencies += "org.json4s" %% "json4s-jackson" % "3.2.11"
