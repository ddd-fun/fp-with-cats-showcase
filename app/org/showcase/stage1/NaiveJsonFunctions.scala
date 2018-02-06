package org.showcase.stage1

import play.api.libs.json.JsValue


trait NaiveJsonFunctions {

  // resolve page element by adding system properties to custom ones, for ex: result = { "page" : {... all custom properties..., "contenttype": "taken from sys props"}  }
  // for more details, please see /docker/tooling/wiremock/__files/parent.json


  def resolveParentPage(js:JsValue) : JsValue = {
    // read this body!
    // if you want to know that it might fail
    // and elastic search raw response is going to be converted into page json
    ???
  }


  def resolveComponents(js:JsValue) : JsValue

  // using this function, we might mess up the args order
  def mergePageAndComponents(page: JsValue, cmps: JsValue) : JsValue


  // This method/function signature tell us very little about its functionality.
  // 1. Parsing json is error-prone operation, so it could fail.
  //    Since method signature doesn't tell us about this fact, we could not reason about it locally,
  //    In other words, we have to drill down into method implementation in order to make sense of its functionality.
  // 2. Input and output is generic json value which could lead to functions misuse



}


