import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.Done
import akka.event.Logging
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import com.typesafe.config.ConfigFactory
import spray.json.DefaultJsonProtocol._

import scala.io.StdIn
import scala.concurrent.Future
import scala.slick.lifted.TableQuery
import scala.slick.driver.PostgresDriver.simple._

object ShareMyBikeService {

  val connectionUrl = "jdbc:postgresql://localhost:5433/med?user=postgres&password=medamine"

  case class Bike(id: Long, speed: Int, description: String)

  implicit val bikeFormat = jsonFormat3(Bike)

  Database.forURL(connectionUrl, driver = "org.postgresql.Driver") withSession {
    implicit session =>
      val bikes = TableQuery[Bikes]

      def fetchBike(id: Long): Future[Option[Bike]] = ???

      def saveBike(bike: Bike): Future[Done] = ???

      def main(args: Array[String]) {

        implicit val system = ActorSystem()
        implicit val executor = system.dispatcher
        implicit val materializer = ActorMaterializer()

        val config = ConfigFactory.load()
        val logger = Logging(system, getClass)

        val route: Route =
          get {
            pathPrefix("bike" / LongNumber) { id =>
              val maybeBike: Future[Option[Bike]] = fetchBike(id)

              onSuccess(maybeBike) {
                case Some(item) => complete(item)
                case None => complete(StatusCodes.NotFound)
              }
            }
          } ~
            post {
              path("create-bike") {
                entity(as[Bike]) { bike =>
                  val saved: Future[Done] = saveBike(bike)
                  onComplete(saved) { done =>
                    complete("bike saved")

                  }
                }
              }

            }

        val bindingFuture = Http().bindAndHandle(route, config.getString("http.interface"), config.getInt("http.port"))

        println(s"Server online at http://localhost:9000/\nPress RETURN to stop...")
        StdIn.readLine()
        bindingFuture
          .flatMap(_.unbind())
          .onComplete(_ => system.terminate())
      }
  }
}