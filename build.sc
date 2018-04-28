import mill._, scalalib._, publish._

trait PublishBased extends PublishModule {
  def publishVersion = "1.0.0"
  def pomSettings = PomSettings(
    description = "java zbase32 based on commons-codec",
    organization = "com.sandinh",
    url = "https://github.com/ohze/zbase32-commons-codec",
    licenses = Seq(License.MIT),
    versionControl = VersionControl.github("ohze", "zbase32-commons-codec"),
    developers = Seq(
      Developer("ohze", "Bui Viet Thanh", "https://github.com/ohze")
    )
  )
}

trait ScalaBased extends ScalaModule {
  def scalaVersion = "2.12.5"
  override def scalacOptions = T {
    Seq("-encoding", "UTF-8", "-deprecation", "-target:jvm-1.8")
  }
}

object zbase32 extends PublishBased with JavaModule {
  override def artifactName = T{"zbase32-commons-codec"}

  override def ivyDeps = Agg(ivy"commons-codec:commons-codec:1.11")

  object test extends Tests with ScalaBased {
    override def ivyDeps = Agg(ivy"org.scalatest::scalatest:3.0.5")
    def testFrameworks = Seq("org.scalatest.tools.Framework")
  }
}
