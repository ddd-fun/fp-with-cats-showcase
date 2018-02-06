package org.showcase

import play.api.libs.json.JsValue


package object stage1 {

  //type aliases
  type Error = String
  type ErrorOr[A] = Either[Error, A]

  type Json[A] = JsValue with A // adds additional info to the JsValue type

  //phantom types aka marker interface
  trait EsRawResponse
  trait Page
  trait Components
  trait PageAndComponents
  trait Images
  trait PageAndComponentsAndImages


}
