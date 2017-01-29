package org.lolhens.piectrl

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.io.Tcp._
import akka.io.{IO, Tcp}
import akka.pattern.ask
import akka.util.{ByteString, Timeout}
import scodec.bits.ByteVector
import swave.core.{Drain, Pipe, PushSpout, Spout}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * Created by u016595 on 13.12.2016.
  */
object TcpStream {
  var logging = false

  def receiver(bind: InetSocketAddress)(implicit actorSystem: ActorSystem): Spout[ByteVector] = {
    val pushSpout = PushSpout[ByteVector](2, 4)
    TcpReceiver.actor(bind, pushSpout)
    pushSpout.asyncBoundary("blocking-io", bufferSize = 1)
  }

  private class TcpServer(bind: InetSocketAddress, pushSpout: PushSpout[ByteVector]) extends Actor {

    import context.system

    IO(Tcp) ! Tcp.Bind(self, bind)

    override def receive: Receive = {
      case CommandFailed(_: Bind) =>
        context.stop(self)

      case Connected(remoteAddress, localAddress) =>
        val connection = sender()
        val handler = TcpReceiver.TcpHandler.actor(connection, pushSpout)
        connection ! Register(handler, useResumeWriting = false)
    }
  }

  private object TcpReceiver {
    def props(bind: InetSocketAddress, pushSpout: PushSpout[ByteVector]) = Props(new TcpServer(bind, pushSpout))

    def actor(bind: InetSocketAddress, pushSpout: PushSpout[ByteVector])(implicit actorSystem: ActorSystem): ActorRef = actorSystem.actorOf(props(bind, pushSpout))

    class TcpHandler(connection: ActorRef, pushSpout: PushSpout[ByteVector]) extends Actor {
      override def receive: Receive = {
        case Received(byteString) =>
          val data = ByteVector(byteString.toByteBuffer)
          if (logging) println(s"receiving $data")
          pushSpout.offer(data) // TODO
          connection ! Write(ByteString(ByteVector(1).toByteBuffer))

        case failed@CommandFailed(_: Write) =>
          if (logging) println(s"write failed $failed")

        case closed: ConnectionClosed =>
          if (logging) println(s"connection closed $closed")
          context.stop(self)
      }
    }

    object TcpHandler {
      def props(connection: ActorRef, pushSpout: PushSpout[ByteVector]) = Props(new TcpHandler(connection, pushSpout))

      def actor(connection: ActorRef, pushSpout: PushSpout[ByteVector])(implicit actorSystem: ActorSystem) = actorSystem.actorOf(props(connection, pushSpout))
    }

  }

  def sender(bind: InetSocketAddress, remote: InetSocketAddress)(implicit actorSystem: ActorSystem): Drain[ByteVector, Future[Unit]] = {
    val sender = TcpSender.actor(bind, remote)
    implicit val timeout = Timeout(5 seconds)
    Pipe[ByteVector].asyncBoundary(bufferSize = 0).flatMap { byteVector =>
      sender ? TcpSender.SendData(byteVector)
    }.to(Drain.ignore)
  }

  private class TcpSender(bind: InetSocketAddress, remote: InetSocketAddress) extends Actor {

    import context.system

    IO(Tcp) ! Connect(remote)

    override def receive: Receive = {
      case CommandFailed(_: Connect) =>
        if (logging) println("connect failed")
        context.stop(self)

      case Connected(remoteAddress, localAddress) =>
        val connection = sender()
        connection ! Register(self, useResumeWriting = false)
        context.become(ready(connection))
    }

    def ready(connection: ActorRef): Receive = {
      case TcpSender.SendData(data) =>
        if (logging) println(s"sending $data")
        connection ! Write(ByteString(data.toByteBuffer), TcpSender.Ack)
        val asker = sender()
        context.become {
          case TcpSender.Ack =>
            context.become(ready(connection))
            asker ! TcpSender.Ack

          case f@CommandFailed(_: Write) =>
            if (logging) println(s"!!!! write failed $f")
        }

      case f@CommandFailed(_: Write) =>
        if (logging) println(s"write failed $f")

      case Received(data) =>

      case closed: ConnectionClosed =>
        if (logging) println(s"connection closed $closed")
        context.stop(self)
    }
  }

  private object TcpSender {
    def props(bind: InetSocketAddress, remote: InetSocketAddress) = Props(new TcpSender(bind, remote))

    def actor(bind: InetSocketAddress, remote: InetSocketAddress)(implicit actorSystem: ActorSystem): ActorRef = actorSystem.actorOf(props(bind, remote))

    case class SendData(byteVector: ByteVector)

    case object Ack extends Event

  }

}