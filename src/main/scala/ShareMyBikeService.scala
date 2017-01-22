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
import scala.slick.lifted.TableQuery
import scala.slick.driver.PostgresDriver.simple._


object ShareMyBikeService {

    class Bikes(tag: Tag) extends Table[(Int, Int, String)](tag, "bikes") {
      def id = column[Int]("id")
      def speed = column[Int]("speed")
      def description = column[String]("description")
      def * = (id, speed, description)
    }


  def main(args: Array[String]): Unit = {

    implicit val system = ActorSystem()
    implicit val executor = system.dispatcher
    implicit val materializer = ActorMaterializer()


    val config = ConfigFactory.load()
    val logger = Logging(system, getClass)

    val connectionUrl = "jdbc:postgresql://localhost:5433/med?user=postgres&password=medamine"

    case class Bike(id: Long, speed: Int, description: String)

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


        def fetchBike(id: Long): Option[Bike] = {
          bikesList.find(_.id == id)
        }

        def saveBike(bike: Bike): Option[Done] =  {
          bikesList.find(_.id == bike.id) match {
            case Some(q) => None // Conflict! id is already taken
            case None =>
              bike :: bikesList // just saved locally!
              Some(Done)
          }
        }


        val route: Route =
          get {
            pathPrefix("bike" / LongNumber) { id =>
              val maybeBike: Option[Bike] = fetchBike(id)
              complete {
                Some(maybeBike)
              }
            }
          } ~
            post {
              path("create-bike") {
                entity(as[Bike]) { bike =>
                  val saved: Option[Done] = saveBike(bike)
                  complete {
                    Some(Done)
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