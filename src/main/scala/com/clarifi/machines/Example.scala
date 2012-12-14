package com.clarifi.machines

import scalaz.{Reader => _, _}
import scalaz.effect._
import Scalaz._

import java.io._

object Example extends SafeApp {

  import Machine.ProcessCategory._
  import Plan._

  def getFileLines[A](f: File, m: Process[String, A]): Procedure[IO, A] =
    new Procedure[IO, A] {
      type K = String => Any

      val machine = m

      def withDriver[R](k: Driver[IO, K] => IO[R]): IO[R] = {
        bufferFile(f).bracket(closeReader)(r => {
          val d = new Driver[IO, String => Any] {
            val M = Monad[IO]
            def apply(k: String => Any) = rReadLn(r) map (_ map k)
          }
          k(d)
        })
      }
    }

  def bufferFile(f: File): IO[BufferedReader] =
    IO { new BufferedReader(new FileReader(f)) }

  /** Read a line from a buffered reader */
  def rReadLn(r: BufferedReader): IO[Option[String]] = IO { Option(r.readLine) }

  def closeReader(r: Reader): IO[Unit] = IO { r.close }

  def lineCount(fileName: String) =
    getFileLines(new File(fileName), Process(_ => 1)).execute

  def lineCharCount(fileName: String) =
    getFileLines(new File(fileName), Process(x => (1, x.length))).execute

  val words: Process[String, String] = (for {
    s <- await[String]
    _ <- traversePlan_(s.split("\\W").toList)(emit)
  } yield ()) repeatedly

  def lineWordCount(fileName: String) =
    getFileLines(new File(fileName),
      (id split words) outmap (_.fold(_ => (1, 0), _ => (0, 1)))) execute

  override def run(args: ImmutableArray[String]): IO[Unit] = {
    val f = args(0)
    val c = lineWordCount(f)
    c flatMap (q => IO.putStrLn(q.toString))
  }
}

