val scalaV = "2.12.2"

name         := "emarsys-client-scala-sdk"
organization := "com.emarsys"
version      := "0.1.4"
scalaVersion := scalaV

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-unchecked",
  "-feature",
  "-language:implicitConversions",
  "-language:postfixOps",
  "-Ywarn-dead-code",
  "-Xlint",
  "-Xfatal-warnings"
)

resolvers += "escher-akka-http on GitHub" at "https://raw.github.com/emartech/escher-akka-http/master/releases"

libraryDependencies ++= {
  val akkaHttpV   = "10.0.5"
  val scalaTestV  = "3.0.1"
  Seq(
    "com.typesafe.akka"     %% "akka-http-core"       % akkaHttpV,
    "com.typesafe.akka"     %% "akka-http"            % akkaHttpV,
    "com.typesafe.akka"     %% "akka-http-spray-json" % akkaHttpV,
    "com.github.fommil"     %% "spray-json-shapeless" % "1.3.0",
    "org.scalatest"         %% "scalatest"            % scalaTestV % "test",
    "com.emarsys"           %% "escher-akka-http"     % "0.1.0",
    "joda-time"             %  "joda-time"            % "2.9.1",
    "com.github.pureconfig" %% "pureconfig"           % "0.7.2"
  )
}

publishTo := Some(Resolver.file("releases", new File("releases")))
scalaVersion in ThisBuild := scalaV
