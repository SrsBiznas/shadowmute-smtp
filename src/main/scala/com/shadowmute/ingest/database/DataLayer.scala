package com.shadowmute.ingest.database

import java.util.UUID

import com.shadowmute.ingest.Logger
import com.shadowmute.ingest.mailbox.Recipient
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DataLayer {

  case class UserRecord(id: Long, key: UUID)

  case class RecipientRecord(
      ownerId: Long,
      mailbox: UUID,
      mailboxId: Option[Long]
  )

  class Users(tag: Tag) extends Table[UserRecord](tag, "users") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

    def key = column[UUID]("user_key")

    def * = (id, key) <> (UserRecord.tupled, UserRecord.unapply)
  }

  class Recipients(tag: Tag) extends Table[RecipientRecord](tag, "recipients") {
    def id = column[Option[Long]]("id", O.PrimaryKey, O.AutoInc)

    def ownerId = column[Long]("owner_id")

    def mailbox = column[UUID]("mailbox")

    def * =
      (ownerId, mailbox, id) <> (RecipientRecord.tupled, RecipientRecord.unapply)
  }

  def getAllRecipients(partition: Long = 0): Future[RecipientSet] = {
    val db = Database.forConfig("recipientDB")

    try {
      val query = for {
        (r, u) <- TableQuery[Recipients].filter(_.id.getOrElse(0L) > partition) join TableQuery[
          Users
        ] on (_.ownerId === _.id)
      } yield (r.mailbox, u.key, r.id)

      db.run(query.result)
        .map(records => {
          val rcpts = records.map {
            case (mb: UUID, uk: UUID, _) => Recipient(mb, uk)
          }

          // Travel through the records collecting the max while the arbitrary baggage
          // tags along
          val maxIndex: Option[Long] =
            if (records.isEmpty) None
            else {
              records
                .reduce[(UUID, UUID, Option[Long])] {
                  case ((x, y, a), (_, _, b)) =>
                    (x, y, List(a, b).flatten match {
                      case Nil => None
                      case xs  => Option(xs.max)
                    })
                }
                ._3
            }

          RecipientSet(rcpts, maxIndex.getOrElse(0L))
        })
        .recover {
          case exception: Exception =>
            Logger()
              .error("[!] Error connecting to accounts database:", exception)
            RecipientSet(Nil, -1)
        }
    } catch {
      case ex: Exception =>
        Logger().error("DB!", ex)
        Future.successful(RecipientSet(Nil, -1))
    } finally db.close
  }
}
