package org.home.explore

import java.util.concurrent.Executors

import cats.effect.IO
import fs2.async.mutable.{Semaphore, Signal}
import fs2.internal.ThreadFactories
import fs2.{Pipe, Sink, Stream, async}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

trait Fs2Partitioning {

  implicit val ec2:ExecutionContext = ExecutionContext
    .fromExecutorService(Executors.newCachedThreadPool(ThreadFactories.named("ec2", daemon = true)))

  val stp = Executors.newScheduledThreadPool(10, ThreadFactories.named("stp", daemon = true))

  val scheduler = fs2.Scheduler.fromScheduledExecutorService(stp)


  trait Data
  case class Private(content:String) extends Data
  case class Public(content: String) extends Data


  // fully sequential stream: Data will "travel" through first observer and then thought second one. for ex: ... data => observer1(data) => data => observer2(data) => data => ...
  fs2.Stream.emits(Seq(Private("private one"), Public("public one"), Private("private two"))).covary[IO]
    .observe((ds:Stream[IO, Data]) => ds.collect{case Private(cnt) => cnt}.evalMap(cnt => IO{println(s"dump to private sink: $cnt")})   )
    .observe((ds:Stream[IO, Data]) => ds.collect{case Public(cnt) => cnt}.evalMap(cnt => IO{println(s"dump to public sink: $cnt")})   )
    .compile.drain//.unsafeRunSync()


  val observingPipe: Pipe[IO, Data, Data] = _
    .observe((ds:Stream[IO, Data]) => ds.collect{case Private(cnt) => cnt}.evalMap(cnt => IO{println(s"thread:${Thread.currentThread.getName}:> dump to private sink: $cnt")})   )
    .observe((ds:Stream[IO, Data]) => ds.collect{case Public(cnt) => cnt}.evalMap(cnt => IO{println(s"thread:${Thread.currentThread.getName}:> dump to public sink: $cnt")})   )


  // parallel by overall load (not by type of Data)..
  // at most 2 parallel threads processing by 10 items in each
  fs2.Stream.emits(Seq(Private("private one"), Public("public one"), Private("private two")))
    .repeat.take(200).covary[IO] // just for purpose of example it will emits 200 of data items
    .segmentN(10) // grouping by 10 data items (to some extend you may think as collecting 10 data items together)
    .map(segmentOf10dataItems => Stream.segment(segmentOf10dataItems).covary[IO].through(observingPipe)  ) // this peace of code will be executed concurrently
    .join(2) // concurrency factor...
    .compile.drain//.unsafeRunSync()




  // Example of partitioning and parallel processing with async boundaries

  val privateDataQueue = async.boundedQueue[IO, Private](10)
  val publicDataQueue = async.boundedQueue[IO, Public](200)

  type DispatchingQueues = Tuple3[async.mutable.Queue[IO, Private], async.mutable.Queue[IO, Public], Semaphore[IO]]

  // zip two queues
  val pairOfQueue:IO[DispatchingQueues] = for{
    prv <- privateDataQueue
    pub <- publicDataQueue
    sig <- async.semaphore[IO](0)
  } yield (prv, pub, sig)



  val asyncDataPartitioner: Sink[IO, Data] =  (dataStream: Stream[IO, Data]) => {

    // 1. eval effects of creating DispatchingQueues
    Stream.eval[IO, DispatchingQueues](pairOfQueue).flatMap{ case (privateQueue, publicQueue, sig) =>

      val dataProducer = dataStream.flatMap{
        case pr:Private =>  Stream.eval(privateQueue.enqueue1(pr))
        case pb:Public =>   Stream.eval(publicQueue.enqueue1(pb))
      }

      // 2. dequeue from each queue and dump data element to Sink
      val privateDataConsumer = privateQueue.timedDequeue1(2.seconds, scheduler).flatMap{
        case Some(data) => IO{ println(s"thread:${Thread.currentThread.getName}:> dump to private sink: $data")}
        case None => IO{println(s"thread:${Thread.currentThread.getName}:> dump to private sink: timed out after 2 seconds")}
          .flatMap(_ => sig.increment) // increment if timed out
      }

      val publicDataConsumer = publicQueue.timedDequeue1(2.seconds, scheduler).flatMap{
        case Some(data) => IO{println(s"thread:${Thread.currentThread.getName}:> dump to public sink: $data")}
        case None => IO{println(s"thread:${Thread.currentThread.getName}:> dump to public sink: timed out after 2 seconds")}
          .flatMap(_ => sig.increment)
      }
      // 3. trigger "consumer's programs" concurrently.
      val dataConsumer = Stream.eval(privateDataConsumer).repeat.merge(Stream.eval(publicDataConsumer).repeat)

      // 4. trigger "produces and consumer's program " concurrently
      dataProducer.mergeHaltR( dataConsumer.interruptWhen(Stream.eval(sig.available.map{_ > 5} ).repeat)  )
    }

  }



  fs2.Stream.emits(Seq(Private("private one"), Public("public one")))
    .repeat.take(200).covary[IO]
    .to(asyncDataPartitioner)
    .compile.drain.unsafeRunSync()


}


object runnner extends App with Fs2Partitioning