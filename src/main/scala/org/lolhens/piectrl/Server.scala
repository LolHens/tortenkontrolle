package org.lolhens.piectrl

import java.net.{ServerSocket, Socket}
import java.util.concurrent.locks.ReentrantReadWriteLock

import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable

import scala.concurrent.Future
import scala.util.control.NonFatal

/**
  * Created by pierr on 04.11.2016.
  */
class Server(val port: Int) {
  private val serverSocket = new ServerSocket(port)

  private val clientsLock = new ReentrantReadWriteLock()
  @volatile private var _clients = List[Client]()

  def clients = {
    clientsLock.readLock().lock()
    val result = _clients
    clientsLock.readLock().unlock()
    result
  }

  class Client(socket: Socket) {
    val remoteAddress = socket.getRemoteSocketAddress

    clientsLock.writeLock().lock()
    _clients = _clients :+ this
    clientsLock.writeLock().unlock()

    println(s"added client $remoteAddress")

    val lock = new ReentrantReadWriteLock()

    @volatile private var _closed = false

    val output = Observable.repeatEval(Future(socket.getInputStream.read()))
      .flatMap(Observable.fromFuture(_))
      .takeWhile(_ != -1)
      .map { e => println(s"receiving $e from $remoteAddress"); e }
      .endWithError(new Exception("ended")).onErrorHandleWith { e =>
      println(e.toString)
      close
      Observable()
    }

    val input = new BoundedEventBuffer[Int]()

    Observable.fromIterable(input)
      .map { e => println(s"sending $e to $remoteAddress"); e }
      .foreach(socket.getOutputStream.write(_)).onFailure {
      case NonFatal(e) =>
        println(e.toString)
        close
    }

    def closed = {
      lock.readLock().lock()
      val result = _closed
      lock.readLock().unlock()
      result
    }

    def close = {
      lock.writeLock().lock()
      _closed = true
      socket.close()
      lock.writeLock().unlock()
      clientsLock.writeLock().lock()
      _clients = _clients.filterNot(_ == this)
      println(s"remove client $remoteAddress")
      clientsLock.writeLock().unlock()
    }
  }

  val clientBuffer = new BoundedEventBuffer[Client](1)

  val output =
    Observable.repeatEval(Future(serverSocket.accept()))
      .flatMap(Observable.fromFuture(_))
      .map(socket => new Client(socket))
      .map { client => clientBuffer += client; client }
      .mergeMap(e => e.output)

  def broadcast(message: Int): Unit = clients.foreach(_.input += message)
}
