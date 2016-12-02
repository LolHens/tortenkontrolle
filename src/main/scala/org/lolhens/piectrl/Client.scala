package org.lolhens.piectrl

import java.net.{Socket, SocketAddress}
import java.util.concurrent.locks.{ReentrantLock, ReentrantReadWriteLock}

import akka.actor.ActorSystem
import monix.execution.Cancelable
import monix.execution.FutureUtils.extensions._
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable
import monix.execution.atomic._
import scala.concurrent.Future
import scala.util.Try

/**
  * Created by pierr on 27.11.2016.
  */
class Client(socket: Socket, onClose: Client => Unit)(implicit actorSystem: ActorSystem) {
  private val remoteAddress: SocketAddress = socket.getRemoteSocketAddress

  @volatile private var _closed = false

  val output: Observable[Int] =
    Observable.fromInputStream(socket.getInputStream, 1)
      .map(_.head & 0xFF)
      .takeWhile(_ != -1)
      .map { e => println(s"receiving 0x${Integer.toHexString(e)} from $this"); e }
      .doOnError(e => println(s"error on client $this: $e"))
      .onErrorHandleWith { e =>
        println(s"error not handled $this")
        Observable()
      }
      .doOnComplete(close)

  val input = Atomic(None: Option[Int])

  val lock = new ReentrantLock()

  lazy val send: Cancelable = {
    Observable.repeatEval(
      input.getAndSet(None)
    )
      .flatMap(_.map(Observable(_)).getOrElse(Observable()))
      .map { e => println(s"sending 0x${Integer.toHexString(e)} to $this"); e }
      .flatMap { msg =>
        Observable.fromFuture(Future {
          println("lock")
          lock.lock()
          val r = Try {
            socket.getOutputStream.write(msg)
            socket.getOutputStream.flush()
          }
          lock.unlock()
          println("unlock")
          r
        }.dematerialize)
      }
      .doOnError(e => println(s"error on client $this: $e"))
      .onErrorHandleWith { e =>
        println(s"error not handled $this")
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
      send.cancel()
      socket.close()
      onClose(this)
    } finally
      closeLock.writeLock().unlock()
  }

  val id = Client.nextId.getAndIncrement()

  override def toString: String = s"$remoteAddress $id"
}

object Client {
  val nextId = Atomic(0)
}