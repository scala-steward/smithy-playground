package playground.smithyql

import weaver._
import playground.Assertions._
import playground.Diffs._
import playground.smithyql.parser.SourceParser

object AtPositionTests extends FunSuite {

  val CURSOR = """<<HERE>>"""

  def extractCursor(s: String): (String, Position) = {
    val cursor = Position(s.indexOf(CURSOR))

    (s.replace(CURSOR, ""), cursor)
  }

  def locateAtCursor(text: String) = {
    val (extracted, position) = extractCursor(text)
    val parsed =
      SourceParser[Query]
        .parse(extracted)
        .toTry
        .get

    RangeIndex
      .build(parsed)
      .findAtPosition(position)
      .map(_.ctx)
  }

  test("atPosition - 1 level deep") {
    val actual = locateAtCursor(
      s"""Operation { root = { ${CURSOR}mid = { child = "hello", }, }, }"""
    )

    assertNoDiff(
      actual,
      Some(
        NodeContext
          .Root
          .inOperationInput
          .inStructBody
          .inStructValue("root")
          .inStructBody
      ),
    )
  }

  test("atPosition - 2 levels deep") {
    val actual = locateAtCursor(
      s"""Operation { root = { mid = {${CURSOR} child = "hello", }, }, }"""
    )

    assertNoDiff(
      actual,
      Some(
        NodeContext
          .Root
          .inOperationInput
          .inStructBody
          .inStructValue("root")
          .inStructBody
          .inStructValue("mid")
          .inStructBody
      ),
    )
  }

  test("atPosition - on operation") {
    val actual = locateAtCursor(
      s"""Operat${CURSOR}ion { root = { mid = { child = "hello", }, }, }"""
    )

    assertNoDiff(
      actual,
      Some(
        NodeContext.Root.inOperationName
      ),
    )
  }

  test("atPosition - on list") {
    val actual = locateAtCursor(
      s"""Operation { root = ${CURSOR}[ { mid = { inner = "hello", }, } ],  }"""
    )

    val expected = NodeContext.Root.inOperationInput.inStructBody.inStructValue("root")

    assertNoDiff(
      actual,
      Some(
        expected
      ),
    )
  }

  test("atPosition - inside list") {
    val actual = locateAtCursor(
      s"""Operation { root = [ ${CURSOR} { mid = { inner = "hello", }, } ],  }"""
    )

    val expected = NodeContext
      .Root
      .inOperationInput
      .inStructBody
      .inStructValue("root")
      .inCollectionEntry(None)

    assertNoDiff(
      actual,
      Some(expected),
    )
  }

  test("atPosition - on item in list") {
    val actual = locateAtCursor(
      s"""Operation { root = [ { ${CURSOR} mid = { inner = "hello", }, } ],  }"""
    )

    val expected =
      NodeContext
        .Root
        .inOperationInput
        .inStructBody
        .inStructValue("root")
        .inCollectionEntry(Some(0))
        .inStructBody

    assertNoDiff(actual, Some(expected))
  }

  test("atPosition - on nested item in list") {
    val actual = locateAtCursor(
      s"""Operation { root = [ {}, { mid = { ${CURSOR} inner = "hello", }, } ],  }"""
    )

    assertNoDiff(
      actual,
      Some(
        NodeContext
          .Root
          .inOperationInput
          .inStructBody
          .inStructValue("root")
          .inCollectionEntry(Some(1))
          .inStructBody
          .inStructValue("mid")
          .inStructBody
      ),
    )
  }

  test("atPosition - around struct ") {
    val actual = locateAtCursor(
      s"""Operation { root = $CURSOR{ }, }"""
    )

    assertNoDiff(
      actual,
      Some(
        NodeContext.Root.inOperationInput.inStructBody.inStructValue("root")
      ),
    )
  }

  test("atPosition - in struct") {
    val actual = locateAtCursor(
      s"""Operation { root = {$CURSOR}, }"""
    )

    assertNoDiff(
      actual,
      Some(
        NodeContext.Root.inOperationInput.inStructBody.inStructValue("root").inStructBody
      ),
    )
  }

  test("atPosition - on field outside quotes") {
    val actual = locateAtCursor(
      s"""Operation { field = $CURSOR"", }"""
    )

    assertNoDiff(
      actual,
      Some(NodeContext.Root.inOperationInput.inStructBody.inStructValue("field")),
    )

  }

  test("atPosition - on string field in quotes") {
    val actual = locateAtCursor(
      s"""Operation { field = "$CURSOR", }"""
    )

    assertNoDiff(
      actual,
      Some(
        NodeContext.Root.inOperationInput.inStructBody.inStructValue("field").inQuotes
      ),
    )
  }
}