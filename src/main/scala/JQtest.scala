import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.server.PathMatchers.IntNumber
import akka.http.scaladsl.server.Directives._
import akka.http._
import scala.util.{Success, Failure}
import akka.actor._
import akka.stream.scaladsl.Flow
import akka.http.scaladsl.client._
import akka.http.scaladsl.model.StatusCodes._
import io.circe._
import io.circe.parser._
import scala.collection.mutable.HashMap
import akka.stream.scaladsl.{ Source, Sink  }

object HelloWorld {

  implicit val system = ActorSystem("Tea")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val serverPort = 8080
  val clientPort = 8080
  val serverHost = "localhost"
  val clientHost = "localhost"
  val nRequests = 42
  val http = Http()

  val JQApiUrlPropertiesCountry = "https://api.jqestate.ru/v1/properties/country"

  val getPaginationQuery = { offset: Int, limit: Int => {
    return JQApiUrlPropertiesCountry + s"?pagination[offset]=$offset&pagination[limit]=$limit"
  }}

  val getTotalFromResponse = (response) => {
      val string = Unmarshal(response.entity).to[String] // get body string (will be json soon)
      val doc: Json = parse(string).getOrElse(Json.Null)
      val cursor: HCursor = doc.hcursor
      cursor.downField("pagination").downField("total").as[Int]
  }

  val getTotal = () => {
    val request = getPaginationQuery(0, 0)
    val response = sendRequest(request)
    getTotalFromResponse(response)
  }

  val getRequestsStrings = () => {
    val total = getTotal()
    val limit = 256 // max for this server
    val n = total / limit + 1 // number of requests
    val requests = {1 to n}.map( x => getPaginationQuery((x - 1) * limit, limit))
    requests
  }


  // server roots
  val route = get {
    pathSingleSlash {
      complete {
          "Hello world!"
      }
    } ~
    pathPrefix(IntNumber) { i =>
      complete {
        Future {
          jsonList(i).nospaces
        }
      }
    }
  }

  // stream flow
  val testConnection = http.outgoingConnection(clientHost, clientPort)

  // Stream pipeline: Source(request) | Flow | Sink
  val testRequest = { request: HttpRequest => {
    println(s"Request: ${request.uri}")
    Source.single(request).via(testConnection).runWith(Sink.head)
  }}

  // request to route '/<i:int>' and get int in response
  val testFetch = { request: String => {
    val requestGet = RequestBuilding.Get(request)
    testRequest(requestGet).flatMap { response =>
      Unmarshal(response.entity).to[String] // get body string (will be json soon)
    }
  }}

  def main(args: Array[String]): Unit = {
    println(s"Start server at $serverHost:$serverPort")
    Http().bindAndHandle(route, serverHost, serverPort) // run server

    {1 to nRequests}.foreach(i => { // send test requests
      testFetch(i) onSuccess {
        case s: String => println(s"Response: $s")
      }
    })
  }
}
