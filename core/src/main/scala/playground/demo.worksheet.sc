import playground.Tokens

import playground.SmithyQLParser
val input = """
CreateHero { //bar
hero = { //foo
//    bad = {
//      evilName = "evil",
//      powerLevel = 9001,
//    },
    good = {
      howGood = //100
      //  200,
      200,
      anotherKey//foo = "a"
        = 42,
    },
  },
}
"""

SmithyQLParser.parser(Tokens.idTokens).parseAll(input).toOption.get
SmithyQLParser.parser(Tokens.lexerTokens).parseAll(input).toOption.get.getConst

SmithyQLParser.parser(Tokens.withSourceTokens).parseAll(input).toOption.get
