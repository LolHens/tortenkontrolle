package org.lolhens.piectrl

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorRef, ActorRefFactory, Props}
import akka.io.{IO, Tcp}
import akka.pattern.ask
import akka.util.{ByteString, Timeout}
import org.lolhens.akka.gpio.Gpio.{GetState, SetState}
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
  println("started")

  IO(Gpio)(context.system) ! Gpio.ConnectDigital(GpioHeader.Raspberry, inverted = true)

  override def receive: Receive = {
    case Gpio.Connected(pins) => println("gpio connected")
      val gpioConnection = sender()

      gpioConnection ! SetState((0 until 8).map(_ -> Some(false)).toMap)

      IO(Tcp)(context.system) ! Tcp.Bind(self, new InetSocketAddress("0.0.0.0", 11641))

      context become {
        case failed@Tcp.CommandFailed(_: Tcp.Bind) =>
          println(s"bind failed: $failed")
          context stop self

        case Tcp.Connected(_, _) =>
          val tcpConnection = sender()
          println("tcp connected")

          val client = ClientActor.actor(tcpConnection, gpioConnection)
          tcpConnection ! Tcp.Register(client, useResumeWriting = false)
          gpioConnection ! Gpio.Register(client)
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
}

private class ClientActor(tcpConnection: ActorRef, gpioConnection: ActorRef) extends Actor {
  println("client connected")

  def toBinaryState(states: Map[Int, Boolean]): Int =
    states.foldLeft(0)((last, state) => last | ((if (state._2) 1 else 0) << state._1))

  def fromBinaryState(binState: Int, range: Range = null): Map[Int, Boolean] = {
    @tailrec
    def rec(binState: Int, position: Int, states: Map[Int, Boolean]): Map[Int, Boolean] = {
      val bit = binState & (1 << position)
      val remaining = binState ^ bit

      val newStates = states + (position -> (bit != 0))

      if (remaining != 0) rec(remaining, position + 1, newStates)
      else newStates
    }

    val state = rec(binState, 0, Map.empty)
    if (range != null) range.map(_ -> false).toMap ++ state
    else state
  }

  def receiveState(data: ByteString): Unit = {
    val binState = ByteVector(data.toByteBuffer).take(1).toInt(signed = false)
    println("received " + binState)
    gpioConnection ! Gpio.SetState(fromBinaryState(binState, 0 until 8).map(e => e._1 -> Some(e._2)))
  }

  def sendState(): Unit = {
    implicit val timeout = Timeout(1 second)
    val state = Await.result((gpioConnection ? GetState(0 until 8: _*)).mapTo[Map[Int, Boolean]], 1 second)
    tcpConnection ! Tcp.Write(ByteString(ByteVector(toBinaryState(state)).toByteBuffer))
  }

  sendState()

  override def receive: Receive = {
    case Tcp.Received(data) =>
      receiveState(data)

    case Gpio.StateChanged(pin, state) =>
      sendState()

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
