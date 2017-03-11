package org.lolhens.piectrl

import akka.actor.ActorSystem

import scala.language.postfixOps

/**
  * Created by pierr on 04.11.2016.
  */
object Main {
  def main(args: Array[String]): Unit = {
    implicit val actorSystem = ActorSystem()

    val gpioControl = new GpioControlImpl(
      pinCount = 8
    )

    ServerActor.actor(gpioControl)
  }
}
