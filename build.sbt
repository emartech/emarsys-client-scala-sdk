organization       := "com.emarsys"
name               := "emarsys-client-scala-sdk"
crossScalaVersions := List("2.13.8", "2.12.15")

scalacOptions := scalacOptionsFor(scalaVersion.value)

libraryDependencies ++= {
  val akkaV      = "2.6.19"
  val akkaHttpV  = "10.2.9"
  val scalaTestV = "3.2.12"
  Seq(
    "com.typesafe.akka"     %% "akka-http-core"       % akkaHttpV,
    "com.typesafe.akka"     %% "akka-http"            % akkaHttpV,
    "com.typesafe.akka"     %% "akka-http-spray-json" % akkaHttpV,
    "com.typesafe.akka"     %% "akka-http-testkit"    % akkaHttpV  % Test,
    "com.typesafe.akka"     %% "akka-stream-testkit"  % akkaV      % Test,
    "org.scalatest"         %% "scalatest"            % scalaTestV % Test,
    "com.emarsys"           %% "escher-akka-http"     % "1.3.19",
    "joda-time"              % "joda-time"            % "2.10.14",
    "org.joda"               % "joda-convert"         % "2.2.2",
    "com.github.pureconfig" %% "pureconfig"           % "0.17.1"
  )
}

scalacOptions in (Compile, console) --= Seq("-Ywarn-unused:imports", "-Xfatal-warnings")

Global / onChangedBuildSource := ReloadOnSourceChanges

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
    )
  )
)

fork in Test := true

def scalacOptionsFor(scalaVersion: String) =
  Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-unchecked",
    "-feature",
    "-language:implicitConversions",
    "-language:postfixOps",
    "-Ywarn-dead-code",
    "-Xlint"
  ) ++ (if (is2_12(scalaVersion))
          Seq(
            "-Xfatal-warnings"
          )
        else Seq())

def is2_12(scalaVersion: String): Boolean =
  CrossVersion.partialVersion(scalaVersion) match {
    case Some((2, 12)) => true
    case _             => false
  }
