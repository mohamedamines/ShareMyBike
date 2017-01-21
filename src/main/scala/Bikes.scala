import scala.slick.driver.PostgresDriver.simple._

  class Bikes(tag: Tag) extends Table[(Int, Int, String)](tag, "bikes") {
    def id = column[Int]("id")
    def speed = column[Int]("speed")
    def description = column[String]("description")
    def * = (id, speed, description)
  }