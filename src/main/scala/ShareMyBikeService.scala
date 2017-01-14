import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import spray.json.DefaultJsonProtocol

case class Bike(weight: Option[Double], speed: Option[Double], description: Option[String])


trait Protocols extends DefaultJsonProtocol {

  implicit val bikeFormat = jsonFormat3(Bike.apply)

}

object ShareMyBikeService extends App {

  implicit val system = ActorSystem()
  implicit val executor = system.dispatcher
  implicit val materializer = ActorMaterializer()

  val config = ConfigFactory.load()
  val logger = Logging(system, getClass)

}

