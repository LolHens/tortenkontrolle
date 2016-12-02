package org.lolhens.piectrl

import java.util.concurrent.locks.ReentrantReadWriteLock

import monix.reactive.Observable

import scala.concurrent.Future
import scala.util.Try

/**
  * Created by pierr on 02.12.2016.
  */
class BoundedQueue[E](val capacity: Option[Long]) {
  private var queue: List[E] = Nil
  private val lock = new ReentrantReadWriteLock()

  private val notFullEvent = new Object()
  private val nonEmptyEvent = new Object()

  def +=(elem: E): Boolean = push(elem)

  def push(elem: E): Boolean = {
    lock.writeLock().lock()
    if (isEmpty) nonEmptyEvent.notifyAll()
    val result = isFull
    if (!isFull) queue = queue :+ elem
    lock.writeLock().unlock()
    result
  }

  def isEmpty: Boolean = {
    lock.readLock().lock()
    val result = queue.isEmpty
    lock.readLock().unlock()
    result
  }

  def isFull: Boolean = {
    lock.readLock().lock()
    val result = capacity.exists(capacity => queue.size >= capacity)
    lock.readLock().unlock()
    result
  }

  def headOption: Option[E] = {
    lock.readLock().lock()
    val result = queue.headOption
    lock.readLock().unlock()
    result
  }

  def pop: Option[E] = {
    lock.writeLock().lock()
    val result = headOption
    queue =
      if (queue.isEmpty)
        Nil
      else {
        notFullEvent.notifyAll() // sync
        queue.tail
      }
    lock.writeLock().unlock()
    result
  }

  def pushBlocking(elem: E): Future[Unit] = Future {
    while (!push(elem))
      Try(notFullEvent.synchronized(notFullEvent.wait()))
  }

  def popBlocking: Future[E] =

  def observable: Observable[E] = {

  }
}
