package org.lolhens.piectrl

import akka.actor.{Actor, ActorRef, ActorRefFactory, Props}
import com.pi4j.io.gpio.{GpioController, GpioFactory}
import org.lolhens.piectrl.GpioActor.SetState

/**
  * Created by pierr on 29.03.2017.
  */
class GpioActor extends Actor {
  val gpioController: GpioController = GpioFactory.getInstance()

  override def receive: Receive = {
    case SetState(state) =>
      gpioController.provisionDigitalOutputPin()
  }
}

object GpioActor {
  val props: Props = Props[GpioActor]

  def actor(implicit actorRefFactory: ActorRefFactory): ActorRef = actorRefFactory.actorOf(props)

  case class Pin(number: Int)

  trait Command

  trait Event

  case class SetState(pins: Map[Pin, Option[Boolean]]) extends Command

  case class Register(pins: Seq[Pin]) extends Command

  case class Unregister(pins: Seq[Pin]) extends Command

  case class StateChanged(pins: Map[Pin, Boolean]) extends Event
}
