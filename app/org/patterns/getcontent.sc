//@ import $ivy.`org.typelevel:cats-core_2.12:1.1.0`
//@ import $ivy.`org.typelevel:cats-effect_2.12:1.0.0-RC`
//@ import $ivy.`io.circe:circe-optics_2.12:0.9.3`
//@ import $ivy.`org.http4s:http4s-blaze-client_2.12:0.18.9`
//@ import $ivy.`org.http4s:http4s-circe_2.12:0.18.9`

import cats.{MonadError, Monad}
import cats.data.{EitherT, Reader}
import cats.syntax.all._
import cats.instances.future._
import cats.effect._
import io.circe.Json
import io.circe.optics.JsonPath._
import org.http4s.client.blaze.Http1Client

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.higherKinds

type Error = String
type Html = String
type ErrorOr[A] = Either[String, A]

case class ContentUri(space:String, id:String){
  def gerUri = s"/spaces/$space/environments/staging/entries/$id?access_token=f0aa56e3659d58947f8e3ddd6301c9591c9b73a8b54c35435233b18b3e6752d5"
}

object ContentUri{

  val lngToSpaceMap = Map("de-DE"  -> "yadj1kx9rmg0")
  val pathToIdMap = Map("/product/stream-liner" -> "5KsDBWseXY6QegucYAoacS")

  def parse(path:String, lng:String) : ErrorOr[ContentUri] = {
    for{
      space  <- Either.fromOption(lngToSpaceMap.get(lng), s"lng $lng not found")
      entity <- Either.fromOption(pathToIdMap.get(path), s"path $path not found")
    } yield ContentUri(space, entity)
  }
}

def getContent(loc:ContentUri) : Future[ErrorOr[Json]] = {
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
 val loc = ContentUri.parse(path, lng).right.get
 val json = Await.result(getContent(loc), 5.seconds).right.get
 val html = renderContent(json).right.get
 html
}

//drawbacks:
// - we could spot DRY issue here;
// - we don't see big picture Error handling is intermingled with main flow
// - we could not unit test program flow, unless we mocked getContent(...) when(Loc).then(Json)
def getHtml1(path: String, lng:String) : Future[ErrorOr[Html]] = {
  ContentUri.parse(path, lng) match {
    case Right(loc) => getContent(loc).map{
      case Right(json) => renderContent(json)
      case Left(msg) => Left(msg)
    }
    case Left(msg) => Future.successful(Left(msg))
  }
}

def getHtml11(path: String, lng:String) : Future[ErrorOr[Html]] = {
   for{
     idE   <- Future.successful(ContentUri.parse(path, lng))
     jsonE <- idE.fold(err => Future.successful(Left(err)), loc => getContent(loc))
     htmlE <- jsonE.fold(err => Future.successful(Left(err)), json => Future.successful(renderContent(json)))
   } yield htmlE
}

def getHtml2(path: String, lng:String) : Future[ErrorOr[Html]] = {
  val tStack = for{
    uri  <- EitherT(ContentUri.parse(path, lng).pure[Future])
    json <- EitherT(getContent(uri))
    html <- EitherT(renderContent(json).pure[Future])
  } yield html
  tStack.value
}

def getHtml22(path: String, lng:String) : Future[ErrorOr[Html]] = {
  val tStack = for{
    uri  <- ContentUri.parse(path, lng).toEitherT[Future]
    json <- EitherT(getContent(uri))
    html <- renderContent(json).toEitherT[Future]
  } yield html
  tStack.value
}

def getHtml33(path: String, lng:String)(implicit ME:MonadError[Future, Throwable]) : Future[ErrorOr[Html]] = {
  val meStack = for{
     uri  <- ME.fromEither(ContentUri.parse(path, lng).leftMap(msg => new RuntimeException(msg)))
     json <- ME.rethrow(getContent(uri).map(_.leftMap(msg => new RuntimeException(msg))))
     html <- ME.fromEither(renderContent(json).leftMap(msg => new RuntimeException(msg)))
  } yield html
  meStack.attempt.map(_.leftMap(th => th.getMessage()))
}


// now it's time to make it easier to test
trait GetContent[F[_]]{
  def doGet(contentLocator: ContentUri) : F[ErrorOr[Json]]
}

object GetContent{
  def apply[F[_]](implicit inst:GetContent[F]) : GetContent[F] = inst
  implicit val getContentFutureInst = new GetContent[Future] {
    override def doGet(contentLocator: ContentUri): Future[ErrorOr[Json]] = {
      getContent(contentLocator)
    }
  }
}


def getHtml4[F[_]: Monad : GetContent](path: String, lng:String) : F[ErrorOr[Html]] = {
  val tStack = for{
    uri  <- ContentUri.parse(path, lng).toEitherT[F]
    json <- EitherT(GetContent[F].doGet(uri))
    html <- renderContent(json).toEitherT[F]
  }yield html
  tStack.value
}


object UnitTest {

  type TestUriMap = Map[ContentUri, Json]
  type Mocked[A] = Reader[TestUriMap, A]

  def run = getHtml4[Mocked]("/product/stream-liner", "de-DE").apply(Map.empty[ContentUri, Json])

  implicit val getContentMockedInstance = new GetContent[Mocked] {
    override def doGet(contentLocator: ContentUri): Mocked[ErrorOr[Json]] = {
      Reader[TestUriMap, ErrorOr[Json]]{ map =>
        Either.fromOption(map.get(contentLocator), s"no found by $contentLocator") }
    }
  }
}


case class Mocked[A](reader:Reader[Mocked.Data, A])
object Mocked{
  type Data = Map[ContentUri, Json]

  def apply[A](map: Data => A) : Mocked[A]  = Mocked(Reader(map))

  implicit val getContentMockedInstance = new GetContent[Mocked] {
    override def doGet(contentLocator: ContentUri): Mocked[ErrorOr[Json]] = {
      Mocked[ErrorOr[Json]]{ map:Data =>
        Either.fromOption(map.get(contentLocator), s"no found by $contentLocator") }
    }
  }
  // add here more instances to your Mockery

  type ReaderData[A] = Reader[Data, A]
  implicit def monadInstance(implicit RM:Monad[ReaderData]) : Monad[Mocked] = new Monad[Mocked] {
    override def pure[A](x: A): Mocked[A] = Mocked(RM.pure(x))
    override def flatMap[A, B](fa: Mocked[A])(f: (A) => Mocked[B]): Mocked[B] = Mocked(RM.flatMap(fa.reader)((a:A) => f(a).reader))
    override def tailRecM[A, B](a: A)(f: (A) => Mocked[Either[A, B]]): Mocked[B] = Mocked(RM.tailRecM(a)((a:A)=> f(a).reader))
  }

}

getHtml4[Mocked]("/product/stream-liner", "de-DE")

Await.result(getHtml4[Future]("/product/stream-liner", "de-DE"), 5.seconds)