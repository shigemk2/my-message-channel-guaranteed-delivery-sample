package com.example

import java.util.Date

import akka.actor.{Actor, ActorPath, Props}
import akka.persistence.{Channel, ConfirmablePersistent, Deliver, PersistenceFailure, Persistent, Processor, Recover, SnapshotSelectionCriteria}
import com.example._

object GuaranteedDeliveryDriver extends CompletableApp(2) {
  val analyzer1 =
    system.actorOf(Props[OrderAnalyzer], "orderAnalyzer1")

  val orderProcessor1 =
    system.actorOf(Props(classOf[OrderProcessor], analyzer1.path), "orderProcessor1")

  val orderId = new Date().getTime.toString
  println(s"Processing: $orderId")
  orderProcessor1 ! Persistent(ProcessOrder(orderId, "Details..."))

  awaitCompletion
}

case class ProcessOrder(orderId: String, details: String)

class OrderProcessor(orderAnalyzer: ActorPath) extends Processor {
  val channel =
    context.actorOf(
      Channel.props(),
      s"${self.path.name}-channel"
    )

  override def preStart() = {
    self ! Recover(replayMax = 0L)
  }

  def receive = {
    case message @ Persistent(actualMessage, sequenceNumber) =>
      print(s"Handling persisted: $sequenceNumber: ")
      actualMessage match {
        case processOrder: ProcessOrder =>
          println(s"ProcessOrder: $processOrder")
          channel ! Deliver(message, orderAnalyzer)
          GuaranteedDeliveryDriver.completedStep
        case unknown: Any =>
          println(s"Unknown: $unknown")
      }
    case PersistenceFailure(actualMessage, sequenceNumber, cause) =>
      println(s"Handling failed persistent: acutalMessage")
      GuaranteedDeliveryDriver.completedStep
    case non_persisted: Any =>
      println(s"Handling non-persistent: $non_persisted")
      GuaranteedDeliveryDriver.completedStep
  }
}

class OrderAnalyzer extends Actor {
  def receive = {
    case confirmable @ ConfirmablePersistent(
    actualMessage, sequenceNumber, redeliveries) =>

      println(s"OrderAnalyzer: $actualMessage")
      confirmable.confirm
      GuaranteedDeliveryDriver.completedStep
  }
}