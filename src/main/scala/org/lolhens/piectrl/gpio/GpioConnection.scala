package org.lolhens.piectrl.gpio

import akka.actor.{Actor, ActorRef, ActorRefFactory, Props, Terminated}
import akka.routing.{BroadcastRoutingLogic, Router}
import com.pi4j.io.gpio._
import com.pi4j.io.gpio.event.{GpioPinDigitalStateChangeEvent, GpioPinListenerDigital}
import org.lolhens.piectrl.gpio.Gpio.{Register, SetState, StateChanged}

/**
  * Created by pierr on 07.04.2017.
  */
class GpioConnection(gpioController: GpioController,
                     pins: Set[Pin]) extends Actor {
  var eventRouter = Router(BroadcastRoutingLogic())

  case class ProvisionedPin(gpioPin: GpioPinDigitalOutput, state: Option[Boolean]) {
    def setState(state: Option[Boolean]): ProvisionedPin =
      if (state != this.state) {
        state match {
          case Some(state) =>
            if (this.state.isEmpty)
              gpioPin.setMode(PinMode.ANALOG_OUTPUT)
            gpioPin.setState(state)

          case None =>
            gpioPin.setMode(PinMode.ANALOG_INPUT)
        }

        copy(state = state)
      } else this
  }

  object ProvisionedPin {
    def apply(pin: Pin, state: Option[Boolean]): ProvisionedPin = {
      val gpioPin = gpioController.provisionDigitalMultipurposePin(pin,
        state.map(_ => PinMode.ANALOG_OUTPUT).getOrElse(PinMode.ANALOG_INPUT))
      state.foreach(gpioPin.setState)
      gpioPin.addListener(new GpioPinListenerDigital {
        override def handleGpioPinDigitalStateChangeEvent(event: GpioPinDigitalStateChangeEvent): Unit =
          self ! StateChanged(Map(pin -> event.getState.isHigh))
      })
      ProvisionedPin(gpioPin, state)
    }
  }

  var provisionedPins: Map[Pin, ProvisionedPin] = Map.empty

  override def receive: Receive = {
    case Register(ref) =>
      context watch ref
      eventRouter = eventRouter.addRoutee(ref)

    case Terminated(ref) =>
      eventRouter = eventRouter.removeRoutee(ref)

    case stateChanged: StateChanged =>
      eventRouter.route(stateChanged, self)

    case SetState(states) =>
      val newStates = states
        .filter(e => pins.contains(e._1))
        .groupBy(_._2)
        .map(e => (e._1, e._2.keys.toSet))
        .flatMap {
          case (state, pins) =>
            pins.foreach(pin => provisionedPins.getOrElse(pin, {
              provisionedPins + (pin -> ProvisionedPin(pin, state))
            }))

            //sender() ! StateChanged(pins.map(_ -> state).toMap)
            pins.map(pin => pin -> state)
        }

      eventRouter.route(StateChanged(newStates.flatMap(e => e._2.map(e._1 -> _))), self)
  }
}

object GpioConnection {
  private[gpio] def props(gpioController: GpioController,
                          pins: Set[Pin]) = Props(new GpioConnection(gpioController, pins))

  private[gpio] def actor(gpioController: GpioController,
                          pins: Set[Pin])
                         (implicit actorRefFactory: ActorRefFactory): ActorRef =
    actorRefFactory.actorOf(props(gpioController, pins))
}
