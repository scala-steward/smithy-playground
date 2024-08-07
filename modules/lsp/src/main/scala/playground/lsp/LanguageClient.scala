package playground.lsp

import cats.FlatMap
import cats.data.Kleisli
import cats.effect.kernel.Async
import cats.syntax.all.*
import cats.tagless.Derive
import cats.tagless.FunctorK
import cats.tagless.catsTaglessApplyKForIdK
import cats.tagless.implicits.*
import cats.~>
import com.google.gson.JsonElement
import org.eclipse.lsp4j.ConfigurationItem
import org.eclipse.lsp4j.ConfigurationParams
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import playground.language.Feedback

import java.util.concurrent.CompletableFuture
import scala.jdk.CollectionConverters.*
import scala.util.chaining.*

trait LanguageClient[F[_]] extends Feedback[F] {

  def configuration[A](
    v: ConfigurationValue[A]
  ): F[A]

  def showMessage(
    tpe: MessageType,
    msg: String,
  ): F[Unit]

  def refreshDiagnostics: F[Unit]
  def refreshCodeLenses: F[Unit]

  def showInfoMessage(
    msg: String
  ): F[Unit] = showMessage(MessageType.Info, msg)

  def showWarnMessage(
    msg: String
  ): F[Unit] = showMessage(MessageType.Warning, msg)

  def showErrorMessage(
    msg: String
  ): F[Unit] = showMessage(MessageType.Error, msg)

}

object LanguageClient {

  def apply[F[_]](
    implicit F: LanguageClient[F]
  ): LanguageClient[F] = F

  def adapt[F[_]: Async](
    client: PlaygroundLanguageClient
  ): LanguageClient[F] =
    new LanguageClient[F] {

      private def withClientF[A](
        f: client.type => CompletableFuture[A]
      ): F[A] = Async[F].fromCompletableFuture(Async[F].delay(f(client)))

      private def withClientSync[A](
        f: client.type => A
      ): F[A] = Async[F].delay(f(client))

      def configuration[A](
        v: ConfigurationValue[A]
      ): F[A] = withClientF(
        _.configuration(
          new ConfigurationParams(
            (new ConfigurationItem().tap(_.setSection(v.key)) :: Nil).asJava
          )
        )
      )
        .flatMap {
          _.asScala
            .toList
            .headOption
            .liftTo[F](new Throwable("missing entry in the response"))
        }
        .map {
          case e: JsonElement => converters.gsonToCirce(e)
          case e              => throw new RuntimeException(s"Unexpected configuration value: $e")
        }
        .flatMap(_.as[A](v.codec).liftTo[F])

      def showMessage(
        tpe: MessageType,
        msg: String,
      ): F[Unit] = withClientSync(
        _.showMessage(new MessageParams(tpe, msg))
      )

      def logOutput(
        msg: String
      ): F[Unit] = withClientSync(
        _.logMessage(new MessageParams().tap(_.setMessage(msg)))
      )

      def showOutputPanel: F[Unit] = withClientSync(_.showOutputPanel())

      def refreshCodeLenses: F[Unit] = withClientF(_.refreshCodeLenses()).void
      def refreshDiagnostics: F[Unit] = withClientF(_.refreshDiagnostics()).void
    }

  implicit val functorK: FunctorK[LanguageClient] = Derive.functorK[LanguageClient]

  def defer[F[_]: FlatMap](
    fa: F[LanguageClient[F]]
  ): LanguageClient[F] = Derive
    .readerT[LanguageClient, F]
    .mapK(new (Kleisli[F, LanguageClient[F], *] ~> F) {

      def apply[A](
        k: Kleisli[F, LanguageClient[F], A]
      ): F[A] = fa.flatMap(k.run)

    })

  val NoChangeDetected: String = "No change detected, not rebuilding server"
}
