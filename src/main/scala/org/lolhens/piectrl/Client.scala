package org.lolhens.piectrl

import java.net.{Socket, SocketAddress}
import java.util.concurrent.locks.{ReentrantLock, ReentrantReadWriteLock}

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

  val output: Observable[Int] =
    Observable.fromInputStream(socket.getInputStream, 1)
      .map(_.head & 0xFF)
      .takeWhile(_ != -1)
      .map { e => println(s"receiving 0x${Integer.toHexString(e)} from $remoteAddress"); e }
      .doOnError(e => println(s"error on client $remoteAddress: $e"))
      .onErrorHandleWith { e =>
        println(s"error not handled $remoteAddress")
        Observable()
      }
      .doOnComplete(close)

  val input = new BoundedEventBuffer[Int]()

  val lock = new ReentrantLock()

  lazy val send: Cancelable = {
    Observable.fromIterable(input)
      .map { e => println(s"sending 0x${Integer.toHexString(e)} to $remoteAddress"); e }
      .flatMap { msg =>
        Observable.fromFuture(Future {
          lock.lock()
          try {
            socket.getOutputStream.write(msg)
            socket.getOutputStream.flush()
          } finally
            lock.unlock()
        })
      }
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
    try {
      _closed = true
      socket.close()
      onClose(this)
    } finally
      closeLock.writeLock().unlock()
  }
}
