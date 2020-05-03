
name := "math-service"

version := "0.1"

scalaVersion := "2.13.2"

val natchezVersion = "0.0.10"
val http4sVersion = "0.21.0-M6"
val catsVersion = "2.1.0"
val circeVersion = "0.13.0"

libraryDependencies += "org.tpolecat" %% "natchez-jaeger" % natchezVersion
libraryDependencies += "org.tpolecat" %% "natchez-core" % natchezVersion

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-blaze-server" % http4sVersion,
  "org.http4s" %% "http4s-blaze-client" % http4sVersion
)

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-core"   % catsVersion,
  "org.typelevel" %% "cats-effect" % catsVersion
)

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-server" % http4sVersion,
  "org.http4s" %% "http4s-prometheus-metrics" % http4sVersion
)

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-circe" % http4sVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-literal" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion
)

addCompilerPlugin("org.typelevel" % "kind-projector_2.13.1" % "0.11.0")

assemblyJarName in assembly := s"app.jar"
mainClass in assembly := Some("org.parseq.mathservice.Main")

//mainClass in Compile := Some("org.parseq.mathservice.Main")

//dockerBaseImage := "openjdk:jre-alpine"
//enablePlugins(DockerPlugin)
//enablePlugins(AshScriptPlugin)
//enablePlugins(JavaAppPackaging)
