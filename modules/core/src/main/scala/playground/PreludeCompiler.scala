package playground

import cats.Applicative
import cats.Parallel
import cats.data.NonEmptyList
import cats.mtl.Chronicle
import cats.mtl.implicits.*
import cats.syntax.all.*
import playground.*
import playground.smithyql.Prelude
import playground.smithyql.WithSource

trait PreludeCompiler[F[_]] {

  def compile(
    f: Prelude[WithSource]
  ): F[Unit]

}

object PreludeCompiler {

  def instance[F[_]: Parallel: Applicative](
    serviceIndex: ServiceIndex
  )(
    implicit F: Chronicle[F, NonEmptyList[CompilationError]]
  ): PreludeCompiler[F] =
    new PreludeCompiler[F] {

      def compile(
        f: Prelude[WithSource]
      ): F[Unit] = f.useClauses.parTraverse_ { clause =>
        val serviceId = clause.value.identifier.value

        serviceIndex.getService(serviceId) match {
          case None =>
            CompilationError
              .error(
                CompilationErrorDetails.UnknownService(serviceIndex.serviceIds.toList),
                clause.value.identifier.range,
              )
              .pure[NonEmptyList]
              // this might be .dictate if we had a seal in FileCompiler
              // https://github.com/kubukoz/smithy-playground/issues/157
              .confess[F, Unit]

          case Some(service) =>
            service.deprecated.traverse_ { info =>
              CompilationError
                .deprecation(info, clause.value.identifier.range)
                .pure[NonEmptyList]
                .dictate
            }
        }

      }

    }

}
