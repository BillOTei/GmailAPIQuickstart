ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.1.3"

lazy val root = (project in file("."))
  .settings(
    name := "BookingEmailer"
  )

libraryDependencies += "dev.zio" %% "zio" % "2.0.0"
libraryDependencies += "dev.zio" %% "zio-streams" % "2.0.0"
libraryDependencies += "com.google.api-client" % "google-api-client" % "1.35.2"
libraryDependencies += "com.google.oauth-client" % "google-oauth-client-jetty" % "1.34.1"
libraryDependencies += "com.google.apis" % "google-api-services-gmail" % "v1-rev20220404-1.32.1"
