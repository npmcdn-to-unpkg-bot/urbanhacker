package services

import java.net.URI

import com.google.inject.Inject
import model._
import play.api.Logger
import play.api.db.slick.DatabaseConfigProvider
import slick.driver.JdbcProfile
import slick.driver.PostgresDriver.api._

import scala.concurrent.{ExecutionContext, Future}

class FeedStore @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit exec: ExecutionContext) {
  val dbConfig = dbConfigProvider.get[JdbcProfile]
  val db = dbConfig.db
  Logger.info(downloads.schema.create.statements.mkString("\n"))

  def load(url: URI): Future[Seq[TextDownload]] = {
    db.run(downloads.filter(_.url === url.toString).sortBy(_.url).result)
  }

  def save(download: TextDownload): Future[Boolean] = {
    db.run(downloads += download).map(_ == 1)
  }
}
