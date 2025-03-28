$version: "2"

namespace demo.smithy

use alloy#UUID
use alloy#simpleRestJson
use smithy4s.meta#indexedSeq
use smithy4s.meta#refinement

@refinement(targetType: "java.time.Instant", providerImport: "demo.smithy.InstantProvider._")
@trait
structure instant {}

@deprecated(message: "don't use", since: "0.0.0")
service DeprecatedService {
    version: "0.0.1"
    operations: [
        DeprecatedOperation
    ]
}

@deprecated(message: "don't use op", since: "0.0.0")
operation DeprecatedOperation {}

@simpleRestJson
service DemoService {
    version: "0.0.1"
    operations: [
        CreateHero
        GetPowers
        CreateSubscription
    ]
    errors: [
        GenericServerError
    ]
}

@simpleRestJson
service DemoService2 {
    version: "0.0.1"
    operations: [
        GetVersion
        CreateSubscription
    ]
}

@readonly
@http(uri: "/version", method: "GET")
operation GetVersion {}

@http(method: "POST", uri: "/heroes")
@documentation(
    """
    Create a hero.
    """
)
@examples([
    {
        title: "Valid input"
        documentation: "This is a valid input"
        input: {
            hero: {
                good: { howGood: 10 }
            }
        }
    }
    {
        title: "Valid input v2"
        documentation: "This is also a valid input, but for a bad hero"
        input: {
            hero: {
                bad: { evilName: "Evil", powerLevel: 10 }
            }
        }
    }
])
operation CreateHero {
    input: CreateHeroInput
    output: CreateHeroOutput
    errors: [
        HeroIsBad
    ]
}

structure CreateHeroInput {
    @required
    hero: Hero

    @httpQuery("verbose")
    verbose: Boolean

    powers: Powers

    powerMap: PowerMap

    friends: Friends

    intSet: IntSet

    friendSet: FriendSet

    hasNewtypes: HasNewtypes

    hasDeprecations: HasDeprecations

    doc: Document

    sparse: SampleSparseList

    sparseMap: SampleSparseMap
}

@uniqueItems
list FriendSet {
    member: Hero
}

list Friends {
    member: Hero
}

structure CreateHeroOutput {
    @httpPayload
    @required
    hero: Hero
}

union Hero {
    good: Good

    bad: Bad

    @deprecated(message: "No reason", since: "0.0.1")
    badder: Bad
}

structure Good {
    @required
    howGood: Integer
}

structure Bad {
    @required
    evilName: String

    @required
    powerLevel: Integer
}

@httpError(422)
@error("client")
structure HeroIsBad {
    @required
    powerLevel: Integer
}

@httpError(500)
@error("server")
structure GenericServerError {
    @required
    msg: String
}

@http(method: "GET", uri: "/powers")
@readonly
operation GetPowers {
    output: GetPowersOutput
}

structure GetPowersOutput {
    @httpPayload
    @required
    powers: Powers
}

list Powers {
    member: Power
}

map PowerMap {
    key: Power
    value: Hero
}

enum Power {
    ICE = "Ice"
    FIRE = "Fire"
    LIGHTNING = "Lightning"
    WIND = "Wind"
}

intEnum PrivacyTier {
    PUBLIC = 0
    PRIVATE = 1
}

@http(method: "PUT", uri: "/subscriptions")
@idempotent
@documentation(
    """
    Create a subscription.
    """
)
operation CreateSubscription {
    input := {
        @httpPayload
        @required
        subscription: Subscription
    }

    output := {
        @httpPayload
        @required
        subscription: Subscription
    }
}

structure Subscription {
    @required
    id: String

    name: String

    createdAt: Timestamp

    status: SubscriptionStatus

    skus: Skus

    next: Subscription
}

list Skus {
    member: Sku
}

structure Sku {
    @required
    id: Integer

    @required
    sku: String
}

enum SubscriptionStatus {
    ACTIVE
    INACTIVE
}

@indexedSeq
list Ints {
    member: Integer
}

@uniqueItems
list IntSet {
    member: Integer
}

structure HasNewtypes {
    intSet: IntSet
    myInt: MyInt
    str: MyString
    power: Power
    powerMap: PowerMap
    anUUID: UUID
    anInstant: MyInstant
    stringWithLength: StringWithLength
}

@instant
timestamp MyInstant

integer MyInt

string MyString

@length(min: 1)
string StringWithLength

structure EnumStruct {
    @length(max: 2)
    enumWithLength: EnumABC

    @pattern("^.{0,2}$")
    enumWithPattern: EnumABC

    @range(max: 2)
    intEnumWithRange: FaceCard
}

enum EnumABC {
    A
    AB
    ABC
}

intEnum FaceCard {
    JACK = 1
    QUEEN = 2
    KING = 3
    ACE = 4
    JOKER = 5
}

structure HasConstraintFields {
    @required
    minLength: StringWithLength
}

structure HasDeprecations {
    @deprecated(message: "Made-up reason")
    hasMessage: Boolean

    @deprecated(since: "0.1.0")
    @required
    hasSince: Boolean

    @deprecated(message: "Another reason", since: "1.0.0")
    @required
    hasBoth: Boolean
}

structure HasDefault {
    message: String = "test"
}

structure Person {
    @required
    name: String

    age: Integer
}

@mixin
structure SampleMixin {
    @required
    id: String
}

structure HasMixin with [SampleMixin] {
    @required
    name: String
}

@sparse
list SampleSparseList {
    member: Integer
}

@sparse
map SampleSparseMap {
    key: String
    value: Integer
}
