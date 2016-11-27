package org.lolhens.piectrl

import java.io.OutputStream

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import org.lolhens.piectrl.SenderActor.{SendMessage, SendMessageResult}

import scala.util.Try

/**
  * Created by pierr on 27.11.2016.
  */
class SenderActor(outputStream: OutputStream) extends Actor {
  override def receive: Receive = {
    case SendMessage(msg) =>
      sender ! SendMessageResult(Try {
        println(msg)
        outputStream.write(msg)
        outputStream.flush()
      })
  }
}

object SenderActor {
  def props(outputStream: OutputStream): Props = Props(new SenderActor(outputStream))

  def actor(outputStream: OutputStream)(implicit actorSystem: ActorSystem): ActorRef = actorSystem.actorOf(props(outputStream))

  case class SendMessage(msg: Int)

  case class SendMessageResult(result: Try[Unit])

}
