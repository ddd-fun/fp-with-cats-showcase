import cats.data.Const
import cats.effect._

import org.http4s.client.blaze._
import org.http4s.client.Client
import org.http4s.circe._

import io.circe._
import io.circe.optics.JsonPath._
import io.circe.syntax._

import scala.language.higherKinds


val httpClient : IO[Client[IO]]  = Http1Client[IO]()
val entriesUrl = "https://preview.contentful.com/spaces/yadj1kx9rmg0/entries?access_token=f0aa56e3659d58947f8e3ddd6301c9591c9b73a8b54c35435233b18b3e6752d5&include=2&limit=2"
val assetUrlPattern = "https://preview.contentful.com/spaces/yadj1kx9rmg0/assets/asset-id?access_token=f0aa56e3659d58947f8e3ddd6301c9591c9b73a8b54c35435233b18b3e6752d5"
val assetIdRegexp = "asset-id".r
val assetUrl = (id:String) => "asset-id".r.replaceFirstIn(assetUrlPattern, id)

def getJson(url:String) : IO[Json] = for{
  client  <- httpClient
  body    <- client.get(url){_.as[Json]}
}yield body

//well cats to scalaz converter
implicit def scalazApplicative[F[_]: cats.Applicative] : scalaz.Applicative[F] = {
  new scalaz.Applicative[F] {
    override def point[A](a: => A): F[A] = cats.Applicative[F].point(a)
    override def ap[A, B](fa: => F[A])(f: => F[(A) => B]): F[B] = cats.Applicative[F].ap(f)(fa)
  }
}

val zipWithAsset =
for{
  json <- getJson(entriesUrl)
  zip  <- root.items.each.fields.image.each.json.modifyF[IO]{imgJson =>
            root.sys.id.string.getOption(imgJson).map{id =>
               getJson(assetUrl(id)).map(imgJson.deepMerge)
            }.getOrElse(IO.pure(imgJson))
          }(json)
} yield zip


//getJson(entriesUrl).unsafeRunSync()

zipWithAsset.unsafeRunSync()











