package com.shadowmute.ingest

import akka.stream.{Inlet, Outlet, Shape}

import scala.collection.immutable

trait SwitchTarget

case class SwitchTargetA() extends SwitchTarget

case class SwitchTargetB() extends SwitchTarget

case class SwitchingBidiShape[Ingress, Egress](
    dataIn: Inlet[Ingress],
    ingressA: Outlet[Ingress],
    ingressB: Outlet[Ingress],
    processedResult: Inlet[(Egress, Option[SwitchTarget])],
    egressA: Outlet[Egress],
    egressB: Outlet[Egress]
) extends Shape {
  override def inlets: immutable.Seq[Inlet[_]] =
    dataIn :: processedResult :: Nil

  override def outlets: immutable.Seq[Outlet[_]] =
    ingressA :: ingressB :: egressA :: egressB :: Nil

  override def deepCopy(): Shape =
    SwitchingBidiShape(
      dataIn.carbonCopy(),
      ingressA.carbonCopy(),
      ingressB.carbonCopy(),
      processedResult.carbonCopy(),
      egressA.carbonCopy(),
      egressB.carbonCopy()
    )
}
