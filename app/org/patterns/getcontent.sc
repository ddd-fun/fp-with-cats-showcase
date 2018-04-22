//@ import $ivy.`org.typelevel:cats-effect_2.12:0.10`
//@ import $ivy.`io.circe:circe-optics_2.12:0.9.3`

import cats.data.EitherT
import cats.syntax.all._
import cats.effect._

import io.circe.Json
import io.circe.optics.JsonPath._

import org.http4s.client.blaze.Http1Client

import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

type Error = String
type Html = String
type ErrorOr[A] = Either[String, A]

case class ContentLocator(space:String, id:String){
  def gerUri = s"/spaces/$space/environments/staging/entries/$id?access_token=f0aa56e3659d58947f8e3ddd6301c9591c9b73a8b54c35435233b18b3e6752d5"
}

object ContentLocator{

  val lngToSpaceMap = Map("de-DE"  -> "yadj1kx9rmg0")
  val pathToIdMap = Map("/product/stream-liner" -> "5KsDBWseXY6QegucYAoacS")

  def parse(path:String, lng:String) : ErrorOr[ContentLocator] = {
    for{
      space  <- Either.fromOption(lngToSpaceMap.get(lng), s"lng $lng not found")
      entity <- Either.fromOption(pathToIdMap.get(path), s"path $path not found")
    } yield ContentLocator(space, entity)
  }
}

def getContent(loc:ContentLocator) : Future[ErrorOr[Json]] = {
  import org.http4s.circe._
  val apiEndpoint = "https://preview.contentful.com"
  (for{
     client <- Http1Client[IO]()
     json   <- client.get(s"$apiEndpoint${loc.gerUri}"){_.as[Json]}
   } yield json).attempt.map(_.leftMap(th => s"error calling api: $th")).unsafeToFuture()
}

def renderContent(json:Json) : ErrorOr[Html] =
  Either.fromOption(root.fields.productName.string.getOption(json), s"could not parse json: root.fields.productName")
        .map(name => s"<h1>$name</h1>")

//low level imperative code is possible is scala, but:
// - doesn't scale at ALL >> Await bocks execution thread
// - will throw exception on runtime >> when deployed on prod >> result into OpcGenie Alert on 3:00am and so on..
// - every time when we want to re-ues it,  we have to keep in mind >> it could blowup!
// - harder to unit test >> mock it: when(getContent(..).thenReturn(Json)) >> every time you touch the code >> change the mock
def getHtml0(path: String, lng:String) : Html = {
 val loc = ContentLocator.parse(path, lng).right.get
 val json = Await.result(getContent(loc), 5.seconds).right.get
 val html = renderContent(json).right.get
 html
}

//drawbacks:
// - we could spot DRY issue here;
// - we don't see big picture Error handling is intermingled with main flow
// - we could not unit test program flow, unless we mocked getContent(...) when(Loc).then(Json)
def getHtml1(path: String, lng:String) : Future[ErrorOr[Html]] = {
  ContentLocator.parse(path, lng) match {
    case Right(loc) => getContent(loc).map{
      case Right(json) => renderContent(json)
      case Left(msg) => Left(msg)
    }
    case Left(msg) => Future.successful(Left(msg))
  }
}

def getHtml11(path: String, lng:String) : Future[ErrorOr[Html]] = {
   for{
     idE   <- Future.successful(ContentLocator.parse(path, lng))
     jsonE <- idE.fold(err => Future.successful(Left(err)), loc => getContent(loc))
     htmlE <- jsonE.fold(err => Future.successful(Left(err)), json => Future.successful(renderContent(json)))
   } yield htmlE
}

def getHtml2(path: String, lng:String) : Future[ErrorOr[Html]] = {
  import cats.instances.future._
  val tStack = for{
    uri  <- EitherT(ContentLocator.parse(path, lng).pure[Future])
    json <- EitherT(getContent(uri))
    html <- EitherT(renderContent(json).pure[Future])
  } yield html
  tStack.value
}

def getHtml22(path: String, lng:String) : Future[ErrorOr[Html]] = {
  import cats.instances.future._
  val tStack = for{
    uri  <- ContentLocator.parse(path, lng).toEitherT[Future]
    json <- EitherT(getContent(uri))
    html <- renderContent(json).toEitherT[Future]
  } yield html
  tStack.value
}

Await.result(getHtml1("/product/stream-liner", "de-DE"), 5.seconds)