name := "Tortenkontrolle"

lazy val settings = Seq(
  version := "0.1.4",

  scalaOrganization in ThisBuild := "org.typelevel",
  scalaVersion := "2.12.1",

  resolvers := Seq("Artifactory" at "http://lolhens.no-ip.org/artifactory/maven-public/"),

  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor" % "2.5.0",
    "org.scodec" %% "scodec-bits" % "1.1.4",
    "org.lolhens" %% "akka-gpio" % "1.3.5"
  ),

  addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
  addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.3"),

  mainClass in Compile := Some("org.lolhens.piectrl.Main"),

  dependencyUpdatesExclusions := moduleFilter(organization = "org.scala-lang"),

  scalacOptions ++= Seq("-Xmax-classfile-name", "254")
)

lazy val root = project.in(file("."))
  .enablePlugins(
    JavaAppPackaging,
    UniversalPlugin)
  .settings(settings: _*)
