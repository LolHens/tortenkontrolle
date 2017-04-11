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

  case object Connect extends Command

  private[gpio] case class Register(ref: ActorRef) extends Command

  case class CommandFailed(command: Command) extends Event

  case object Connected extends Event

  case class SetState(pins: Map[Pin, Option[Boolean]]) extends Command

  case class StateChanged(pins: Map[Pin, Boolean]) extends Event

}

class GpioExt(system: ExtendedActorSystem) extends IO.Extension {
  override def manager: ActorRef = GpioManager.actor(system)
}
