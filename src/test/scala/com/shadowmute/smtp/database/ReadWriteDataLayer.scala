package com.shadowmute.smtp.database

import slick.lifted.TableQuery
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class ReadWriteDataLayer() extends DataLayer {

  implicit val ec: ExecutionContext = ExecutionContext.global

  def deleteAll(): Future[Unit] = {
    val userTable = TableQuery[Users]
    val recipientTable = TableQuery[Recipients]

    val db = Database.forConfig("recipientDB")

    try {
      db.run(userTable.delete)
        .flatMap(_ => {
          db.run(recipientTable.delete)
            .map(_ => {
              ()
            })
        })
    } finally db.close
  }

  def createUserRecord(newUser: UserRecord): Future[UserRecord] = {
    val db = Database.forConfig("recipientDB")
    val table = TableQuery[Users]
    val insertQuery = (table returning table.map(_.id)) += newUser

    try {
      db.run(insertQuery).map(uid => newUser.copy(id = uid))
    } finally db.close()
  }

  def createRecipientRecord(newRecipient: RecipientRecord): Future[Long] = {
    val db = Database.forConfig("recipientDB")
    val table = TableQuery[Recipients]

    val recipientQuery = (table returning table.map(_.id)) += newRecipient

    try {
      db.run(recipientQuery).map(_.get)
    } finally db.close()
  }
}
