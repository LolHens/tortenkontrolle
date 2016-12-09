package org.lolhens.piectrl

import java.net.ServerSocket
import java.util.concurrent.locks.ReentrantReadWriteLock

import monix.execution.Scheduler
import monix.reactive.Observable
import swave.core.{Drain, Spout}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, blocking}
import scala.util.Try

/**
  * Created by pierr on 04.11.2016.
  */
class Server(val port: Int, onAccept: Client => Unit = _ => ()) {
  private val serverSocket = new ServerSocket(port)
  private implicit val scheduler = Scheduler.io()

  class ClientManager {
    private val lock = new ReentrantReadWriteLock()

    @volatile private var _clients: List[Client] = Nil

    def clients: List[Client] = {
      lock.readLock().lock()
      val result = _clients
      lock.readLock().unlock()
      result
    }

    def +=(client: Client): Unit = {
      lock.writeLock().lock()
      _clients = _clients :+ client
      lock.writeLock().unlock()
      println(s"added client $client")
    }

    def -=(client: Client): Unit = {
      lock.writeLock().lock()
      _clients = _clients.filterNot(_ == client)
      lock.writeLock().unlock()
      println(s"removed client $client")
    }
  }

  val clientManager = new ClientManager()

  private val outputBuffer = new BoundedQueue[Int](20)

  val acceptor = Spout.continually(Try(blocking(serverSocket.accept()))).async("blocking-io")
    .flattenConcat()
    .drainTo(Drain.toPublisher()).get

  Observable.fromReactivePublisher(acceptor)
    .map { socket =>
      val client = new Client(socket, clientManager -= _)
      clientManager += client
      Future(client.send)
      onAccept(client)
      client
    }
    .mergeMap(_.output.flatMap(message => {
      Observable.fromFuture(outputBuffer += message)
    })).subscribe()

  val output: Observable[Int] =
    outputBuffer.observable
      .map { e => println(s"server is receiving 0x${Integer.toHexString(e)}"); e }

  def broadcast(message: Int): Unit = {
    println(s"broadcasting to clients: ${clientManager.clients.mkString}")
    clientManager.clients.foreach(client => Await.result(client.input += message, Duration.Inf))
  }
}
