package org.lolhens.piectrl

import akka.actor.{Actor, ActorSystem, Props}
import akka.io.IO
import org.lolhens.piectrl.gpio.Gpio
import org.lolhens.piectrl.gpio.Gpio.{Connect, Connected, SetState, StateChanged}

import scala.language.postfixOps

/**
  * Created by pierr on 04.11.2016.
  */
object Main {
  def main(args: Array[String]): Unit = {println("start")
    implicit val actorSystem = ActorSystem()

    println("setup")
    /*val gpioControl = new GpioControlImpl(
      pinCount = 8
    )

    ServerActor.actor(gpioControl)*/

    class GpioTestActor extends Actor {
      println("connecting")
      IO(Gpio) ! Connect

      override def receive: Receive = {
        case Connected(pins) =>
          println(pins)
          val connection = sender()

          context become {
            case StateChanged(state) =>
              println(state)
          }

          //Thread.sleep(2000)

          connection ! SetState(Map(pins(1) -> None, pins(2) -> Some(true), pins(3) -> Some(false), pins(4) -> None, pins(5) -> None))
      }
    }

    println("creating")
    actorSystem.actorOf(Props(new GpioTestActor()))
    println("done?")
  }
}
