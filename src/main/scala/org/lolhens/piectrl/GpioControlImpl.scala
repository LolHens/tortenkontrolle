package org.lolhens.piectrl


import com.pi4j.io.gpio.{GpioFactory, PinState, RaspiPin}
import com.pi4j.system.SystemInfo.BoardType

/**
  * Created by pierr on 04.11.2016.
  */
class GpioControlImpl(pinOffset: Int = 0,
                      pinCount: Int) extends GpioControl(pinOffset, pinCount) {
  private val gpio = GpioFactory.getInstance()
  private val pins = (pinOffset until pinCount).map { i =>
    gpio.provisionDigitalOutputPin(RaspiPin.allPins(null: BoardType).apply(i), PinState.HIGH)
  }

  private var _state = 0

  override def state: Int = _state

  override def state_=(state: Int): Unit = {
    _state = state

    val active = pins.zipWithIndex.filter {
      case (_, i) => (state & (1 << i)) != 0
    }.map(_._1)

    pins.foreach { pin =>
      if (active.contains(pin))
        pin.setState(PinState.LOW)
      else
        pin.setState(PinState.HIGH)
    }
  }

  def close(): Unit = gpio.shutdown()
}
