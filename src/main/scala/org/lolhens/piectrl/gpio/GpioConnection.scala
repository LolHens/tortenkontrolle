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

  private val output = PinMode.DIGITAL_OUTPUT
  private val input = PinMode.DIGITAL_INPUT

  case class ProvisionedPin(pin: Pin, state: Option[Boolean]) {
    val gpioPin: GpioPinDigitalOutput = state match {
      case Some(high) =>
        val gpioPin = gpioController.provisionDigitalMultipurposePin(pin, output, PinPullResistance.PULL_DOWN)
        gpioPin.setState(high)
        gpioPin

      case None =>
        gpioController.provisionDigitalMultipurposePin(pin, input, PinPullResistance.PULL_DOWN)
    }

    gpioPin.addListener(new GpioPinListenerDigital {
      override def handleGpioPinDigitalStateChangeEvent(event: GpioPinDigitalStateChangeEvent): Unit =
        self ! StateChanged(pin, event.getState.isHigh)
    })

    def setState(newState: Option[Boolean]): ProvisionedPin =
      if (newState != state) {
        newState match {
          case Some(high) =>
            if (state.isEmpty)
              gpioPin.setMode(output)

            gpioPin.setState(high)
            self ! StateChanged(pin, high)

          case None =>
            gpioPin.setMode(input)
        }

        copy(state = newState)
      } else this
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
      states
        .filter(e => pins.contains(e._1))
        .foreach {
          case (pin, state) =>
            val provisionedPin = provisionedPins.get(pin) match {
              case Some(provisionedPin) => provisionedPin.setState(state)
              case None => ProvisionedPin(pin, state)
            }

            provisionedPins + (pin -> provisionedPin)
        }
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
