package playground

import cats.Id
import demo.smithy.Good
import playground.NodeEncoder
import playground.smithyql.AST
import playground.smithyql.DSL._
import smithy4s.schema.Schema
import weaver._
import demo.smithy.Hero
import smithy4s.Timestamp
import smithy.api.TimestampFormat

object NodeEncoderTests extends FunSuite {

  def assertEncodes[A](
    schema: Schema[A],
    value: A,
    expected: AST[Id],
  )(
    implicit loc: SourceLocation
  ) = {
    val enc = NodeEncoder.derive(schema)

    assert.eql(enc.toNode(value), expected)
  }

  test("unit") {
    assertEncodes(Schema.unit, (), struct())
  }

  test("String") {
    assertEncodes(Schema.string, "test", "test")
  }

  test("int") {
    assertEncodes(Schema.int, 42, 42)
  }

  test("simple struct") {
    assertEncodes(Good.schema, Good(42), struct("howGood" -> 42))
  }

  test("union") {
    assertEncodes(Hero.schema, Hero.GoodCase(Good(42)), struct("good" -> struct("howGood" -> 42)))
  }

  test("timestamp") {
    assertEncodes(
      Schema.timestamp,
      Timestamp.parse("2022-07-11T17:42:28Z", TimestampFormat.DATE_TIME).get,
      "2022-07-11T17:42:28Z",
    )
  }
}