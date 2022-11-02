package playground.language

import cats.Id
import cats.data.NonEmptyList
import cats.implicits._
import playground.MultiServiceResolver
import playground.smithyql.NodeContext
import playground.smithyql.NodeContext.EmptyPath
import playground.smithyql.NodeContext.^^:
import playground.smithyql.OperationName
import playground.smithyql.Position
import playground.smithyql.QualifiedIdentifier
import playground.smithyql.Query
import playground.smithyql.RangeIndex
import playground.smithyql.WithSource
import playground.smithyql.parser.SourceParser
import playground.smithyql.syntax._
import smithy4s.dynamic.DynamicSchemaIndex
import playground.smithyql.SourceFile

trait CompletionProvider {
  def provide(documentText: String, pos: Position): List[CompletionItem]
}

object CompletionProvider {

  def forSchemaIndex(
    dsi: DynamicSchemaIndex
  ): CompletionProvider = forServices(dsi.allServices)

  def forServices(
    allServices: List[DynamicSchemaIndex.ServiceWrapper]
  ): CompletionProvider = {
    val servicesById =
      allServices.map { service =>
        QualifiedIdentifier.forService(service.service) -> service
      }.toMap

    val serviceIdsById = servicesById.map { case (k, _) => (k, k) }

    val opsToServices = servicesById.toList.foldMap { case (serviceId, service) =>
      service
        .service
        .endpoints
        .foldMap(e => Map(OperationName[Id](e.name) -> NonEmptyList.one(serviceId)))
    }

    val completeOperationName
      : Map[QualifiedIdentifier, List[QualifiedIdentifier] => List[CompletionItem]] = servicesById
      .map { case (serviceId, service) =>
        serviceId -> { (presentServiceIdentifiers: List[QualifiedIdentifier]) =>
          val needsUseClause =
            MultiServiceResolver
              .resolveService(
                presentServiceIdentifiers,
                servicesById,
              )
              .isLeft

          val insertUseClause =
            if (needsUseClause)
              CompletionItem.InsertUseClause.Required(opsToServices)
            else
              CompletionItem.InsertUseClause.NotRequired

          service
            .service
            .endpoints
            .map { e =>
              CompletionItem.forOperation(
                insertUseClause = insertUseClause,
                endpoint = e,
                serviceId = serviceId,
              )
            }
        }
      }

    val completeAnyOperationName = completeOperationName.toList.map(_._2).flatSequence.apply(Nil)

    val completionsByEndpoint
      : Map[QualifiedIdentifier, Map[OperationName[Id], CompletionResolver[Any]]] = servicesById
      .fmap { service =>
        service
          .service
          .endpoints
          .map { endpoint =>
            OperationName[Id](endpoint.name) -> endpoint.input.compile(CompletionVisitor)
          }
          .toMap
      }

    def completeOperationNameFor(
      q: Query[WithSource],
      serviceId: Option[QualifiedIdentifier],
    ) =
      serviceId match {
        case Some(serviceId) =>
          completeOperationName(serviceId)(
            q.mapK(WithSource.unwrap).collectServiceIdentifiers
          )
        case _ => completeAnyOperationName
      }

    def completeInQuery(q: Query[WithSource], ctx: NodeContext): List[CompletionItem] = {
      // still wrong
      val serviceIdOpt =
        MultiServiceResolver
          .resolveService(
            q.mapK(WithSource.unwrap).collectServiceIdentifiers,
            serviceIdsById,
          )
          .toOption

      ctx match {
        case NodeContext.PathEntry.AtOperationName ^^: EmptyPath =>
          completeOperationNameFor(q, serviceIdOpt)

        case NodeContext.PathEntry.AtOperationInput ^^: ctx =>
          serviceIdOpt match {
            case Some(serviceId) =>
              completionsByEndpoint(serviceId)(
                q.operationName.value.operationName.value.mapK(WithSource.unwrap)
              )
                .getCompletions(ctx)

            case None => Nil
          }

        case _ => Nil
      }
    }

    (doc, pos) =>
      SourceParser[SourceFile].parse(doc) match {
        case Left(_) =>
          // we can try to deal with this later
          Nil

        case Right(sf) =>
          val matchingNode = RangeIndex
            .build(sf)
            .findAtPosition(pos)

          // System.err.println("matchingNode: " + matchingNode.render)

          matchingNode match {
            case NodeContext.PathEntry.InQuery(n) ^^: rest =>
              val q =
                sf
                  .queries
                  .get(n.toLong)
                  .getOrElse(sys.error(s"Fatal error: no query at index $n"))
                  .query
                  .value

              completeInQuery(q, rest)

            case NodeContext.PathEntry.AtUseClause ^^: EmptyPath =>
              servicesById
                .toList
                .sortBy(_._1)
                .map(CompletionItem.useServiceClause.tupled)
                .toList

            case EmptyPath => completeAnyOperationName
            case _         => Nil
          }

      }
  }

}
