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
import scala.concurrent.{ExecutionContext, Future}
import scala.slick.lifted.TableQuery
import scala.slick.driver.PostgresDriver.simple._


object BikeService extends App {

case class Bike(id: Long, speed: Int, description: String)

class ShareMyBikeService(implicit val executionContext: ExecutionContext) {

  val connectionUrl = "jdbc:postgresql://localhost:5433/med?user=postgres&password=medamine"

  implicit val bikeFormat = jsonFormat3(Bike)

  Database.forURL(connectionUrl, driver = "org.postgresql.Driver") withSession {
    implicit session =>
      val bikes = TableQuery[Bikes]

      val bikesList: List[Bike] = {
        for {
          bike <- bikes.list
        } yield Bike(bike._1, bike._2, bike._3)
      }

      bikesList foreach { item =>
        println("bike with id " + item.id + " has speed " + item.speed + "with description: "
          + item.description)
      }


      def fetchBike(id: Long): Future[Option[Bike]] = Future {
        bikesList.find(_.id == id)
      }

      def saveBike(bike: Bike): Future[Option[Done]] = Future {
        bikesList.find(_.id == bike.id) match {
          case Some(q) => None // Conflict! id is already taken
          case None =>
            bike :: bikesList // just saved locally!
            Some(Done)
        }
      }

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
                  val saved: Future[Option[Done]] = saveBike(bike)
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