val scalaV = "2.12.6"

name         := "emarsys-client-scala-sdk"
organization := "com.emarsys"
version      := "0.4.2"
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
  val akkaHttpV   = "10.0.11"
  val scalaTestV  = "3.0.4"
  Seq(
    "com.typesafe.akka"     %% "akka-http-core"       % akkaHttpV,
    "com.typesafe.akka"     %% "akka-http"            % akkaHttpV,
    "com.typesafe.akka"     %% "akka-http-spray-json" % akkaHttpV,
    "com.github.fommil"     %% "spray-json-shapeless" % "1.4.0",
    "org.scalatest"         %% "scalatest"            % scalaTestV % "test",
    "com.emarsys"           %% "escher-akka-http"     % "1.0.1",
    "joda-time"             %  "joda-time"            % "2.9.1",
    "org.joda"              %  "joda-convert"         % "2.0.1",
    "com.github.pureconfig" %% "pureconfig"           % "0.9.1"
  )
}

addCompilerPlugin("io.tryp" % "splain" % "0.3.1" cross CrossVersion.patch)

scalacOptions in (Compile, console) --= Seq("-Ywarn-unused:imports", "-Xfatal-warnings")

publishTo := Some(Resolver.file("releases", new File("releases")))
scalaVersion in ThisBuild := scalaV
fork in Test := true
