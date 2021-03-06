import NativePackagerHelper._

name := """sxacml-admin"""

version := "1.0-SNAPSHOT"

lazy val module = (project in file("OntoPlay"))
  .enablePlugins(PlayJava)

lazy val root = (project in file("."))
  .enablePlugins(PlayJava)
  .aggregate(module)
  .dependsOn(module)

scriptClasspath := Seq("../conf/", "*")

scalaVersion := "2.11.12"

resolvers += "Local Maven Repository" at Path.userHome.asFile.toURI.toURL+".m2/repository/"

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  guice,
  "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % Test,
  "net.drozdowicz" % "sxacml" % "0.0.1-SNAPSHOT" exclude("org.slf4j", "slf4j-simple")
)

mappings in Universal ++= directory("onto")
mappings in Universal ++= directory("policy")
