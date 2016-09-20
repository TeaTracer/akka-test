    name := "JQEstate Test"

    version := "0.1"

    scalaVersion := "2.11.8"

    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-http-experimental" % "2.4.6",
        "com.typesafe.akka" %% "akka-stream" % "2.4.6",
        "io.circe" %% "circe-core" % "0.4.1",
        "io.circe" %% "circe-parser" % "0.4.1"
    )
