package playground.language

import cats.syntax.all.*
import playground.smithyql.InputNode
import playground.smithyql.Listed
import playground.smithyql.Prelude
import playground.smithyql.Query
import playground.smithyql.SourceFile
import playground.smithyql.SourceRange
import playground.smithyql.Struct
import playground.smithyql.UseClause
import playground.smithyql.WithSource
import playground.smithyql.parser.SourceParser

object DocumentSymbolProvider {

  def make(
    text: String
  ): List[DocumentSymbol] =
    SourceParser[SourceFile].parse(text) match {
      case Left(_) => Nil
      case Right(sf) =>
        findInPrelude(sf.prelude) ++
          sf.queries(WithSource.unwrap).map(_.query).flatMap(findInQuery)
    }

  private def findInPrelude(
    p: Prelude[WithSource]
  ): List[DocumentSymbol] = p.useClauses.foldMap(findInUseClause)

  private def findInQuery(
    wsq: WithSource[Query[WithSource]]
  ): List[DocumentSymbol] = {
    val q = wsq.value

    DocumentSymbol(
      name = q.operationName.value.operationName.value.text,
      kind = SymbolKind.Function,
      selectionRange = q.operationName.range,
      range = wsq.range,
      children = findInStruct(q.input),
    ) :: Nil
  }

  private def findInUseClause(
    clause: WithSource[UseClause[WithSource]]
  ): List[DocumentSymbol] = {
    val useClause = clause.value

    DocumentSymbol(
      name = useClause.identifier.value.render,
      kind = SymbolKind.Package,
      selectionRange = useClause.identifier.range,
      range = useClause.identifier.range,
      children = Nil,
    ) :: Nil
  }

  private def findInStruct(
    struct: WithSource[Struct[WithSource]]
  ): List[DocumentSymbol] = struct.value.fields.value.value.map { binding =>
    val key = binding.identifier
    val value = binding.value

    DocumentSymbol(
      name = key.value.text,
      kind = SymbolKind.Field,
      selectionRange = key.range,
      range = key.range.fakeUnion(value.range),
      children = findInNode(value),
    )
  }

  private def findInNode(
    node: WithSource[InputNode[WithSource]]
  ): List[DocumentSymbol] = node
    .value
    .fold(
      struct = struct => findInStruct(node.copy(value = struct)),
      string = _ => Nil,
      int = _ => Nil,
      listed = list => findInList(node.copy(value = list)),
      bool = _ => Nil,
      nul = _ => Nil,
    )

  private def findInList(
    list: WithSource[Listed[WithSource]]
  ): List[DocumentSymbol] = list.value.values.value.zipWithIndex.map { case (item, i) =>
    DocumentSymbol(
      name = i.toString(),
      kind = SymbolKind.Array,
      selectionRange = item.range,
      range = item.range,
      children = findInNode(item),
    )
  }

}

final case class DocumentSymbol(
  name: String,
  kind: SymbolKind,
  selectionRange: SourceRange,
  range: SourceRange,
  children: List[DocumentSymbol],
)

sealed trait SymbolKind extends Product with Serializable

object SymbolKind {
  case object Function extends SymbolKind
  case object Package extends SymbolKind
  case object Array extends SymbolKind
  case object Field extends SymbolKind
}
