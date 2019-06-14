val scalaV = "2.12.8"

name := "emarsys-client-scala-sdk"
organization := "com.emarsys"
scalaVersion := scalaV

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding",
  "UTF-8",
  "-unchecked",
  "-feature",
  "-language:implicitConversions",
  "-language:postfixOps",
  "-Ywarn-dead-code",
  "-Xlint",
  "-Xfatal-warnings"
)

libraryDependencies ++= {
  val akkaHttpV  = "10.0.11"
  val scalaTestV = "3.0.4"
  Seq(
    "com.typesafe.akka"     %% "akka-http-core"       % akkaHttpV,
    "com.typesafe.akka"     %% "akka-http"            % akkaHttpV,
    "com.typesafe.akka"     %% "akka-http-spray-json" % akkaHttpV,
    "com.typesafe.akka"     %% "akka-http-testkit"    % akkaHttpV % Test,
    "com.github.fommil"     %% "spray-json-shapeless" % "1.4.0",
    "org.scalatest"         %% "scalatest"            % scalaTestV % Test,
    "com.emarsys"           %% "escher-akka-http"     % "1.0.4",
    "joda-time"             % "joda-time"             % "2.9.1",
    "org.joda"              % "joda-convert"          % "2.0.1",
    "com.github.pureconfig" %% "pureconfig"           % "0.9.1"
  )
}

addCompilerPlugin("io.tryp" % "splain" % "0.4.1" cross CrossVersion.patch)

scalacOptions in (Compile, console) --= Seq("-Ywarn-unused:imports", "-Xfatal-warnings")

scalaVersion in ThisBuild := scalaV

inThisBuild(
  List(
    licenses := Seq("MIT" -> url("http://opensource.org/licenses/MIT")),
    homepage := Some(url("https://github.com/emartech/emarsys-client-scala-sdk")),
    developers := List(
      Developer("andrasp3a", "Andras Papp", "andras.papp@emarsys.com", url("https://github.com/andrasp3a")),
      Developer("bkiss1988", "Balazs Kiss", "balazs.kiss@emarsys.com", url("https://github.com/bkiss1988")),
      Developer("doczir", "Robert Doczi", "doczi.r@gmail.com", url("https://github.com/doczir")),
      Developer("handriss", "Andras Hinkel", "andras.hinkel@emarsys.com", url("https://github.com/handriss")),
      Developer("itsdani", "Daniel Segesdi", "daniel.segesdi@emarsys.com", url("https://github.com/itsdani")),
      Developer("jupposessho", "Vilmos Feher", "vilmos.feher@emarsys.com", url("https://github.com/jupposessho")),
      Developer("Ksisu", "Kristof Horvath", "kristof.horvath@emarsys.com", url("https://github.com/Ksisu")),
      Developer("mfawal", "Margit Fawal", "margit.fawal@emarsys.com", url("https://github.com/mfawal")),
      Developer("miklos-martin", "Miklos Martin", "miklos.martin@gmail.com", url("https://github.com/miklos-martin")),
      Developer("stsatlantis", "Barnabas Olah", "stsatlantis@gmail.com", url("https://github.com/stsatlantis")),
      Developer("suliatis", "Attila Suli", "attila.suli@emarsys.com", url("https://github.com/suliatis")),
      Developer("tg44", "Gergo Torcsvari", "gergo.torcsvari@emarsys.com", url("https://github.com/tg44")),
      Developer("kartonfarkas", "Gabor Manner", "gabor.manner@emarsys.com", url("https://github.com/kartonfarkas")),
      Developer("dani3lb", "Daniel Balazs", "daniel.balazs@emarsys.com", url("https://github.com/dani3lb"))
    ),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/emartech/emarsys-client-scala-sdk"),
        "scm:git:git@github.com:emartech/emarsys-client-scala-sdk.git"
      )
    ),
    // These are the sbt-release-early settings to configure
    pgpPublicRing := file("./ci/local.pubring.asc"),
    pgpSecretRing := file("./ci/local.secring.asc"),
    releaseEarlyWith := SonatypePublisher
  )
)

fork in Test := true
