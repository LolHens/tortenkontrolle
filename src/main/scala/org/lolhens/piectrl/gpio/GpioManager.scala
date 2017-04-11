package org.lolhens.piectrl.gpio

import akka.actor.{Actor, ActorRef, ActorRefFactory, Props}
import com.pi4j.io.gpio.{GpioController, GpioFactory, Pin, RaspiPin}
import com.pi4j.system.SystemInfo
import org.lolhens.piectrl.gpio.Gpio._

import scala.util.control.NonFatal

/**
  * Created by pierr on 07.04.2017.
  */
class GpioManager extends Actor {
  override def receive: Receive = {
    case connect@Connect =>
      val listener = sender()
      try {
        val gpioController: GpioController = GpioFactory.getInstance()
        val pins: Set[Pin] = RaspiPin.allPins(SystemInfo.getBoardType).toSet

        val connection: ActorRef = GpioConnection.actor(gpioController, pins)
        connection ! Register(listener)
        listener tell(Connected, connection)

        context.become {
          case Connect =>
            val listener = sender()
            connection ! Register(listener)
            listener tell(Connected, connection)
        }
      } catch {
        case NonFatal(_) =>
          listener ! CommandFailed(connect)

        case exception: UnsatisfiedLinkError =>
          listener ! CommandFailed(connect)
      }
  }
}

object GpioManager {
  private[gpio] val props = Props[GpioManager]

  private[gpio] def actor(implicit actorRefFactory: ActorRefFactory): ActorRef = actorRefFactory.actorOf(props)
}
