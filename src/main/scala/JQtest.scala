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
          Thread.sleep(5000) // sleep 5 sec (to test async)
          s"$i"
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
  val testFetch = { i: Int => {
    val request = RequestBuilding.Get(s"/$i")
    testRequest(request).flatMap { response =>
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
