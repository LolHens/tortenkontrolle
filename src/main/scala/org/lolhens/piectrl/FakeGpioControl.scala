package org.lolhens.piectrl

import scala.concurrent.Future

/**
  * Created by pierr on 27.11.2016.
  */
class FakeGpioControl(val pinOffset: Int = 0,
                      val pinCount: Int) {
  @volatile private var _state = 0

  def state: Int = _state

  def state_=(state: Int): Future[Int] = {
    _state = state
    Future.successful(state)
  }
}
