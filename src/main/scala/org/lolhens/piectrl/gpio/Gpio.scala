package org.lolhens.piectrl.gpio

import akka.actor.{ActorRef, ExtendedActorSystem, ExtensionId, ExtensionIdProvider}
import akka.io.IO
import com.pi4j.io.gpio.Pin

/**
  * Created by pierr on 07.04.2017.
  */
object Gpio extends ExtensionId[GpioExt] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): GpioExt = new GpioExt(system)

  override def lookup = Gpio

  trait Command

  trait Event

  case class Connect(gpioHeader: GpioHeader) extends Command

  private[gpio] case class Register(ref: ActorRef) extends Command

  case class CommandFailed(command: Command, reason: Throwable) extends Event

  case class Connected(pins: Map[Int, Pin]) extends Event

  object Connected {
    def apply(pins: Set[Pin]): Connected = Connected(pins.map(pin => pin.getAddress -> pin).toMap)
  }

  case class SetState(pins: Map[Pin, Option[Boolean]]) extends Command

  object SetState {
    def apply(pin: Pin, state: Option[Boolean]): SetState = SetState(Map(pin -> state))
  }

  case class StateChanged(pin: Pin, state: Boolean) extends Event

}

class GpioExt(system: ExtendedActorSystem) extends IO.Extension {
  override def manager: ActorRef = GpioManager.actor(system)
}
