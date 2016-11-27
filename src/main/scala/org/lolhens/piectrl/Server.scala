package org.lolhens.piectrl

import java.net.{ServerSocket, Socket}
import java.util.concurrent.locks.ReentrantLock

import monix.reactive.Observable

import scala.ref.WeakReference

/**
  * Created by pierr on 04.11.2016.
  */
class Server(val port: Int) {
  private val serverSocket = new ServerSocket(port)
  private val lock = new ReentrantLock()
  @volatile private var clients: List[WeakReference[Socket]] = Nil

  private def addClient(client: Socket) = {
    lock.lock()
    clients = (clients :+ WeakReference(client)).filter(_.get.isDefined)
    lock.unlock()
  }

  private def messageSocket = {
    {
      for (
        socket <- Observable.repeatEval(serverSocket.accept());
        _ = addClient(socket);
        inputStream = socket.getInputStream;
        message <- Observable.fromIterator(new Iterator[Int] {
          var lastByte: Option[Int] = None

          def read() = lastByte.getOrElse {
            val byte = inputStream.read()
            lastByte = Some(byte)
            byte
          }

          override def hasNext: Boolean = read() != -1

          override def next(): Int = {
            val byte = read()
            lastByte = None
            byte
          }
        })
      ) yield message
    }.doOnError(_ => serverSocket.close())
  }

  def broadcast(message: Int): Unit =

  def messages: Observable[Int] = messageSocket.onErrorRestartUnlimited
}
