package org.lolhens.piectrl

import java.net.Socket

import akka.actor.{Actor, ActorRef}
import akka.actor.Actor.Receive

/**
  * Created by pierr on 27.11.2016.
  */
class ClientActor(client: Client, clientManager: ActorRef) extends Actor {
  @scala.throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    clientManager ! ClientManager.AddClientMessage(client)
  }

  override def receive: Receive = {
    //case SendMessage(byte) =>
      //client.
    case _ =>
  }

  @scala.throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    clientManager ! ClientManager.RemoveClientMessage(client)
  }
}

object ClientActor {
  /*case class SendMessage(byte: Int)

  case class ReceiveResult(byte: Int)*/
}
