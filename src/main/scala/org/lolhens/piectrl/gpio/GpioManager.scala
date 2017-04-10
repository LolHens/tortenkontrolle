package org.lolhens.piectrl.gpio

import akka.actor.{Actor, ActorRef, ActorRefFactory, Props}
import com.pi4j.io.gpio.{GpioController, GpioFactory, RaspiPin}
import com.pi4j.system.SystemInfo
import org.lolhens.piectrl.gpio.Gpio.{CommandFailed, Connect, Pin}

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
        val pins: Set[Pin] = RaspiPin.allPins(SystemInfo.getBoardType).map(new Pin(_)).toSet


      } catch {
        case NonFatal(exception) =>
          listener ! CommandFailed(connect)
      }
  }
}

object GpioManager {
  private[gpio] val props = Props[GpioManager]

  private[gpio] def actor(implicit actorRefFactory: ActorRefFactory): ActorRef = actorRefFactory.actorOf(props)
}
