package playground

import cats.effect.Resource
import cats.effect.implicits._
import cats.effect.kernel.Async
import cats.implicits._
import demo.smithy.CreateHeroOutput
import demo.smithy.CreateSubscriptionOutput
import demo.smithy.DemoService
import demo.smithy.GetPowersOutput
import demo.smithy.Hero
import demo.smithy.Power
import demo.smithy.Subscription
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import smithy4s.http4s.SimpleRestJsonBuilder
import fs2.io.net.tls.TLSContext
import fs2.io.net.tls.SecureContext

object client {

  def make[F[_]: Async](useNetwork: Boolean): Resource[F, Client[F]] = {
    val fakeClient = SimpleRestJsonBuilder
      .routes {
        new DemoService[F] {
          def createHero(
            hero: Hero,
            verbose: Option[Boolean],
          ): F[CreateHeroOutput] = CreateHeroOutput(hero).pure[F]

          def createSubscription(subscription: Subscription): F[CreateSubscriptionOutput] =
            CreateSubscriptionOutput(subscription).pure[F]

          def getPowers(): F[GetPowersOutput] = GetPowersOutput(List(Power.FIRE, Power.ICE)).pure[F]
        }
      }
      .resource
      .map(_.orNotFound)
      .map(Client.fromHttpApp(_))

    {
      if (useNetwork)
        Async[F]
          .delay(
            // todo: use facade
            TLSContext
              .Builder
              .forAsync[F]
              .fromSecureContext(
                SecureContext
                  .fromJS(
                    scalajs
                      .js
                      .Dynamic
                      .global
                      .require("tls")
                      .applyDynamic("createSecureContext")(
                        scalajs
                          .js
                          .Object
                          .fromEntries(
                            scalajs
                              .js
                              .Array(
                                scalajs
                                  .js
                                  .Tuple2(
                                    "_vscodeAdditionalCaCerts",
                                    scalajs
                                      .js
                                      .Array
                                      .apply(),
                                  )
                              )
                          )
                      )
                  )
              )
          )
          // Network[F].tlsContext.system
          .toResource
          .flatMap { tls =>
            EmberClientBuilder.default[F].withTLSContext(tls).build
          }
      else
        fakeClient
    }
  }

}
