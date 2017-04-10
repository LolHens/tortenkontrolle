package org.lolhens.piectrl.gpio

import akka.actor.{Actor, ActorRef, ActorRefFactory, Props}
import akka.routing.{BroadcastRoutingLogic, Router}
import com.pi4j.io.gpio.GpioController
import com.pi4j.io.gpio.event.{GpioPinDigitalStateChangeEvent, GpioPinListener, GpioPinListenerDigital}
import org.lolhens.piectrl.gpio.Gpio.{Pin, SetState, StateChanged}
import shapeless.Lazy

/**
  * Created by pierr on 07.04.2017.
  */
class GpioConnection(gpioController: GpioController,
                     pins: Set[Pin]) extends Actor {
  var eventRouter = Router(BroadcastRoutingLogic())

  var listeners: Map[Pin, Lazy[Unit]] = pins.map(pin =>
    pin -> Lazy(gpioController.addListener(new GpioPinListenerDigital {
      override def handleGpioPinDigitalStateChangeEvent(event: GpioPinDigitalStateChangeEvent): Unit = ???
    }, pin))
  )


  override def receive: Receive = {
    case Register(ref) =>
      context watch ref
      eventRouter = eventRouter.addRoutee(ref)
      upnpDevices.foreach(ref ! DeviceAdded(upnpService.getRegistry, _))

    case Terminated(ref) =>
      eventRouter = eventRouter.removeRoutee(ref)

    case SetState(states) =>
      states.filter(e => pins.contains(e._1)).groupBy(_._2).map(e => (e._1, e._2.keys.toSet)).foreach {
        case (None, pins) =>


        case (Some(state), pins) =>
          sender() ! StateChanged(pins.map(_ -> state).toMap)
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
