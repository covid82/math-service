package org.parseq.mathservice

import cats.Parallel
import cats.data.{Kleisli, OptionT}
import cats.effect.{Clock, ConcurrentEffect, ExitCode, IO, IOApp, Resource, Sync, Timer}
import cats.syntax.functor._
import io.jaegertracing.Configuration.{ReporterConfiguration, SamplerConfiguration}
import io.prometheus.client.CollectorRegistry
import natchez.Tags.http
import natchez.jaeger.Jaeger
import natchez.{EntryPoint, Kernel, Span}
import org.http4s
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.metrics.prometheus.{Prometheus, PrometheusExportService}
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Metrics
import org.http4s.server.{Router, Server}
import org.http4s.{EntityDecoder, Header, HttpApp, HttpRoutes, Request, Response}
import org.parseq.mathservice.expression.Expr

import scala.concurrent.ExecutionContext.global
import scala.language.higherKinds
import scala.util.Random

object Main extends IOApp {

  def spannedClient[F[_] : Sync : Span : Client]: Client[F] = {
    val span = implicitly[Span[F]]
    val client =implicitly[Client[F]]
    Client[F](request =>
      for {
        s <- span.span(s"http4s-request")
        //        _      <- Resource.liftF(s.put(span.kind("client")))
        _ <- Resource.liftF(s.put(http.method(request.method.name)))
        _ <- Resource.liftF(s.put(http.url(request.uri.renderString)))
        kernel <- Resource.liftF(s.kernel)
        c <- client.run(request.transformHeaders(headers => headers ++ http4s.Headers(kernel.toHeaders.toList.map(b => Header(b._1, b._2)))))
        _ <- Resource.liftF(s.put(http.status_code(c.status.code.toString)))
      } yield c
    )
  }

  def httpGetString[F[_] : ConcurrentEffect : Span : Client, A](url: String)(implicit d: EntityDecoder[F, A]): F[String] =
    spannedClient.expect[String](url)

  def calculate[F[_] : ConcurrentEffect : Span : Client](expr: Expr)(implicit e: EntityDecoder[F, String]): F[String] = {
    object dsl extends Http4sClientDsl[F]
    import dsl._
    import org.http4s.Method._
    import io.circe.generic.auto._
    import io.circe.syntax._
    import org.http4s.circe.CirceEntityCodec._
    spannedClient.expect(POST(expr.asJson, uri"http://math-service:8082/api/calculate"))(implicitly[EntityDecoder[F, String]])
  }

  def api[F[_] : ConcurrentEffect : Parallel](ep: EntryPoint[F], client: Client[F]): Kleisli[OptionT[F, *], Request[F], Response[F]] = {
    object dsl extends Http4sDsl[F]
    import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / "health" => Ok("Ok")
      case req@GET -> Root / "random" / IntVar(limit) => span(s"random($limit)")(req, ep).use { _ =>
        Ok(Random.nextInt(limit).toString)
      }
      case req@GET -> Root / "sum" / IntVar(x) / IntVar(y) => span(s"sum($x+$y)")(req, ep).use { _ =>
        Ok((x + y).toString)
      }
      case req@POST -> Root / "calculate" => span(s"calculate")(req, ep).use { implicit span =>
        import io.circe.generic.auto._
        import org.http4s.circe.CirceEntityCodec._
        import cats.syntax.flatMap._
        import expression._
        implicit val c: Client[F] = client

        for {
          expr <- req.as[Expr]
          result: F[String] = {
            import cats.syntax.applicative._
            expr match {
              case Rand(max) =>
                Random.nextInt(max).toString.pure
              case Add(l, r) =>
                import cats.syntax.parallel._
                val q = (calculate(l), calculate(r)).parTupled.map { case (a, b) =>
                  (a.toInt + b.toInt).toString
                }
                q
            }
          }
          s <- result
          x <- Ok(s)
        } yield x
      }
    }
  }

  def span[F[_]](name: String)(req: Request[F], ep: EntryPoint[F]): Resource[F, Span[F]] =
    ep.continueOrElseRoot(name, Kernel(req.headers.toList.map(h => h.name.value -> h.value).toMap))

  def server[F[_] : ConcurrentEffect : Timer](routes: HttpApp[F]): Resource[F, Server[F]] = BlazeServerBuilder[F]
    .withHttpApp(routes)
    .bindHttp(8080, "0.0.0.0")
    .resource

  def entryPoint[F[_] : Sync]: Resource[F, EntryPoint[F]] = Jaeger.entryPoint[F]("math-service") { conf =>
    Sync[F].delay(conf
      .withSampler(SamplerConfiguration.fromEnv())
      .withReporter(ReporterConfiguration.fromEnv())
      .getTracer
    )
  }

  implicit val clock: Clock[IO] = Clock.create[IO]

  def meteredRoutes[F[_] : ConcurrentEffect : Clock](routes: HttpRoutes[F], registry: CollectorRegistry): Resource[F, HttpRoutes[F]] =
    Prometheus.metricsOps[F](registry, "http4s_app").map(ops => Metrics[F](ops)(routes))

  def client[F[_] : ConcurrentEffect]: Resource[F, Client[F]] = {
    BlazeClientBuilder[F](global).resource
  }

  /**
   * http://127.0.0.1:28080/api/hello/X
   * http://127.0.0.1:28080/metrics
   */
  override def run(args: List[String]): IO[ExitCode] = {
    for {
      entryPoint <- entryPoint[IO]
      prometheus <- PrometheusExportService.build[IO]
      client <- client[IO]
      apiRoutes <- meteredRoutes[IO](api(entryPoint, client), prometheus.collectorRegistry)
      server <- server[IO](Router(
        "/" -> prometheus.routes,
        "/api" -> apiRoutes
      ).orNotFound)
    } yield server
  }.use(_ => IO.never) as ExitCode.Success
}