package org.lolhens.piectrl

import java.net.{Socket, SocketAddress}
import java.util.concurrent.locks.ReentrantReadWriteLock

import akka.actor.ActorSystem
import monix.execution.Cancelable
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable

import scala.concurrent.Future

/**
  * Created by pierr on 27.11.2016.
  */
class Client(socket: Socket, onClose: Client => Unit)(implicit actorSystem: ActorSystem) {
  val remoteAddress: SocketAddress = socket.getRemoteSocketAddress

  @volatile private var _closed = false

  val output: Observable[Int] = Observable.repeatEval(Future(socket.getInputStream.read()))
    .flatMap(Observable.fromFuture(_))
    .takeWhile(_ != -1)
    .map { e => println(s"receiving 0x${Integer.toHexString(e)} from $remoteAddress"); e }
    .doOnError(e => println(s"error on client $remoteAddress: $e"))
    .onErrorHandleWith { e =>
      println(s"error not handled $remoteAddress")
      Observable()
    }
    .doOnComplete(close)

  val input = new BoundedEventBuffer[Int]()

  val senderActorRef = SenderActor.actor(socket.getOutputStream)

  lazy val send: Cancelable = {


    Observable.fromIterable(input)
      .map { e => println(s"sending 0x${Integer.toHexString(e)} to $remoteAddress"); e }
      .map(msg => senderActorRef ! SenderActor.SendMessage(msg))
      .doOnError(e => println(s"error on client $remoteAddress: $e"))
      .onErrorHandleWith { e =>
        println(s"error not handled $remoteAddress")
        Observable()
      }
      .doOnComplete(close)
      .foreach(_ => ())
  }

  val closeLock = new ReentrantReadWriteLock()

  def closed = {
    closeLock.readLock().lock()
    val result = _closed
    closeLock.readLock().unlock()
    result
  }

  def close = {
    closeLock.writeLock().lock()
    _closed = true
    socket.close()
    onClose(this)
    closeLock.writeLock().unlock()
  }
}
