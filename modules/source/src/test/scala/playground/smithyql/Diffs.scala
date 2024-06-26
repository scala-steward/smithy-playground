package playground.smithyql

import com.softwaremill.diffx.Diff

object Diffs {
  import com.softwaremill.diffx.cats.*
  import com.softwaremill.diffx.generic.auto.*

  implicit val diffSourceRange: Diff[SourceRange] = Diff.derivedDiff

  implicit val diffComment: Diff[Comment] = Diff.derivedDiff

  implicit val diffQualifiedIdentifier: Diff[QualifiedIdentifier] = Diff.derivedDiff

  implicit val diffUseClause: Diff[UseClause[WithSource]] = Diff.derivedDiff

  implicit def diffListedWithSource: Diff[Listed[WithSource]] = Diff.derivedDiff

  implicit val diffStructWithSource: Diff[Struct[WithSource]] = Diff.derivedDiff

  implicit val diffQueryWithSource: Diff[Query[WithSource]] = Diff.derivedDiff

  implicit val diffPreludeWithSource: Diff[Prelude[WithSource]] = Diff.derivedDiff

  implicit val diffSourceFile: Diff[SourceFile[WithSource]] = Diff.derivedDiff

}
