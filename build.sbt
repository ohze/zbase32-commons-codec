val publishSettings = Seq(
  publishTo := sonatypePublishTo.value,
  publishMavenStyle := true,
  pomExtra :=
    <url>https://github.com/ohze/zbase32-commons-codec</url>
      <licenses>
        <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0</url>
        </license>
      </licenses>
      <scm>
        <url>https://github.com/ohze/zbase32-commons-codec</url>
        <connection>scm:git@github.com:ohze/zbase32-commons-codec.git</connection>
      </scm>
      <developers>
        <developer>
          <id>giabao</id>
          <name>Gia Bảo</name>
          <email>giabao@sandinh.net</email>
          <organization>Sân Đình</organization>
          <organizationUrl>https://sandinh.com</organizationUrl>
        </developer>
      </developers>
)

val commonSettings = Seq(
  version := "1.0.0",
  scalaVersion := "2.12.6",
  organization := "com.sandinh",
  scalacOptions := Seq("-encoding", "UTF-8", "-deprecation", "-target:jvm-1.8")
)

lazy val zbase32 = (project in file("."))
  .settings(
    name := "zbase32-commons-codec",
    libraryDependencies ++= Seq(
      "commons-codec" % "commons-codec" % "1.11",
      "org.scalatest" %% "scalatest" % "3.0.5" % Test
    ),
  )
  .settings(commonSettings ++ publishSettings: _*)
