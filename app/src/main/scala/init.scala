package ls

import java.io.{File,FileWriter}

object LsInit {
  def main(args: Array[String]) {
    val exit = run(args)
    System.exit(exit)
  }
  def run(args: Array[String]): Int = {
    val base = new File(args.headOption.getOrElse("."))
    val result = if (base.isDirectory) {
      lsVersion match {
        case Some(version) =>
          setup(base, version)
        case None =>
          Left("Unable to retrive current ls version")
      }
    } else {
      Left("Directory not found: " + base.getCanonicalPath)
    }
    result.fold(
      { err => println(err); 1 },
      { msg => println(msg); 0 }
    )
  }
  def lsVersion = {
    import dispatch._
    val req = :/("ls.implicit.ly") / "api" / "1" / "latest" / "ls-sbt"
    val http = new Http with NoLogging
    try {
      http(req >- { js =>
        scala.util.parsing.json.JSON.parseFull(js).map {
          _.asInstanceOf[Map[String,Any]]("version").toString
        }
      })
    } catch {
      case e => Some(e.getMessage)
    } finally {
      http.shutdown()
    }
  }
  def setup(base: File, version: String) = {
    val build = new File(base, "build.sbt")
    val plugin = new File(base, "project/plugins.sbt")
    val files = build :: plugin :: Nil
    def stat(f: File) =
      "%s: %s".format(
        if (f.exists) "Updated" else "Created",
        f.getCanonicalPath
      )
    val msg = files.map(stat).mkString("\n")
    files.foreach { f =>
      new File(f.getParent).mkdirs()
    }
    def write(f: File)(block: FileWriter => Unit) {
      val writer = new FileWriter(f, true)
      try {
        block(writer)
      } finally {
        writer.close()
      }
    }
    write(build) { fw =>
      fw.write("""
      |
      |seq(lsSettings :_*)
      |""".stripMargin)
    }
    write(plugin) { fw =>
      fw.write("""
      |
      |resolvers ++= Seq(
      |  "less is" at "http://repo.lessis.me",
      |  "coda" at "http://repo.codahale.com")
      |
      |addSbtPlugin("me.lessis" % "ls-sbt" % "%VERSION%")
      |""".replace("%VERSION%", version).stripMargin)
    }
    Right(msg)
  }
}
class LsInit extends xsbti.AppMain {
  def run(config: xsbti.AppConfiguration) =
    new Exit(LsInit.run(config.arguments))
}
class Exit(val code: Int) extends xsbti.Exit
