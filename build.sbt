organization := "net.kronmiller.william"
version := "0.0.1"
scalaVersion := "2.11.8"

name := "Bitcoin-Parser"

resolvers += "Central" at "http://central.maven.org/maven2/"
resolvers += "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
resolvers += "Secured Central Repository" at "https://repo1.maven.org/maven2"
externalResolvers := Resolver.withDefaultResolvers(resolvers.value, mavenCentral = false)

libraryDependencies += "com.typesafe" % "config" % "1.3.0"
//libraryDependencies += "fr.acinq" %% "bitcoin-lib" % "0.9.10"
libraryDependencies += "org.apache.spark" %% "spark-core" % "2.1.0" % "provided"
libraryDependencies += "org.apache.spark" %% "spark-sql" % "2.1.0" % "provided"
libraryDependencies += "org.bitcoinj" % "bitcoinj-core" % "0.14.4"
libraryDependencies += "org.json4s" %% "json4s-jackson" % "3.2.11"

// S3 Support
libraryDependencies += "org.apache.hadoop" % "hadoop-aws" % "2.7.3"

// Copied from SO: http://stackoverflow.com/questions/30446984/spark-sbt-assembly-deduplicate-different-file-contents-found-in-the-followi
mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
  {
    case PathList("javax", "servlet", xs @ _*) => MergeStrategy.last
    case PathList("javax", "activation", xs @ _*) => MergeStrategy.last
    case PathList("org", "apache", xs @ _*) => MergeStrategy.last
    case PathList("com", "google", xs @ _*) => MergeStrategy.last
    case PathList("com", "esotericsoftware", xs @ _*) => MergeStrategy.last
    case PathList("com", "codahale", xs @ _*) => MergeStrategy.last
    case PathList("com", "yammer", xs @ _*) => MergeStrategy.last
    case "about.html" => MergeStrategy.rename
    case "META-INF/ECLIPSEF.RSA" => MergeStrategy.last
    case "META-INF/mailcap" => MergeStrategy.last
    case "META-INF/mimetypes.default" => MergeStrategy.last
    case "plugin.properties" => MergeStrategy.last
    case "log4j.properties" => MergeStrategy.last
    case x => old(x)
  }
}
