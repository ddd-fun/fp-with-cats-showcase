name := """fp-with-cats-showcase"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.11"

libraryDependencies ++= Seq(
  ws,
  filters,
  "org.typelevel" %% "cats" % "0.9.0",
  "io.circe" %% "circe-core" % "0.8.0",
  "io.circe" %% "circe-generic" % "0.8.0",
  "io.circe" %% "circe-parser" % "0.8.0",
  "io.circe" %% "circe-optics" % "0.8.0",
  "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test,
  "org.scalatest" %% "scalatest" % "2.2.6" % Test,
  "org.scalacheck" %% "scalacheck" % "1.13.4" % Test,
  "com.github.tomakehurst" % "wiremock" % "1.33",
  "co.fs2" %% "fs2-core" % "0.10.0-RC1",
  "co.fs2" %% "fs2-io" % "0.10.0-RC1"
)

routesImport ++= Seq("extensions.Binders._")
