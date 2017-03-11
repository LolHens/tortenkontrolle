package org.lolhens.piectrl

/**
  * Created by pierr on 27.11.2016.
  */
class GpioControl(val pinOffset: Int = 0,
                  val pinCount: Int) {
  def state: Int = 0

  def state_=(state: Int): Unit = ()
}
