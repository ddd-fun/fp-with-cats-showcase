package org.showcase.step1

import play.api.libs.json.JsValue


trait ExplicitFunctions1 {


  def resolveParentPage(js:JsValue) : Either[String, JsValue]


  def resolveComponents(js:JsValue) : Either[String, JsValue]


  def mergePageAndComponents(page: JsValue, cmps: JsValue) : Either[String, JsValue]


  // okeish, but left String clutters signatures

}


trait ExplicitFunctions2 {

  type Error = String //our naive error type
  type ErrorOr[A] = Either[Error, A]

  def resolveParentPage(js:JsValue) : ErrorOr[JsValue]

  def resolveComponents(js:JsValue) : ErrorOr[JsValue]

  def mergePageAndComponents(page: JsValue, cmps: JsValue) : ErrorOr[JsValue]


}

trait ExplicitFunctions3 {

  //type aliases
  type Error = String
  type ErrorOr[A] = Either[Error, A]

  type Json[A] = JsValue with A // adds additional info to the JsValue type

  //phantom types... aka marker interfaces.
  trait EsRawResponse
  trait Page
  trait Components
  trait PageAndComponents


  def resolveParentPage(js:Json[EsRawResponse]) : ErrorOr[Json[Page]]

  def resolveComponents(js:Json[EsRawResponse]) : ErrorOr[Json[Components]]

  def mergePageAndComponents(page: Json[Page], cmps: Json[Components]) : ErrorOr[Json[PageAndComponents]]


}