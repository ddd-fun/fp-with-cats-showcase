package org.showcase.stage1



trait PageJsonAlgebra {

  def resolveParentPage(js:Json[EsRawResponse]) : ErrorOr[Json[Page]]

  def resolveComponents(js:Json[EsRawResponse]) : ErrorOr[Json[Components]]

  def mergeComponents(page: Json[Page], cmps: Json[Components]) : ErrorOr[Json[PageAndComponents]]

  def resolveImages(js:Json[EsRawResponse]) :  ErrorOr[Json[Images]]

  def mergeImages(js:Json[PageAndComponents], imgs:Json[Images]) : Json[PageAndComponentsAndImages]

}


// Hints!
// Let the types to drive local reasoning!
// Method name could lie, it may have implemented not mentioned side effect for ex: throwing exception, calling ws, or saving stuff into db.
// Pure FP requires extreme explicitly about data transformation and side effects, which enables much better readability nad re-usability.

// Theory!
// A pure function is a function that depends only on its declared input parameters and its algorithm to produce its output.
// It does not read any other values from “the outside world” — the world outside of the function’s scope — and it does not modify any values in the outside world.
// =======> Output depends only on input! <============= and no Side Effects!

