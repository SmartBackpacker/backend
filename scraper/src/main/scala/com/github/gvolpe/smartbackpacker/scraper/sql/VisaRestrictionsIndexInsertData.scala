/*
 * Copyright 2017 Smart Backpacker App
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.gvolpe.smartbackpacker.scraper.sql

import cats.effect.Async
import cats.instances.list._
import cats.syntax.functor._
import com.github.gvolpe.smartbackpacker.model._
import doobie.implicits._
import doobie.util.transactor.Transactor
import doobie.util.update.Update

class VisaRestrictionsIndexInsertData[F[_] : Async](xa: Transactor[F]) {

  type CreateVisaIndexDTO = (String, Int, Int, Int)

  private def insertVisaIndexBulk(list: List[(CountryCode, VisaRestrictionsIndex)]) = {
    val sql = "INSERT INTO visa_restrictions_index (country_code, rank, acc, sharing) VALUES (?, ?, ?, ?)"
    val dtoList: List[CreateVisaIndexDTO] = list.map { case (code, index) =>
      (code.value, index.rank.value, index.count.value, index.sharing.value)
    }
    Update[CreateVisaIndexDTO](sql).updateMany(dtoList)
  }

  def run(list: List[(CountryCode, VisaRestrictionsIndex)]): F[Unit] = {
    insertVisaIndexBulk(list).transact(xa).map(_ => ())
  }

}
