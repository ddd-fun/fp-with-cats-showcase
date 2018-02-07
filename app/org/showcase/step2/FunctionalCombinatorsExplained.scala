package org.showcase.step2

import scala.util.Try
import scala.language.higherKinds

trait FunctionalCombinatorsExplained {

  //let's consider two functions:
  val parse:Function1[String, Int] = str => str.toInt

  val divide:Function1[Int, Double] = int => 1.0 / int

  //let's derive new one based on composition of ones above:

  // String => Int  andThen  Int => Double  produce new function  String => Double
  val func3:Function1[String, Double] = parse andThen divide

  // how about?
  parse("bla-bla")

  // or?
  divide(0)

  // how about this?
  func3("0")
  //  In order to understand what might happen here, we have to drill down and then
  // figure out all details about what our func3 is composed from? and How?
  // this dramatically reduces local reasoning and make no sense of composition at all!



  //let's define safe version of our functions:

  val safeParser:Function1[String, Option[Int]] = str => Try(str.toInt).toOption

  val safeDivide:Function1[Int, Option[Double]] = int => Try(1.0 / int).toOption

  //given:
  // 1. String => Option[Int]
  // 2. Int => Option[Double]
  // let's compose them into new function:
  // String => Option[Int] andThen Int => Option[Double] produces String => Option[Double]

  val composition : Function1[String, Option[Double]] = str => {
    safeParser(str).flatMap(int => safeDivide(int))
  }

  // As far as we got String => Option[Double], we don't care about composition details,
  // we just know it might return no result!


  // What is flatMap?

  // Abstract definition of flatMap
  // F[A] flatMap (A => F[B]) == F[B]


  trait FlatMap[F[_]] {
    def flatMap[A,B](fa:F[A])(continuation: A => F[B]) : F[B]
  }

  object OptionFlatMappable extends FlatMap[Option] {
    override def flatMap[A, B](fa: Option[A])(continuation: (A) => Option[B]): Option[B] = {
       fa match {
         case Some(a) => continuation.apply(a) //if fa does contain some value then go on and apply it on continuation
         case None => None  // if fa doesn't contain value then interrupt and return None
       }
    }
  }

  // and implementation for our ErrorOr[A]
  object ErrorOrFlatMappable extends FlatMap[ErrorOr] {
    override def flatMap[A, B](fa: ErrorOr[A])(continuation: (A) => ErrorOr[B]): ErrorOr[B] = {
      fa match {
        case Right(a) => continuation.apply(a) //if fa does contain some value then go on and apply it on continuation
        case Left(msg) => Left(msg)  // if fa doesn't contain value then interrupt and return Left
      }
    }
  }

  //this is very common pattern in functional programming and it's already implemented
  //check out implementations here

  // and add it if you want to have flatMap method attached to your Either


  // Is it valuable knowledge? Will it help me to maintain value of my programming skills?
  // okay..

  // if you use node.js:
  //  function readJSON(filename){
  //    return readFile(filename, 'utf8').then(JSON.parse);
  //  }


  // or reactive Java:
  //  Observable.from(jsonFile).flatMap(new Func1<File, Observable<String>>() {
  //    @Override public Observable<String> call(File file) {
  //     ...
  //  });


  // or akka stream:
  //  S1.flatMapMerge(3, { case filename =>
  //    FileIO.fromFile(new File(filename))
  //      .map(line => doSomething(line))
  //  })


}
