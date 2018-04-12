import io.circe.optics.JsonPath._
import io.circe._
import io.circe.syntax._
import cats.syntax.either._

val json: String = """
  {
    "id": "2432660",
    "title": "Audi page",
    "rating" : 3,
    "images": [
     {
     "id" : "aaaa",
     "title" : "hero"
     },{
      "id":"bbbb",
      "title" : "jumbo"
     }],
    "assets" : [
     {
     "sys" : { "type" : "image", "id" : "aaaa"},
     "link": "/assets/aaaaa"
     },{
     "sys" : { "type" : "image", "id" : "bbbb"},
     "link": "/assets/bbbbb"
     },{
     "sys" : { "type" : "pdf", "id": "cccc"},
     "link": "/assets/cccc"
     }
   ]
  }
"""
val doc : Json =  io.circe.parser.parse(json).getOrElse(Json.Null)

// get single string element
val _title = root.title.string

// get many elements
val _images = root.images.each.json

val _imagesTitle = _images composeOptional _title

_imagesTitle.getAll(doc) == root.images.each.title.string.getAll(doc)

//modify element
val upperCaseTitle = _title.modify(_.toUpperCase)

val upperCaseImageTitle = _imagesTitle.modify(_.toUpperCase)

// combine both
val upperCaseTitles = upperCaseTitle andThen upperCaseImageTitle

val upperCaseTitlesAndBumpUpRating = upperCaseTitles andThen root.rating.int.modify(_+1)

//add element
val addSize = _images.modify( _.deepMerge(Json.obj("size"-> "M".asJson)) )

val andThenAddSize = upperCaseTitlesAndBumpUpRating andThen addSize

// find element
val findImageAsset = (json:Json) => (id:String) => root.assets.each.json.find(root.sys.id.string.getOption(_).contains(id))(json)

// findImage in doc
val findImageAssetById: String => Option[Json] = findImageAsset(doc)

// zip two arrays by element id
val zipWithAssets = _images.modify{imgJson =>
  (for{
    id <- root.id.string.getOption(imgJson)
    as <- findImageAssetById(id)
  } yield imgJson.deepMerge(as))
  .getOrElse(imgJson.deepMerge(Json.obj("error" -> "could not resolve asset".asJson)))
}


// remove element
val removeAssets = root.obj.modify(obj => obj.remove("assets"))

val cleanImage = _images.modify(json => root.obj.getOption(json).map(_.remove("sys").asJson).getOrElse(json))

val zipWithAssetsAndCleanUp = zipWithAssets.andThen(removeAssets).andThen(cleanImage)


// filter array
val filterImageAssets = root.assets.each.json.modify{
  item =>
    root.sys.`type`.string.getOption(item).collect{
      case "image" => item
    }.getOrElse(Json.Null)
}

// compose all things together
val program = filterImageAssets andThen zipWithAssetsAndCleanUp andThen upperCaseTitlesAndBumpUpRating

program(doc)






