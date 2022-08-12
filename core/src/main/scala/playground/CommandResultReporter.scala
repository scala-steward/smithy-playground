package playground

import cats.FlatMap
import cats.Id
import cats.data.NonEmptyList
import cats.effect.kernel.Ref
import cats.implicits._
import playground.smithyql.Formatter
import playground.smithyql.InputNode
import playground.smithyql.Query
import playground.smithyql.WithSource

import Runner.Issue.ProtocolIssues

object CommandResultReporter {
  def apply[F[_]](implicit F: CommandResultReporter[F]): F.type = F

  def instance[
    F[_]: Feedback: FlatMap: Ref.Make
  ]: F[CommandResultReporter[F]] = Ref[F].of(0).map(withRequestCounter(_))

  def withRequestCounter[F[_]: Feedback: FlatMap](
    requestCounter: Ref[F, Int]
  ): CommandResultReporter[F] =
    new CommandResultReporter[F] {

      type RequestId = Int

      def onUnsupportedProtocol(
        issues: ProtocolIssues
      ): F[Unit] = {
        val supportedString = issues.supported.map(_.show).mkString_(", ")
        val foundOnServiceString = issues.found.map(_.show).mkString(", ")

        Feedback[F].showErrorMessage(
          s"""The service uses an unsupported protocol.
             |Supported protocols: $supportedString
             |Found protocols: $foundOnServiceString""".stripMargin
        )
      }

      def onIssues(issues: NonEmptyList[Throwable]): F[Unit] = Feedback[F].showErrorMessage(
        issues.map(_.toString).mkString_("\n\n")
      )

      def onQueryCompiled(parsed: Query[Id], compiled: CompiledInput): F[RequestId] =
        Feedback[F].showOutputPanel *>
          requestCounter.updateAndGet(_ + 1).flatTap { requestId =>
            Feedback[F]
              .logOutput(
                s"// Calling ${parsed.operationName.text} ($requestId)"
              )
          }

      def onQuerySuccess(parsed: Query[Id], requestId: RequestId, out: InputNode[Id]): F[Unit] =
        Feedback[F].logOutput(
          s"// Succeeded ${parsed.operationName.text} ($requestId), response:\n"
            + writeOutput(out)
        )

      def onQueryFailure(e: Throwable, compiled: CompiledInput, requestId: Int): F[Unit] = {
        val rendered =
          compiled
            .catchError(e)
            .flatMap(err => compiled.writeError.map(_.toNode(err))) match {
            case Some(e) => "\n" + writeOutput(e)
            case None    => e.toString
          }

        Feedback[F].logOutput(s"// ERROR ($requestId) $rendered")
      }

      private def writeOutput(
        node: InputNode[cats.Id]
      ) = Formatter.writeAst(node.mapK(WithSource.liftId)).renderTrim(80)

    }

}

trait CommandResultReporter[F[_]] {
  type RequestId
  def onUnsupportedProtocol(issues: ProtocolIssues): F[Unit]
  def onIssues(issues: NonEmptyList[Throwable]): F[Unit]
  def onQueryCompiled(parsed: Query[Id], compiled: CompiledInput): F[RequestId]
  def onQuerySuccess(parsed: Query[Id], requestId: RequestId, output: InputNode[Id]): F[Unit]
  def onQueryFailure(e: Throwable, compiled: CompiledInput, requestId: RequestId): F[Unit]
}