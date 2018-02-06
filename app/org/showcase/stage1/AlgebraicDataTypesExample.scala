package org.showcase.stage1

trait AlgebraicDataTypesExample {

  //every case class is explicit about its intent and contain ALWAYS valid data, aka preserves invariant.
  trait Picture
  case class OneSize(link: String) extends Picture
  case class SMSize(sLink: String, mLink: String) extends Picture
  case class SMLSize(sLink: String, mLink: String, lLink: String) extends Picture
  case object Default extends Picture

  // VS no type

  case class SomePicture(s:Option[String], m:Option[String], l:Option[String])

  // welcome the bug into our system
  val invalidSandLSizes = SomePicture(Some("/images/bmw-small.gif"), None, Some("/images/bmw-large.gif"))


  // link: String  - for demo only...  well... find a better way to model link :-)

}
