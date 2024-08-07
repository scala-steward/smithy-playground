package playground.plugins

import cats.effect.Concurrent
import org.http4s.client.Client
import smithy4s.Service
import smithy4s.UnsupportedProtocolError
import smithy4s.http4s.SimpleProtocolBuilder

import java.util.ServiceLoader
import scala.jdk.CollectionConverters.*

trait PlaygroundPlugin {
  def simpleBuilders: List[SimpleHttpBuilder]
}

object PlaygroundPlugin {

  def getAllPlugins(
    loader: ClassLoader
  ): List[PlaygroundPlugin] =
    ServiceLoader
      .load(
        classOf[PlaygroundPlugin],
        loader,
      )
      .asScala
      .toList

}

/** A more flexible interface for SimpleProtocolBuilder-like things.
  */
trait SimpleHttpBuilder {

  def client[Alg[_[_, _, _, _, _]], F[_]: Concurrent](
    service: Service[Alg],
    backend: Client[F],
  ): Either[UnsupportedProtocolError, service.Impl[F]]

}

object SimpleHttpBuilder {

  def fromSimpleProtocolBuilder(
    builder: SimpleProtocolBuilder[?]
  ): SimpleHttpBuilder =
    new SimpleHttpBuilder {

      def client[Alg[_[_, _, _, _, _]], F[_]: Concurrent](
        service: Service[Alg],
        backend: Client[F],
      ): Either[UnsupportedProtocolError, service.Impl[F]] = builder(service).client(backend).make

    }

}
