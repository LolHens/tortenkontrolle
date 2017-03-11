package org.lolhens.piectrl

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.io.Tcp._
import akka.io.{IO, Tcp}
import akka.util.ByteString
import org.lolhens.piectrl.ServerActor.{AddClient, RemoveClient}
import scodec.bits.ByteVector

/**
  * Created by pierr on 11.03.2017.
  */
private class ServerActor(gpioControl: GpioControl) extends Actor {
  private var clients = List[ActorRef]()

  import context.system

  IO(Tcp) ! Tcp.Bind(self, new InetSocketAddress("0.0.0.0", 11641))

  override def receive: Receive = {
    case failed@CommandFailed(_: Bind) =>
      println(s"bind failed: $failed")
      context.stop(self)

    case Connected(_, _) =>
      val connection = sender()
      connection ! Register(
        context.actorOf(ClientActor.props(connection, self)),
        useResumeWriting = false
      )

    case AddClient(clientActor) =>
      clients = clientActor +: clients

      val state = gpioControl.state.toByte
      clientActor ! SendData(ByteVector(state))

    case RemoveClient(clientActor) =>
      clients = clients.filterNot(_ == clientActor)

    case SendData(data) =>
      clients.foreach(_ ! SendData(data))

    case ReceiveData(data) =>
      val state = data(0)
      gpioControl.state = state
      self ! SendData(ByteVector(state))
  }
}

object ServerActor {
  def props(gpioControl: GpioControl): Props = Props(classOf[ServerActor], gpioControl)

  def actor(gpioControl: GpioControl)(implicit actorSystem: ActorSystem): ActorRef =
    actorSystem.actorOf(props(gpioControl))

  case class AddClient(clientActor: ActorRef)

  case class RemoveClient(clientActor: ActorRef)

}

private class ClientActor(connection: ActorRef, serverActor: ActorRef) extends Actor {
  override def receive: Receive = {
    case data: SendData =>
      connection ! Write(data.byteString)

    case Received(data) =>
      serverActor ! ReceiveData(data)

    case failed@CommandFailed(_: Write) =>
      println(s"write failed: $failed")

    case closed: ConnectionClosed =>
      println(s"connection closed $closed")
      context.stop(self)
  }

  override def preStart(): Unit = serverActor ! ServerActor.AddClient(self)

  override def postStop(): Unit = serverActor ! ServerActor.RemoveClient(self)
}

object ClientActor {
  def props(connection: ActorRef, serverActor: ActorRef): Props =
    Props(classOf[ClientActor], connection, serverActor)

  def actor(connection: ActorRef, serverActor: ActorRef)(implicit actorSystem: ActorSystem): ActorRef =
    actorSystem.actorOf(props(connection, serverActor))
}

case class ReceiveData(data: ByteVector) {
  lazy val byteString = ByteString(data.toByteBuffer)
}

object ReceiveData {
  def apply(byteString: ByteString): ReceiveData = ReceiveData(ByteVector(byteString.toByteBuffer))
}

case class SendData(data: ByteVector) {
  lazy val byteString = ByteString(data.toByteBuffer)
}

object SendData {
  def apply(byteString: ByteString): SendData = SendData(ByteVector(byteString.toByteBuffer))
}

