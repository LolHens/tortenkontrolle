package org.lolhens.piectrl

import akka.actor.{Actor, ActorSystem, Props}
import akka.io.IO
import org.lolhens.piectrl.gpio.Gpio
import org.lolhens.piectrl.gpio.Gpio.{Connect, Connected, StateChanged}

import scala.language.postfixOps

/**
  * Created by pierr on 04.11.2016.
  */
object Main {
  def main(args: Array[String]): Unit = {
    implicit val actorSystem = ActorSystem()

    /*val gpioControl = new GpioControlImpl(
      pinCount = 8
    )

    ServerActor.actor(gpioControl)*/

    class GpioTestActor extends Actor {
      IO(Gpio) ! Connect

      override def receive: Receive = {
        case Connected =>
          val connection = sender()

          context become {
            case StateChanged(state) =>
              println(state)
          }


      }
    }

    actorSystem.actorOf(Props(new GpioTestActor()))
  }
}
