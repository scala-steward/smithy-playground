package playground

import cats.Id
import playground.smithyql.InputNode
import playground.smithyql.Struct
import schematic.Alt
import schematic.ByteArray
import schematic.Field
import smithy4s.Document
import smithy4s.Hints
import smithy4s.Schematic
import smithy4s.Timestamp
import sourcecode.Enclosing

import java.util.UUID
import playground.smithyql.StringLiteral
import playground.smithyql.IntLiteral

trait NodeEncoder[A] {
  def toNode(a: A): InputNode[Id]
}

object NodeEncoderSchematic
  extends Schematic[NodeEncoder]
  with schematic.struct.GenericAritySchematic[NodeEncoder] {

  def todo[A](implicit sc: Enclosing): NodeEncoder[A] =
    v => throw new Exception(s"Unsupported operation: ${sc.value} for value $v")

  def short: NodeEncoder[Short] = todo

  def int: NodeEncoder[Int] = IntLiteral(_)

  def long: NodeEncoder[Long] = todo

  def double: NodeEncoder[Double] = todo

  def float: NodeEncoder[Float] = todo

  def bigint: NodeEncoder[BigInt] = todo

  def bigdecimal: NodeEncoder[BigDecimal] = todo

  def string: NodeEncoder[String] = StringLiteral(_)

  def boolean: NodeEncoder[Boolean] = todo

  def uuid: NodeEncoder[UUID] = todo

  def byte: NodeEncoder[Byte] = todo

  def bytes: NodeEncoder[ByteArray] = todo

  def unit: NodeEncoder[Unit] = todo

  def list[S](fs: NodeEncoder[S]): NodeEncoder[List[S]] = todo

  def set[S](fs: NodeEncoder[S]): NodeEncoder[Set[S]] = todo

  def vector[S](fs: NodeEncoder[S]): NodeEncoder[Vector[S]] = todo

  def map[K, V](fk: NodeEncoder[K], fv: NodeEncoder[V]): NodeEncoder[Map[K, V]] = todo

  def genericStruct[S](
    fields: Vector[Field[NodeEncoder, S, _]]
  )(
    const: Vector[Any] => S
  ): NodeEncoder[S] =
    s =>
      Struct[Id] {

        def go[A](
          f: Field[NodeEncoder, S, A]
        ) = f.fold(new Field.Folder[NodeEncoder, S, Option[(String, InputNode[Id])]] {
          def onRequired[F](
            label: String,
            instance: NodeEncoder[F],
            get: S => F,
          ): Option[(String, InputNode[Id])] = Some(label -> instance.toNode(get(s)))

          def onOptional[F](
            label: String,
            instance: NodeEncoder[F],
            get: S => Option[F],
          ): Option[(String, InputNode[Id])] = get(s).map(f => label -> instance.toNode(f))
        })

        fields.flatMap {
          go(_).map { case (s, v) => Struct.Key(s) -> v }
        }.toMap
      }

  def union[S](
    first: Alt[NodeEncoder, S, _],
    rest: Vector[Alt[NodeEncoder, S, _]],
  )(
    total: S => Alt.WithValue[NodeEncoder, S, _]
  ): NodeEncoder[S] =
    s => {
      def go[A](r: Alt.WithValue[NodeEncoder, S, A]) = Struct[Id](
        Map(Struct.Key(r.alt.label) -> r.alt.instance.toNode(r.value))
      )

      go(total(s))
    }

  def enumeration[A](
    to: A => (String, Int),
    fromName: Map[String, A],
    fromOrdinal: Map[Int, A],
  ): NodeEncoder[A] = todo

  def suspend[A](f: => NodeEncoder[A]): NodeEncoder[A] = todo

  def bijection[A, B](
    f: NodeEncoder[A],
    to: A => B,
    from: B => A,
  ): NodeEncoder[B] = b => f.toNode(from(b))

  def timestamp: NodeEncoder[Timestamp] = todo

  def withHints[A](fa: NodeEncoder[A], hints: Hints): NodeEncoder[A] = fa

  def document: NodeEncoder[Document] = todo

}