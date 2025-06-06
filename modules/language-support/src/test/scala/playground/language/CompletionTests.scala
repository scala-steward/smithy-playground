package playground.language

import cats.syntax.all.*
import demo.smithy.Good
import demo.smithy.HasDeprecations
import demo.smithy.HasNewtypes
import demo.smithy.Hero
import demo.smithy.IntSet
import demo.smithy.Ints
import demo.smithy.MyInstant
import demo.smithy.MyInt
import demo.smithy.MyString
import demo.smithy.Power
import demo.smithy.PowerMap
import demo.smithy.PrivacyTier
import demo.smithy.SampleSparseList
import demo.smithy.SampleSparseMap
import playground.Assertions.*
import playground.language.Diffs.given
import playground.smithyql.NodeContext
import playground.smithyql.NodeContext.PathEntry.*
import smithy.api.TimestampFormat
import smithy4s.Hints
import smithy4s.Timestamp
import smithy4s.schema.Schema
import weaver.*

import java.util.UUID

object CompletionTests extends FunSuite {

  def getCompletions(
    schema: Schema[?],
    ctx: NodeContext,
  ): List[CompletionItem] = schema.compile(CompletionVisitor).getCompletions(ctx)

  test("completions on struct are empty without StructBody") {

    val completions = getCompletions(Good.schema, NodeContext.EmptyPath)

    expect(completions.isEmpty)
  }

  test("completions on struct include all field names") {

    val completions = getCompletions(Good.schema, NodeContext.EmptyPath.inStructBody)

    val fieldNames = completions.map(_.label)

    expect.eql(fieldNames, List("howGood")) &&
    expect(completions.map(_.kind).forall(_ == CompletionItemKind.Field))
  }

  test("completions on struct describe the field types") {

    val completions = getCompletions(Good.schema, NodeContext.EmptyPath.inStructBody)

    val results = completions.map { field =>
      (field.label, field.detail)
    }

    expect.eql(results, List("howGood" -> ": integer Integer"))
  }

  test("completions on struct add prefix/docs for optional fields") {
    val AnOptionalFieldLabel = "str"

    val completions = getCompletions(HasNewtypes.schema, NodeContext.EmptyPath.inStructBody)
      .filter(_.label == AnOptionalFieldLabel)

    val details = completions.map(_.detail)
    val docs = completions.map(_.docs)

    expect.eql(details, List("?: string MyString")) &&
    expect.eql(docs, List("**Optional**".some))
  }

  test("completions on union are empty without StructBody") {

    val completions = getCompletions(Hero.schema, NodeContext.EmptyPath)

    expect(completions.isEmpty)
  }

  test("completions on union") {

    val completions = getCompletions(Hero.schema, NodeContext.EmptyPath.inStructBody)

    val fieldNames = completions.map(_.label)
    val details = completions.map(_.detail)
    val kinds = completions.map(_.kind)

    expect.eql(fieldNames, List("good", "bad", "badder")) &&
    expect(details == List(": structure Good", ": structure Bad", ": structure Bad")) &&
    expect(kinds.forall(_ == CompletionItemKind.UnionMember))
  }

  test("completions on union case are the same as completions on the underlying structure") {
    val pathToField = NodeContext.EmptyPath.inStructValue("good").inStructBody

    val completionsOnAlt = getCompletions(
      Hero.schema,
      StructBody ^^: pathToField,
    ).map(_.label)

    val completionsOnStruct = getCompletions(
      Good.schema,
      NodeContext.EmptyPath.append(StructBody),
    ).map(_.label)

    expect.eql(completionsOnAlt, completionsOnStruct)
  }

  test("no completions on collection without entry") {
    val completions = getCompletions(
      Schema.list(Good.schema),
      NodeContext.EmptyPath,
    )

    expect(completions.isEmpty)
  }

  test("completions in sparse collection root contain null") {
    val completions = getCompletions(
      Schema.list(Schema.int.option),
      NodeContext.EmptyPath.inCollectionEntry(None),
    )

    assertNoDiff(
      completions,
      List(
        CompletionItem.forNull
      ),
    )
  }

  test("completions in sparse map root contain null") {
    val completions = getCompletions(
      Schema.map(Schema.string, Schema.int.option),
      NodeContext.EmptyPath.inStructBody.inStructValue("test"),
    )

    assertNoDiff(
      completions,
      List(
        CompletionItem.forNull
      ),
    )
  }

  test("completions on struct in list are available") {
    val completions = getCompletions(
      Schema.list(Good.schema),
      NodeContext.EmptyPath.inCollectionEntry(0.some).inStructBody,
    )

    val fieldNames = completions.map(_.label)

    expect.eql(fieldNames, List("howGood"))
  }

  test("completions on struct in sparse list are available") {
    val completions = getCompletions(
      Schema.list(Good.schema.option),
      NodeContext.EmptyPath.inCollectionEntry(0.some).inStructBody,
    )

    val fieldNames = completions.map(_.label)

    expect.eql(fieldNames, List("howGood"))
  }

  test("completions on enum without quotes have quotes") {

    val completions = getCompletions(Power.schema, NodeContext.EmptyPath)

    val inserts = completions.map(_.insertText)
    val expectedInserts = List("ICE", "FIRE", "LIGHTNING", "WIND")
      .map(s => s"\"$s\"")
      .map(InsertText.JustString(_))

    expect(completions.map(_.kind).forall(_ == CompletionItemKind.EnumMember)) &&
    expect(inserts == expectedInserts)
  }

  test("completions on enum in quotes don't have quotes") {
    val completions = getCompletions(Power.schema, NodeContext.EmptyPath.inQuotes)

    val inserts = completions.map(_.insertText)
    val expectedInserts = List("ICE", "FIRE", "LIGHTNING", "WIND")
      .map(InsertText.JustString(_))

    expect(completions.map(_.kind).forall(_ == CompletionItemKind.EnumMember)) &&
    expect(inserts == expectedInserts)
  }

  test("completions on enum don't have Optional docs") {
    val completions = getCompletions(Power.schema, NodeContext.EmptyPath.inQuotes)

    val docs = completions.flatMap(_.docs)

    expect(docs.isEmpty)
  }

  test("completions on map keys that are enums") {
    val completions = getCompletions(PowerMap.schema, NodeContext.EmptyPath.inStructBody)

    val inserts = completions.map(_.insertText)

    val expectedInserts = List("ICE", "FIRE", "LIGHTNING", "WIND")
      .map(_ + " = ")
      .map(InsertText.JustString(_))

    expect(completions.map(_.kind).forall(_ == CompletionItemKind.EnumMember)) &&
    expect(inserts == expectedInserts)
  }

  test("completions on map values (struct)") {
    val completions = getCompletions(
      Schema
        .map(
          Schema.string,
          Good.schema,
        ),
      NodeContext.EmptyPath.inStructBody.inStructValue("anyKey").inStructBody,
    )

    val fieldNames = completions.map(_.label)

    expect.eql(fieldNames, List("howGood")) &&
    expect(completions.map(_.kind).forall(_ == CompletionItemKind.Field))
  }

  test("completions on timestamp without quotes have quotes") {
    val completions = getCompletions(Schema.timestamp, NodeContext.EmptyPath)

    val extractQuote = """\"(.*)\"""".r

    val inserts = completions.map(_.insertText).foldMap {
      case InsertText.JustString(extractQuote(value)) =>
        expect(Timestamp.parse(value, TimestampFormat.DATE_TIME).isDefined)
      case s => failure("unexpected insert text: " + s)
    }

    expect(completions.map(_.kind).forall(_ == CompletionItemKind.Constant)) &&
    expect.eql(completions.size, 1) &&
    inserts
  }

  test("completions on timestamp in quotes don't have quotes") {
    val completions = getCompletions(
      Schema.timestamp,
      NodeContext.EmptyPath.inQuotes,
    )

    val inserts = completions.map(_.insertText).foldMap {
      case InsertText.JustString(value) =>
        expect(Timestamp.parse(value, TimestampFormat.DATE_TIME).isDefined)
      case s => failure("unexpected insert text: " + s)
    }

    expect(completions.map(_.kind).forall(_ == CompletionItemKind.Constant)) &&
    expect.eql(completions.size, 1) &&
    inserts
  }

  test("completions on uuid include a random uuid") {
    val completions = getCompletions(Schema.uuid, NodeContext.EmptyPath.inQuotes)

    val inserts = completions.map(_.insertText).foldMap {
      case InsertText.JustString(value) =>
        val parsed = Either.catchNonFatal(UUID.fromString(value))
        expect(parsed.isRight)

      case s => failure("unexpected insert text: " + s)
    }

    expect(completions.map(_.kind).forall(_ == CompletionItemKind.Constant)) &&
    expect.eql(completions.size, 1) &&
    inserts
  }

  test("completions on deprecated fields have proper hints in docs") {
    val completions = getCompletions(HasDeprecations.schema, NodeContext.EmptyPath.inStructBody)
      .filter(_.deprecated)

    val results = completions.map(c => (c.label, c.docs)).toMap

    expect.eql(results.keySet, Set("hasBoth", "hasMessage", "hasSince")) &&
    expect.eql(
      results,
      Map(
        "hasBoth" -> Some("**Deprecated** (since 1.0.0): Another reason"),
        "hasMessage" -> Some("**Deprecated**: Made-up reason\n\n**Optional**"),
        "hasSince" -> Some("**Deprecated** (since 0.1.0)"),
      ),
    )
  }

  test("describe indexed seq") {
    expect.eql(
      CompletionItem.describeSchema(Ints.schema)(),
      "list Ints { member: integer Integer }",
    )
  }

  test("describe set of ints") {
    expect.eql(
      CompletionItem.describeSchema(IntSet.schema)(),
      "@uniqueItems list IntSet { member: integer Integer }",
    )
  }

  test("describe int newtype") {
    expect.eql(
      CompletionItem.describeSchema(MyInt.schema)(),
      "integer MyInt",
    )
  }

  test("describe string newtype") {
    expect.eql(
      CompletionItem.describeSchema(MyString.schema)(),
      "string MyString",
    )
  }

  test("describe enum") {
    expect.eql(
      CompletionItem.describeSchema(Power.schema)(),
      "enum Power",
    )
  }

  test("describe int enum") {
    expect.eql(
      CompletionItem.describeSchema(PrivacyTier.schema)(),
      "intEnum PrivacyTier",
    )
  }

  test("describe map") {
    expect.eql(
      CompletionItem.describeSchema(PowerMap.schema)(),
      "map PowerMap { key: enum Power, value: union Hero }",
    )
  }

  test("describe uuid") {
    expect.eql(
      CompletionItem.describeSchema(Schema.uuid)(),
      "uuid UUID",
    )
  }

  test("describe refinement") {
    expect.eql(
      CompletionItem.describeSchema(MyInstant.schema)(),
      "timestamp MyInstant",
    )
  }

  test("describe non-field: no optionality sign") {
    expect.eql(
      CompletionItem.describeType(isField = false, Schema.string),
      ": string String",
    )
  }

  test("describe required field: no optionality sign") {
    expect.eql(
      CompletionItem.describeType(isField = true, Schema.string.addHints(smithy.api.Required())),
      ": string String",
    )
  }

  test("describe optional field: optionality sign present") {
    expect.eql(
      CompletionItem.describeType(isField = true, Schema.string),
      "?: string String",
    )
  }

  test("describe sparse collection: sparse trait present") {
    expect.eql(
      CompletionItem.describeType(
        isField = false,
        SampleSparseList.schema,
      ),
      ": @sparse list SampleSparseList { member: integer Integer }",
    )
  }

  test("describe sparse map: sparse trait present") {
    expect.eql(
      CompletionItem.describeType(
        isField = false,
        SampleSparseMap.schema,
      ),
      ": @sparse map SampleSparseMap { key: string String, value: integer Integer }",
    )
  }

  test("buildDocumentation: deprecation note goes before optionality note") {
    val doc = CompletionItem.buildDocumentation(
      isField = true,
      hints = Hints(smithy.api.Deprecated()),
    )

    expect.eql(
      doc,
      """**Deprecated**
        |
        |**Optional**""".stripMargin.some,
    )
  }

}
