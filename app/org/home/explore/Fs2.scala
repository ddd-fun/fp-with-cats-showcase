package org.home.explore

import java.util.concurrent.Executors


import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import scala.language.higherKinds

trait Fs2 {

  import fs2._
  import cats.effect.{IO, Sync}
  import fs2.{io, text, async}
  import fs2.async.mutable.Signal
  import fs2.internal.ThreadFactories
  import cats.effect.implicits


  implicit val ec2:ExecutionContext = ExecutionContext
    .fromExecutorService(Executors.newCachedThreadPool(ThreadFactories.named("ec2", daemon = true)))

  val altEc:ExecutionContext = ExecutionContext
    .fromExecutorService(Executors.newCachedThreadPool(ThreadFactories.named("altEc", daemon = true)))

  val stp = Executors.newScheduledThreadPool(10, ThreadFactories.named("stp", daemon = true))

  val scheduler = fs2.Scheduler.fromScheduledExecutorService(stp)

  // this Scheduler will be shout down at the end of the stream
  val safeScheduler = fs2.Scheduler[IO](20, threadPrefix = "safeScheduler")

  def log[A](pfx:String) : Pipe[IO, A, A] = _.evalMap{ a =>
    IO{ println(s" ${Thread.currentThread().getName}:$pfx>$a"); a }
  }

  def randomDelays[A](dur: FiniteDuration) : Pipe[IO, A, A] = _.flatMap{ a =>
    val  delay = scala.util.Random.nextInt(dur.toMillis.toInt)
    scheduler.delay(Stream.eval(IO{a}), delay.millis)
  }

  def safeRandomDelay[A](dur:FiniteDuration) : Pipe[IO, A, A] = _.flatMap{ a =>
    for{
      sch <- safeScheduler
           delay = scala.util.Random.nextInt(dur.toMillis.toInt)
      a   <- sch.delay(Stream.eval(IO{a}), delay.millis)
    } yield a
  }


  val a = Stream.range(0, 5).covary[IO]
    .through(safeRandomDelay(1000.millis))
    .through(log("A"))

  a.compile.drain//.unsafeRunSync()

  val b = Stream.range(0,5).covary[IO]
    .through(randomDelays(200.millis))
    .through(log("B"))

  val c = Stream.range(2,8).covary[IO]
    .through(randomDelays(500.millis))
    .through(log("C"))

  //wait from A then wait from B then output A then B, thus only emitting when both elements are available
  (a interleave b).through(log("ALL")).compile.drain//.unsafeRunSync()

  //it will pull next available either from a or b
  ((a merge b) merge c).through(log("MRG")).compile.drain//.unsafeRunSync()

  val ss:Stream[IO, Stream[IO, Int]] = Stream(a,b,c)

  // join accept parallel factor, aka how many of inner streams will be opened/pulled-from at the same time;
  // given 2, it opens 2 streams (a and b) in parallel and pulls values from them until they are finalized,
  // only then it opens next 2 streams;
  ss.join(2).through(log("JOIN")).compile.drain//.unsafeRunSync()

  // first will drain values from ids: 1,2 then ids:3,4
  val sss: Stream[IO, Stream[IO, Int]] =
    Stream.range(1, 5).map(id => Stream.range(0, 3).covary[IO].through[Int](log(s"id$id")))

  sss.join(2).compile.drain//.unsafeRunSync()



  // ----  communicating with streams, or why Akka has Mat :-)...

  //we didn't get signal immediately, we have to eval IO in order to get it
  val signal : IO[Signal[IO, Int]] = fs2.async.signalOf[IO, Int](0)

  // explore threads underneath: Eval IO[Signal[IO[Int]]] and IO[Int] are on current thread (main), so why EC is needed for creation?
  signal.flatMap(s => { println(s"eval of Signal.get is on: ${Thread.currentThread().getName}"); s.get} )
    .flatMap(i => IO{println(s"eval of IO[$i] is on: ${Thread.currentThread().getName}")})
  //.unsafeToFuture()

  // implements writer - reader pattern
  Stream.eval(signal).flatMap{ s =>

    val writer = Stream.range(0,10).covary[IO]
      .evalMap(v => s.set(v))
      .through(randomDelays(200.millis))
      .through(log(s"set")).drain

    val reader = s.discrete.through(log("get")).drain

    reader mergeHaltBoth writer
  }.compile.drain//.unsafeRunSync()


  // interrupt example
  // stream read infinitely from stdin and in case of parsing exception it reports error then stops
  Stream.eval(signal).flatMap{sig =>

    val reader = Stream.eval(IO{scala.io.StdIn.readLine().toInt}).attempt.evalMap{ // attempt works only if exception was thrown within IO
      case Left(th) => IO{println(s"ERRROR: $th")}.flatMap(_ => sig.set(-1))
      case Right(i) => IO{println(s"OK: $i")}.flatMap(_ => sig.set(i))
    }.repeat

    // this stream produce true if sig == -1 otherwise infinite false
    val listener = sig.discrete.map(_== -1)

    //interrupt when listener turns to true
    reader.interruptWhen(sig.map(_ == -1))  // == reader.interruptWhen(listener)

  }.compile.drain//.unsafeRunSync()



  // "counter observer" observes stream sync, calculate rate msg/sec and write to out
  Stream("msg").covary[IO]
  .through(randomDelays(5.millis)).repeat.take(5000)
  .observe{ in =>
     Stream.eval(fs2.async.refOf[IO, Int](0)).flatMap{  ref =>
       //potentially awakeDelay might be more precise; and dump operation sync
       val rater = scheduler.awakeEvery[IO](1.second).flatMap( _ =>
         Stream.eval(ref.modify(_ => 0).flatMap(change => IO{println(s"${change.previous} msg/sec")} ) )
       ).drain
       // since observe is sync, modify might add performance penalty
       val counter = in.evalMap(_ => ref.modify(_+1)).drain

       counter.mergeHaltL(rater)
     }
  }.compile.drain.unsafeRunSync()


  // read stdin -> enqueue -> dequeue -> print, stops when read 'exit'
  val queue = async.boundedQueue[IO, String](10)

  Stream.eval(queue).flatMap{ q =>

    val enQueue =  Stream.eval(IO{scala.io.StdIn.readLine()}).to(q.enqueue).repeat.drain

    val deQueue = Stream.eval(q.dequeue1.flatMap(s => IO{println(s"from queue: $s"); s})).repeat.takeWhile(_ != "exit").drain

    enQueue mergeHaltR deQueue

  }.compile.drain//.unsafeRunSync()


  //example of sink which writes concurrently
  def concurrentIOSink[Item](writeCapacity:Int, write:Item => IO[Unit]) : Sink[IO, Item] =
    _.segmentN(writeCapacity).flatMap[Stream[IO, Unit]]{seg =>
        val writersIO : List[IO[Unit]]  = seg.map(write).force.toList
        val writersList : List[Stream[IO, Unit]] =  writersIO.map(Stream.eval)
        Stream.fromIterator[IO, Stream[IO, Unit]](writersList.iterator)
    }.join(writeCapacity)



  stp.shutdown()


}

object runner extends App with Fs2
