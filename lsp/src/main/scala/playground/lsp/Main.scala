package playground.lsp

import cats.Show
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.implicits._
import cats.effect.kernel.Deferred
import cats.effect.kernel.DeferredSink
import cats.effect.kernel.Resource
import cats.effect.std
import cats.effect.std.Dispatcher
import cats.implicits._
import fs2.io.file.Files
import fs2.io.file.Path
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services
import org.http4s.ember.client.EmberClientBuilder
import playground.BuildConfig
import playground.BuildConfigDecoder
import playground.ModelReader
import smithy4s.aws.AwsEnvironment
import smithy4s.aws.http4s.AwsHttp4sBackend
import smithy4s.aws.kernel.AwsRegion
import smithy4s.codegen.cli.DumpModel
import smithy4s.codegen.cli.Smithy4sCommand
import smithy4s.dynamic.DynamicSchemaIndex

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintWriter
import java.nio.charset.Charset

object Main extends IOApp.Simple {

  private val logWriter = new PrintWriter(new File("lsp-log.txt"))

  def log(s: String): IO[Unit] = IO(logWriter.println(s))

  implicit val ioConsole: std.Console[IO] =
    new std.Console[IO] {

      def readLineWithCharset(
        charset: Charset
      ): IO[String] = IO.consoleForIO.readLineWithCharset(charset)

      def print[A](a: A)(implicit S: Show[A]): IO[Unit] = IO(logWriter.print(a.show))

      def println[A](a: A)(implicit S: Show[A]): IO[Unit] = IO(logWriter.println(a.show))

      def error[A](a: A)(implicit S: Show[A]): IO[Unit] = IO(logWriter.print("ERROR: " + a.show))

      def errorln[A](a: A)(implicit S: Show[A]): IO[Unit] = IO(
        logWriter.println("ERROR: " + a.show)
      )

    }

  def run: IO[Unit] =
    Dispatcher[IO]
      .flatMap { implicit d =>
        launch(System.in, System.out)
      }
      .use { launcher =>
        IO.interruptibleMany(launcher.startListening().get())
      } *> log("Server terminated without errors")

  def launch(
    in: InputStream,
    out: OutputStream,
  )(
    implicit d: Dispatcher[IO]
  ) = Deferred[IO, services.LanguageClient].toResource.flatMap { clientPromise =>
    makeServer(LanguageClient.adapt(clientPromise.get)).evalMap { server =>
      val launcher = new LSPLauncher.Builder[services.LanguageClient]()
        .setLocalService(new PlaygroundLanguageServerAdapter(server))
        .setRemoteInterface(classOf[services.LanguageClient])
        .setInput(in)
        .setOutput(out)
        .traceMessages(logWriter)
        .create();

      connect(launcher.getRemoteProxy(), clientPromise).as(launcher)
    }

  }

  private def makeServer(
    implicit lc: LanguageClient[IO]
  ): Resource[IO, LanguageServer[IO]] = EmberClientBuilder.default[IO].build.flatMap { client =>
    AwsEnvironment
      .default(AwsHttp4sBackend(client), AwsRegion.US_EAST_1)
      .memoize
      .flatMap { awsEnv =>
        TextDocumentManager
          .instance[IO]
          .flatMap { implicit tdm =>
            // todo: workspace root
            readBuildConfig(Path("/Users/kubukoz/projects/smithy-playground-demo"))
              .flatMap(buildSchemaIndex)
              .map { dsi =>
                LanguageServer.instance[IO](dsi, log, client, awsEnv)
              }
          }
          .toResource
      }
  }

  private def readBuildConfig(ctx: Path) = Files[IO]
    .readAll(ctx / "smithy-build.json")
    .compile
    .toVector
    .map(_.toArray)
    .flatMap {
      BuildConfigDecoder.decode(_).liftTo[IO]
    }

  private def buildSchemaIndex(bc: BuildConfig): IO[DynamicSchemaIndex] = IO
    .interruptibleMany {
      DumpModel.run(
        Smithy4sCommand.DumpModelArgs(
          specs = bc.imports.combineAll.map(os.Path(_)),
          repositories = bc.mavenRepositories.combineAll,
          dependencies = bc.mavenDependencies.combineAll,
          transformers = Nil,
          localJars = Nil,
        )
      )
    }
    .flatMap { modelText =>
      ModelReader
        .modelParser(modelText)
        .liftTo[IO]
        .map(ModelReader.buildSchemaIndex(_))
    }

  private def connect(
    client: services.LanguageClient,
    clientDeff: DeferredSink[IO, services.LanguageClient],
  ) =
    log("connecting: " + client) *>
      clientDeff.complete(client) *>
      IO(client.showMessage(new MessageParams(MessageType.Info, "hello from smithyql server"))) *>
      log("Server connected")

}
