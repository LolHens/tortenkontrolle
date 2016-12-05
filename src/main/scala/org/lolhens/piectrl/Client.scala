package org.lolhens.piectrl

import java.net.{Socket, SocketAddress}
import java.util.concurrent.locks.{ReentrantLock, ReentrantReadWriteLock}

import monix.execution.Scheduler.Implicits.global
import monix.execution.atomic._
import monix.execution.{Ack, Cancelable}
import monix.reactive.{Notification, Observable, Observer}

import scala.concurrent.{Future, blocking}
import scala.util.Try
import scala.util.control.NonFatal

/**
  * Created by pierr on 27.11.2016.
  */
class Client(socket: Socket, onClose: Client => Unit) {
  private val remoteAddress: SocketAddress = socket.getRemoteSocketAddress

  @volatile private var _closed = false

  val output: Observable[Int] =
    Observable.fromInputStream(socket.getInputStream, 1)
      .map(_.head & 0xFF)
      .takeWhile(_ != -1)
      .materialize
      .map {
        case Notification.OnError(NonFatal(e)) =>
          println(s"error on client $this: $e")
          Notification.OnComplete
        case notification@Notification.OnNext(e) =>
          println(s"receiving 0x${Integer.toHexString(e)} from $this")
          notification
        case notification =>
          notification
      }
      .dematerialize
      .doOnComplete(close())

  val input = new BoundedQueue[Int](10)

  val lock = new ReentrantLock()

  lazy val send: Cancelable = {
    println("a")
    val r = input.observable
      .unsafeSubscribeFn(new Observer[Int] {
        override def onError(ex: Throwable): Unit = {
          println(s"error on client $this: $ex")
          close()
        }

        override def onComplete(): Unit = {
          close()
        }

        override def onNext(elem: Int): Future[Ack] = {
          println("!!!")
          lock.lock()
          println(s"sending 0x${Integer.toHexString(elem)} to ${Client.this}")
          val result = Try {
            blocking {
              socket.getOutputStream.write(elem)
              socket.getOutputStream.flush()
            }
          }
          lock.unlock()
          println(result)
          result.failed.toOption.foreach { failure =>
            println(s"error on client $this: $failure")
            close()
          }
          Future.successful(Ack.Continue)
        }
      })
    /*.map { e => println(s"sending 0x${Integer.toHexString(e)} to $this"); e }
    .foreach {msg =>
      lock.lock()
      val result = Try {
        socket.getOutputStream.write(msg)
        socket.getOutputStream.flush()
      }
      lock.unlock()
      println(result)
      result.failed.toOption.foreach { failure =>
        println(s"error on client $this: $failure")
        close()
      }
    }*/
    println("b")
    r
    /*.flatMap { msg =>
      Observable.fromFuture(Future {
        lock.lock()
        val result = Try {
          socket.getOutputStream.write(msg)
          socket.getOutputStream.flush()
        }
        lock.unlock()
        result
      }.dematerialize)
    }
    .materialize
    .map {
      case Notification.OnError(NonFatal(e)) =>
        println(s"error on client $this: $e")
        Notification.OnComplete
      case notification =>
        notification
    }
    .dematerialize
    .doOnComplete(close)
    .foreach(_ => ())*/
  }

  val closeLock = new ReentrantReadWriteLock()

  def closed: Boolean = {
    closeLock.readLock().lock()
    val result = _closed
    closeLock.readLock().unlock()
    result
  }

  def close(): Unit = {
    closeLock.writeLock().lock()
    println("close")
    try {
      _closed = true
      send.cancel()
      socket.close()
      onClose(this)
    } finally
      closeLock.writeLock().unlock()
  }

  val id: Int = Client.nextId.getAndIncrement()

  override def toString: String = s"$remoteAddress $id"
}

object Client {
  val nextId = Atomic(0)
}