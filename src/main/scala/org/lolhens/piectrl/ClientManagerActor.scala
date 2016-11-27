package org.lolhens.piectrl

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import org.lolhens.piectrl.ClientManager.{AddClientMessage, GetClientsMessage, GetClientsResult, RemoveClientMessage}

/**
  * Created by pierr on 27.11.2016.
  */
class ClientManager extends Actor {
  var clients: List[Client] = Nil

  override def receive: Receive = {
    case AddClientMessage(client) =>
      clients = clients :+ client

    case RemoveClientMessage(client) =>
      clients = clients.filterNot(_ == client)

    case GetClientsMessage =>
      sender() ! GetClientsResult(clients)
  }
}

object ClientManager {
  val props: Props = Props[ClientManager]

  def actor(implicit actorSystem: ActorSystem): ActorRef = actorSystem.actorOf(props)

  case class AddClientMessage(client: Client)

  case class RemoveClientMessage(client: Client)

  case object GetClientsMessage

  case class GetClientsResult(clients: List[Client])

}
