import mill._, scalalib._
import mill.scalalib.TestModule.ScalaTest

trait CommonModule extends ScalaModule { m =>
  def scalaVersion = "2.13.8"

  override def scalacOptions = Seq(
    "-language:reflectiveCalls",
    "-deprecation",
    "-feature",
    "-Xcheckinit",
    "-P:chiselplugin:genBundleElements"
  )

  override def ivyDeps = Agg(
    ivy"edu.berkeley.cs::chisel3:3.5.4",
  )

  override def scalacPluginIvyDeps = Agg(
    ivy"edu.berkeley.cs:::chisel3-plugin:3.5.4",
  )

  object test extends Tests with ScalaTest {
    override def ivyDeps = m.ivyDeps() ++ Agg(
      ivy"edu.berkeley.cs::chiseltest:0.5.4"
    )
  }
}

object arcadia extends CommonModule
object cave extends CommonModule {
  def moduleDeps = Seq(arcadia)
}
