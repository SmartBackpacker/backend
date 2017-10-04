package com.github.gvolpe.smartbackpacker.parser

import cats.effect.Sync
import com.github.gvolpe.smartbackpacker.config.SBConfiguration
import com.github.gvolpe.smartbackpacker.model._
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.model.{Document, Element}
import net.ruippeixotog.scalascraper.scraper.HtmlExtractor

import scala.util.{Failure, Success, Try}

object WikiPageParser {
  def apply[F[_]: Sync]: WikiPageParser[F] = new WikiPageParser[F]
}

class WikiPageParser[F[_] : Sync] extends AbstractWikiPageParser[F] {

  override def htmlDocument(from: CountryCode): Document = {
    val browser = new JsoupBrowser()
    val wikiPage = SBConfiguration.wikiPage(from).getOrElse("http://google.com")
    browser.get(wikiPage)
  }

}

abstract class AbstractWikiPageParser[F[_] : Sync] {

  def htmlDocument(from: CountryCode): Document

  def visaRequirementsFor(from: CountryCode, to: CountryName): F[VisaRequirementsFor] = Sync[F].delay {
    parseVisaRequirements(from).find(_.country == to)
      .getOrElse(VisaRequirementsFor(to, UnknownVisaCategory, "No information available"))
  }

  // TODO: Whenever a colspan of 2 is present, concatenate the contents.
  // To handle special cases like the Irish wiki page containing a table of both 4 and 5 columns
  private def wikiTableExtractor: HtmlExtractor[Element, Iterable[String]] = _.flatMap { e =>
      Try(e.attr("colspan")) match {
        case Success(cs) if cs == "2" => Seq(e.text, "empty")
        case Success(_)               => Seq(e.text)
        case Failure(_)               => Seq(e.text)
      }
    }

  // TODO: Aggregate ".sortable" table with ".wikitable" table that for some countries have partially recognized countries like Kosovo
  // TODO: This will require add more visa categories (See Polish page)
  private def parseVisaRequirements(from: CountryCode): List[VisaRequirementsFor] = {
    val table = htmlDocument(from) >> extractor(".sortable td", wikiTableExtractor)
    val tableSize = htmlDocument(from) >> extractor(".sortable th", texts)

    table.toList.grouped(tableSize.size).map { seq =>
      VisaRequirementsFor(seq.head.asCountry, seq(1).asVisaCategory, seq(2).asDescription)
    }.toList
  }

}