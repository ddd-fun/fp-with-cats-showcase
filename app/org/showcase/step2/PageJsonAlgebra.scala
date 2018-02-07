package org.showcase.step2

import scala.language.higherKinds

trait PageJsonAlgebra {

  def resolveParentPage(js:Json[EsPageResponse]) : ErrorOr[Json[Page]]

  def resolveComponents(js:Json[EsComponentsResponse]) : ErrorOr[Json[Components]]

  def mergeComponents(page: Json[Page], cmps: Json[Components]) : ErrorOr[Json[PageAndComponents]]

  def resolveImages(js:Json[EsImageResponse]) :  ErrorOr[Json[Images]]

  def mergeImages(js:Json[PageAndComponents], imgs:Json[Images]) : ErrorOr[Json[PageAndComponentsAndImages]]


  // how could we derive new functionality?

  def resolvePageAndComponents(page:Json[EsPageResponse], components:Json[EsComponentsResponse]) : ErrorOr[Json[PageAndComponents]] = {

    // given pageJson returned by successfully performed resolveParentPage(page)
    // given cmpJson returned by successfully performed resolveComponents(components)
    // given mergedJson returned by successfully performed mergeComponents(pageJson, cmpJson)
    // return mergedJson

    ???
  }

}


trait NaiveCombinator extends PageJsonAlgebra{

  // reminder: Either[A,B] =  Right[A] | Left[B]
  // Either captures result of calculation in:
  //  Right(json) => means json calculated without errors,
  //  Left(error) => means error happened during calculation

  override def resolvePageAndComponents(page:Json[EsPageResponse], components:Json[EsComponentsResponse]) : ErrorOr[Json[PageAndComponents]] = {

    val resolveParentPageResult:Either[Error, Json[Page]] = resolveParentPage(page)

    val resolveComponentsResult:Either[Error, Json[Components]] = resolveComponents(components)

    (resolveParentPageResult, resolveComponentsResult) match {
      case (Right(pageJosn), Right(cmpJson)) =>  mergeComponents(pageJosn, cmpJson)
      case (_, _) => Left("error: opps...")
    }

  }




  def resolvePageCmpImages(page:Json[EsPageResponse], components:Json[EsComponentsResponse], images: Json[EsImageResponse])  : ErrorOr[Json[PageAndComponentsAndImages]] = {

    val pageAndComponentsResult = resolvePageAndComponents(page, components)

    val imagesResult = resolveImages(images)

    (pageAndComponentsResult, imagesResult) match {
      case (Right(pgJson), Right(imgJson)) =>  mergeImages(pgJson, imgJson)
      case (_, _) => Left("error: opps...")
    }

  }

  // we have to repeat error handling logic over and over again ... more code - more maintenance (read, bugs, testing)
  // why do we do the second step if the first failed, we need fail-fast semantic

}


trait ForComprehensionCombinator extends PageJsonAlgebra{

  override def resolvePageAndComponents(page:Json[EsPageResponse], components:Json[EsComponentsResponse]) : ErrorOr[Json[PageAndComponents]] = {

    import cats.syntax.either._

    // this is very close to the pseudo code in PageJsonAlgebra#resolvePageAndComponents
    for{
      page <- resolveParentPage(page)
      cmps <- resolveComponents(components)
      both <- mergeComponents(page, cmps)
    } yield both

  }

}





trait FlatMapCombinator extends PageJsonAlgebra{


  override def resolvePageAndComponents(page:Json[EsPageResponse], components:Json[EsComponentsResponse]) : ErrorOr[Json[PageAndComponents]] = {
    //this import adds flatMap method to the Either
    import cats.syntax.either._

    // Either[L,A] flatMap  (A => Either[L,B]) = Either[L, B]
    // let's assume we got some Either[L,A]
    // and we have next peace of logic: A => Either[L,B]
    // apparently second step depends on A which is calculated by previous step

    resolveParentPage(page).flatMap{ page =>
      resolveComponents(components).flatMap{ cmps =>
        mergeComponents(page, cmps) // both
      }
    }

  }

}
