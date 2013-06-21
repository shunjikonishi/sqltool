import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName         = "sqltool"
  val appVersion      = "1.0-SNAPSHOT"

  val appDependencies = Seq(
    // Add your project dependencies here,
    jdbc,
    anorm,
    "com.google.code.gson" % "gson" % "2.2.2",
//    "log4j" % "log4j" % "1.2.17",
    "org.apache.poi" % "poi" % "3.9",
    "org.apache.poi" % "poi-ooxml" % "3.9",
    "postgresql" % "postgresql" % "9.2-1002.jdbc4"
  )


  val main = play.Project(appName, appVersion, appDependencies).settings(
    // Add your own project settings here      
    resolvers += "Typesafe Repository 2" at "http://repo.typesafe.com/typesafe/repo/"
  )

}

