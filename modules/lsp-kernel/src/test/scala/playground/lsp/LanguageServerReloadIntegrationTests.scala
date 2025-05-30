package playground.lsp

import cats.effect.IO
import cats.syntax.all.*
import fs2.io.file.Files
import fs2.io.file.Path
import playground.PlaygroundConfig
import playground.language.Uri
import playground.lsp.harness.LanguageServerIntegrationTests
import playground.lsp.harness.TestClient.MessageLog
import weaver.*

import scala.jdk.CollectionConverters.*

object LanguageServerReloadIntegrationTests
  extends SimpleIOSuite
  with LanguageServerIntegrationTests {

  private def readBytes(
    path: Path
  ): IO[Array[Byte]] = Files[IO]
    .readAll(path)
    .compile
    .to(Array)

  private def write(
    path: Path,
    text: String,
  ): IO[Unit] = writeBytes(path, text.getBytes)

  private def writeBytes(
    path: Path,
    bytes: Array[Byte],
  ): IO[Unit] =
    fs2
      .Stream
      .emits(bytes)
      .through(Files[IO].writeAll(path))
      .compile
      .drain

  test("workspace reload results in code lenses showing up") {

    Files[IO]
      .tempDirectory
      .evalTap { base =>
        write(base / "smithy-build.json", "{}") *>
          write(
            base / "demo.smithyql",
            """use service weather#WeatherService
              |GetWeather { city: "hello" }""".stripMargin,
          )
      }
      .flatMap { base =>
        makeServer(Uri.fromPath(base))
          .evalMap { f =>
            val getLenses = f
              .server
              .codeLens(
                documentUri = f.workspaceDir / "demo.smithyql"
              )

            val workspacePath = (testWorkspacesBase / "default").toPath

            val weatherPath = workspacePath / "weather.smithy"

            val addLibrary = readBytes(workspacePath / "smithy-build.json")
              .flatMap { bytes =>
                PlaygroundConfig.decode(bytes).liftTo[IO]
              }
              .flatMap { baseConfig =>
                writeBytes(
                  base / "smithy-build.json",
                  PlaygroundConfig.encode(
                    baseConfig.copy(
                      imports = weatherPath.absolute.toString :: Nil
                    )
                  ),
                )
              }

            getLenses.flatMap { lensesBefore =>
              expect(lensesBefore.isEmpty).failFast[IO]
            } *>
              addLibrary *>
              f.server.didChangeWatchedFiles *>
              getLenses
          }
      }
      .use { lensesAfter =>
        expect(lensesAfter.length == 1).pure[IO]
      }
  }

  test("workspace reload works even if there initially was no config file") {
    Files[IO]
      .tempDirectory
      .flatMap { base =>
        makeServer(Uri.fromPath(base))
          .evalMap { f =>
            f.client.scoped {
              writeBytes(
                base / "smithy-build.json",
                PlaygroundConfig.encode(PlaygroundConfig.empty),
              ) *>
                f.server.didChangeWatchedFiles
            }
          }
      }
      .use_
      .as(success)
  }

  test("workspace can be loaded even if non-model JSON files are included") {
    makeServer(testWorkspacesBase / "non-model-jsons")
      .use(_.client.getEvents)
      .map { events =>
        val errorLogs = events.collect { case MessageLog(MessageType.Error, msg) => msg }
        expect(errorLogs.isEmpty)
      }
  }

  test("JSON smithy models can be loaded") {
    makeServer(testWorkspacesBase / "json-models")
      .use { f =>
        f.client.getEvents.flatMap { events =>
          val errorLogs = events.collect { case MessageLog(MessageType.Error, msg) => msg }

          f.server
            .diagnostic(
              documentUri = f.workspaceDir / "input.smithyql"
            )
            .map { diags =>
              val items = diags.map(_.diagnostic.err)

              expect(errorLogs.isEmpty) &&
              expect(items.isEmpty)
            }
        }
      }
  }
}
