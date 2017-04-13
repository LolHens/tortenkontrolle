package org.lolhens.piectrl

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorRef, ActorRefFactory, Props}
import akka.io.{IO, Tcp}
import akka.pattern.ask
import akka.util.{ByteString, Timeout}
import org.lolhens.akka.gpio.Gpio.GetState
import org.lolhens.akka.gpio.{Gpio, GpioHeader}
import scodec.bits.ByteVector

import scala.annotation.tailrec
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * Created by pierr on 11.03.2017.
  */
private class ServerActor extends Actor {

  IO(Gpio)(context.system) ! Gpio.ConnectDigital(GpioHeader.Raspberry)

  override def receive: Receive = {
    case Gpio.Connected(pins) =>
      val gpioConnection = sender()
      gpioConnection ! Gpio.Register(self)

      IO(Tcp)(context.system) ! Tcp.Bind(self, new InetSocketAddress("0.0.0.0", 11641))

      context become {
        case failed@Tcp.CommandFailed(_: Tcp.Bind) =>
          println(s"bind failed: $failed")
          context stop self

        case Tcp.Connected(_, _) =>
          val tcpConnection = sender()

          val client = ClientActor.actor(tcpConnection, gpioConnection)
          tcpConnection ! Tcp.Register(client, useResumeWriting = false)
          gpioConnection ! Gpio.Register(client)

          implicit val timeout = Timeout(1 second)
          val state = Await.result((gpioConnection ? GetState(0 until 8: _*)).mapTo[Map[Int, Boolean]], 1 second)
          val byte = ServerActor.statesToByte(state.filter(_._2).keys.toSeq)
          tcpConnection ! Tcp.Write(ByteString(ByteVector(byte).toByteBuffer))
      }

    case Gpio.CommandFailed(_: Gpio.ConnectDigital, reason) =>
      println(s"gpio connect failed: $reason")
      context stop self
  }
}

object ServerActor {
  val props: Props = Props[ServerActor]

  def actor(implicit actorRefFactory: ActorRefFactory): ActorRef =
    actorRefFactory.actorOf(props)

  def statesToByte(highPins: Seq[Int]): Int = highPins.foldLeft(0)((last, pin) => last | (1 << pin))

  def statesFromByte(byte: Int): List[Int] = {
    @tailrec
    def getPins(byte: Int, position: Int, pins: List[Int]): List[Int] = {
      val bit = byte & (1 << position)
      val nextBits = bit ^ (1 << position)
      if (nextBits != 0)
        getPins(nextBits, position + 1, if (bit != 0) List(position) else Nil)
      else
        pins
    }

    getPins(byte, 0, Nil)
  }
}

private class ClientActor(tcpConnection: ActorRef, gpioConnection: ActorRef) extends Actor {
  override def receive: Receive = {
    case Tcp.Received(data) =>
      val byte = ByteVector(data.toByteBuffer)(0)
      val states = (0 until 8).map(_ -> false).toMap ++ ServerActor.statesFromByte(byte).map(_ -> true)
      gpioConnection ! Gpio.SetState(states.map(e => e._1 -> Some(e._2)))

    case Gpio.StateChanged(pin, state) =>
      implicit val timeout = Timeout(1 second)
      val state = Await.result((gpioConnection ? GetState(0 until 8: _*)).mapTo[Map[Int, Boolean]], 1 second)
      val byte = ServerActor.statesToByte(state.filter(_._2).keys.toSeq)
      tcpConnection ! Tcp.Write(ByteString(ByteVector(byte).toByteBuffer))

    case failed@Tcp.CommandFailed(_: Tcp.Write) =>
      println(s"write failed: $failed")

    case closed: Tcp.ConnectionClosed =>
      println(s"connection closed $closed")
      context.stop(self)
  }
}

object ClientActor {
  def props(tcpConnection: ActorRef, gpioConnection: ActorRef): Props =
    Props(classOf[ClientActor], tcpConnection, gpioConnection)

  def actor(tcpConnection: ActorRef, gpioConnection: ActorRef)
           (implicit actorRefFactory: ActorRefFactory): ActorRef =
    actorRefFactory.actorOf(props(tcpConnection, gpioConnection))
}
