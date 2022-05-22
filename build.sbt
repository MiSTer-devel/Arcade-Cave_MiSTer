ThisBuild / version      := "0.1.0"

ThisBuild / scalaVersion := "2.13.8"

val chiselVersion = "3.5.3"

lazy val settings = Seq(
  libraryDependencies ++= Seq(
    "edu.berkeley.cs" %% "chisel3" % chiselVersion,
    "edu.berkeley.cs" %% "chiseltest" % "0.5.1" % "test"
  ),
  scalacOptions ++= Seq(
    "-language:reflectiveCalls",
    "-deprecation",
    "-feature",
    "-Xcheckinit",
    "-P:chiselplugin:genBundleElements",
  ),
  addCompilerPlugin("edu.berkeley.cs" % "chisel3-plugin" % chiselVersion cross CrossVersion.full),
)

lazy val axon = (project in file("axon"))
  .settings(
    settings,
    name := "axon"
  )


lazy val root = (project in file("."))
  .aggregate(axon)
  .dependsOn(axon)
  .settings(
    settings,
    name := "cave"
  )
