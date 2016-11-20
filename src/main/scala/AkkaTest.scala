import scala.util.{Success, Failure}
import scala.concurrent.Future
import akka.actor._
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.http._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.unmarshalling._
import akka.http.scaladsl.client._
import akka.stream.scaladsl.{Source, Sink}
import io.circe._
import io.circe.parser._
import io.circe.syntax._

/* Example of remote REST API to fetch data */
object RemoteAPI {
  val clientPort = 443 // https
  val clientHost = "api.jqestate.ru"
/* remote url */
  val apiUrl = "https://api.jqestate.ru/v1/properties/country"

/* pagination query */
  def pQuery(offset: Int, limit: Int): String = apiUrl + s"?pagination[offset]=$offset&pagination[limit]=$limit"

/* just one of pages to fetch common data */
  def onePage = pQuery(0, 0)
}

object AkkaTest {
/* akka magic implicits */
  implicit val system = ActorSystem("MagicActorSystem")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

/* https connection to REST API server */
  lazy val testConnection = Http().outgoingConnectionHttps(RemoteAPI.clientHost, RemoteAPI.clientPort)

  def getRequest(request: String): HttpRequest = RequestBuilding.Get(request)

/* send request stream pipeline */
  def sendRequest(request: HttpRequest): Future[HttpResponse] = {
    println(s"Send request: ${request.uri}")
    Source.single(request).via(testConnection).runWith(Sink.head)
  }

/* request first page and parse total number of pages */
 def paginationLength: Future[Int] = {
    val request: HttpRequest = getRequest(RemoteAPI.onePage)

    val response: Future[String] = sendRequest(request) flatMap { r =>
      Unmarshal(r.entity).to[String] }

    val json: Future[Json] = response flatMap ( x =>
        Future{ parse(x).getOrElse(Json.Null) })

    val resultDecoder: Future[Decoder.Result[Int]] = json flatMap ( j =>
        Future{ j.hcursor.downField("pagination").downField("total").as[Int] })

    val result: Future[Int] = resultDecoder flatMap ( x =>
        Future { x match {
          case cats.data.Xor.Right(i) => i
          case j  => throw new Exception("Can't parse!") }})

    result
  }

/* get response as a string */
  def fetchResponse(s: String): Future[String] = {
    val request: HttpRequest = getRequest(s)
    val response: Future[String] = sendRequest(request) flatMap { response =>
      Unmarshal(response.entity).to[String] }

    response
  }

/* prepare and send requests to REST API */
  def getRequestsStrings(n: Int): Future[List[String]] =  {
    val limit = 256 // elements per page
    val total: Future[Int] = paginationLength // total elements
    val requests: Future[List[String]] = total flatMap ((t:Int) => Future {
        // val n = t / limit + 1 // number of requests
        val requests = {0 to n}.map(x => RemoteAPI.pQuery(x*limit, limit))
        requests.toList
    })

    requests
  }

/* gather data from remote REST API */
  def clientTest(testNumberOfRequests: Int) = getRequestsStrings(testNumberOfRequests).onComplete({
    case Success(l) => {
      l.foreach(request => {
        fetchResponse(request).onComplete({
          case Success(s) => {
            println("Response: ", s slice (0, 42))
            system.terminate() }
          case Failure(e) => throw new Exception(e)
        })
      })
    }
    case Failure(e) => throw new Exception(e)
  })

/* start point */
  def main(args: Array[String]): Unit = {
    val testNumberOfRequests = 2
    clientTest(testNumberOfRequests)
  }
}
