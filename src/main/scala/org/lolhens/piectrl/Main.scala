package org.lolhens.piectrl

import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable

import scala.language.postfixOps

/**
  * Created by pierr on 04.11.2016.
  */
object Main {
  def main(args: Array[String]): Unit = {
    val gpioControl = new GpioControl(
      pinCount = 8
    )

    val server = new Server(11641)

    server.messages
      .flatMap(message => Observable.fromFuture(gpioControl.state = message))
      .map(server.broadcast)
      .subscribe()
    //.foreach(println(_))

    while (true) {
      Thread.sleep(1000)
    }
  }
}
