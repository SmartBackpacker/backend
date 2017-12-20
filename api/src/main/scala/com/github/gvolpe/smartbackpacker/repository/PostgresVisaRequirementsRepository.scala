package com.github.gvolpe.smartbackpacker.repository

import cats.MonadError
import cats.syntax.applicative._
import cats.syntax.applicativeError._
import cats.syntax.option.none
import com.github.gvolpe.smartbackpacker.model.{CountryCode, VisaRequirementsData}
import com.github.gvolpe.smartbackpacker.repository.algebra.VisaRequirementsRepository
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.util.invariant.UnexpectedEnd
import doobie.util.transactor.Transactor

class PostgresVisaRequirementsRepository[F[_]](xa: Transactor[F])
                                              (implicit F: MonadError[F, Throwable]) extends VisaRequirementsRepository[F] {

  override def find(from: CountryCode, to: CountryCode): F[Option[VisaRequirementsData]] = {
    val fromStatement: ConnectionIO[CountryDTO] =
      sql"SELECT * FROM countries WHERE code = ${from.value}"
        .query[CountryDTO].unique

    val toStatement: ConnectionIO[CountryDTO] =
      sql"SELECT * FROM countries WHERE code = ${to.value}"
        .query[CountryDTO].unique

    def visaRequirementsStatement(idFrom: Int, idTo: Int): ConnectionIO[VisaRequirementsDTO] =
      sql"SELECT vc.name AS category, vr.description FROM visa_requirements AS vr INNER JOIN visa_category AS vc ON vr.visa_category = vc.id WHERE vr.from_country = $idFrom AND vr.to_country = $idTo"
        .query[VisaRequirementsDTO].unique

    val program: ConnectionIO[VisaRequirementsData] =
      for {
        f <- fromStatement
        t <- toStatement
        v <- visaRequirementsStatement(f.head, t.head)
      } yield v.toVisaRequirementsData(f, t)

    program.map(Option.apply).transact(xa).recoverWith {
      case UnexpectedEnd => none[VisaRequirementsData].pure[F]
    }
  }

}