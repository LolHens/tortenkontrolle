name := "Tortenkontrolle"

lazy val settings = Seq(
  version := "0.1.2",

  scalaOrganization in ThisBuild := "org.typelevel",
  scalaVersion := "2.12.1",

  resolvers := Seq("Artifactory" at "http://lolhens.no-ip.org/artifactory/maven-public/"),

  libraryDependencies ++= Seq(
    "org.scala-lang" % "scala-reflect" % "2.12.1",
    "org.scala-lang.modules" %% "scala-java8-compat" % "0.8.0",
    "org.slf4j" % "slf4j-api" % "1.7.24",
    "ch.qos.logback" % "logback-classic" % "1.2.1",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
    "org.typelevel" %% "cats" % "0.9.0",
    "com.chuusai" %% "shapeless" % "2.3.2",
    "com.github.mpilquist" %% "simulacrum" % "0.10.0",
    "io.monix" %% "monix" % "2.2.3",
    "io.monix" %% "monix-cats" % "2.2.3",
    "org.atnos" %% "eff" % "3.1.0",
    "com.typesafe.akka" %% "akka-actor" % "2.4.17",
    "com.typesafe.akka" %% "akka-remote" % "2.4.17",
    "com.typesafe.akka" %% "akka-stream" % "2.4.17",
    "io.spray" %% "spray-json" % "1.3.3",
    "com.github.fommil" %% "spray-json-shapeless" % "1.3.0",
    "org.scodec" %% "scodec-bits" % "1.1.4",
    "com.github.julien-truffaut" %% "monocle-core" % "1.4.0",
    "com.github.julien-truffaut" %% "monocle-macro" % "1.4.0",
    "com.github.melrief" %% "pureconfig" % "0.6.0",
    "eu.timepit" %% "refined" % "0.7.0",
    "eu.timepit" %% "refined-pureconfig" % "0.7.0",
    "org.lolhens" %% "akka-gpio" % "1.2.0"
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
