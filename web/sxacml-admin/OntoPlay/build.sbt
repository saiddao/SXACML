name := """OntoPlay"""

version := "1.1-SNAPSHOT"

scalaVersion := "2.11.12"

lazy val module = (project in file("."))
    .enablePlugins(PlayJava).settings(
      watchSources ++= (baseDirectory.value / "public/ui" ** "*").get
    )


libraryDependencies ++= Seq(
  // javaJdbc,
  // cache,
  // javaWs,
  // https://mvnrepository.com/artifact/jakarta.xml.bind/jakarta.xml.bind-api
  "jakarta.xml.bind" % "jakarta.xml.bind-api" % "2.3.2",
  "com.google.code.gson" % "gson" % "2.2.2",
  "commons-io" % "commons-io" % "2.4",
  "net.sourceforge.owlapi" % "owlapi-distribution" % "[5.1.3]",
  "com.github.galigator.openllet" % "openllet-owlapi" % "2.6.2" excludeAll(
    ExclusionRule(organization = "org.slf4j", name = "slf4j-api"),
    ExclusionRule(organization = "org.slf4j", name = "slf4j-simple")
  ),
  "com.github.galigator.openllet" % "openllet-jena" % "2.6.2" excludeAll(
    ExclusionRule(organization = "org.slf4j", name = "slf4j-api"),
    ExclusionRule(organization = "org.slf4j", name = "slf4j-simple")
  ),
//  "com.hermit-reasoner" % "org.semanticweb.hermit" % "1.3.8.4",

  "org.easytesting" % "fest-assert" % "1.4" % "test",
  "xmlunit" % "xmlunit" % "1.6" % "test",
  "org.scala-lang.modules" % "scala-java8-compat_2.11" % "0.8.0"
)

testOptions in Test := Seq(Tests.Argument(TestFrameworks.JUnit, "-a", "-v"))
