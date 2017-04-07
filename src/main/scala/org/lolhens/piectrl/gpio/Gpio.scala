package org.lolhens.piectrl.gpio

import akka.actor.{ActorRef, ExtendedActorSystem, ExtensionId, ExtensionIdProvider}
import akka.io.IO

/**
  * Created by pierr on 07.04.2017.
  */
object Gpio extends ExtensionId[GpioExt] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): GpioExt = new GpioExt(system)

  override def lookup = Gpio

  trait Command

  trait Event

  class Pin private[gpio](pin: com.pi4j.io.gpio.Pin) {
    def name: String = pin.getName

    def address: Int = pin.getAddress
  }

  case object Connect extends Command

  case class CommandFailed(command: Command) extends Event

  case class Connected(pins: Set[Pin]) extends Event

}

class GpioExt(system: ExtendedActorSystem) extends IO.Extension {
  override def manager: ActorRef = GpioManager.actor(system)
}
