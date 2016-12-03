package org.lolhens.piectrl


import java.util.concurrent.locks.ReentrantReadWriteLock

import com.pi4j.io.gpio.{GpioFactory, PinState, RaspiPin}
import com.pi4j.system.SystemInfo.BoardType
import monix.execution.Scheduler.Implicits.global

import scala.concurrent.Future

/**
  * Created by pierr on 04.11.2016.
  */
class GpioControl(val pinOffset: Int = 0,
                  val pinCount: Int) {
  private val gpio = GpioFactory.getInstance()
  private val pins = (pinOffset until pinCount).map { i =>
    gpio.provisionDigitalOutputPin(RaspiPin.allPins(null: BoardType).apply(i), PinState.HIGH)
  }

  private val lock = new ReentrantReadWriteLock()

  @volatile private var _state = 0

  def state: Int = {
    lock.readLock().lock()
    val result = _state
    lock.readLock().unlock()
    result
  }

  def state_=(state: Int): Future[Int] = {
    lock.writeLock().lock()
    _state = state
    lock.writeLock().unlock()

    val active = pins.zipWithIndex.filter {
      case (pin, i) => (state & (1 << i)) != 0
    }.map(_._1)

    Future {
      pins.foreach { pin =>
        if (active.contains(pin))
          pin.setState(PinState.LOW)
        else
          pin.setState(PinState.HIGH)
      }

      state
    }
  }

  def close() = gpio.shutdown()
}
