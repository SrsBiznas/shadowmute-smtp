package com.shadowmute.ingest

import akka.stream.{Inlet, Outlet, Shape}

import scala.collection.immutable

trait SwitchTarget

case object InsecureTunnelEnabled extends SwitchTarget

case object SecureTunnelEnabled extends SwitchTarget

case class SwitchingBidiShape[Ingress, Egress](
    dataIn: Inlet[Ingress],
    plaintextWrapperIn: Outlet[Ingress],
    secureWrapperIn: Outlet[Ingress],
    processedResult: Inlet[(Egress, Option[SwitchTarget])],
    plainTextWrapperOut: Outlet[Egress],
    secureWrapperOut: Outlet[Egress]
) extends Shape {
  override def inlets: immutable.Seq[Inlet[_]] =
    dataIn :: processedResult :: Nil

  override def outlets: immutable.Seq[Outlet[_]] =
    plaintextWrapperIn :: secureWrapperIn :: plainTextWrapperOut :: secureWrapperOut :: Nil

  override def deepCopy(): Shape =
    SwitchingBidiShape(
      dataIn.carbonCopy(),
      plaintextWrapperIn.carbonCopy(),
      secureWrapperIn.carbonCopy(),
      processedResult.carbonCopy(),
      plainTextWrapperOut.carbonCopy(),
      secureWrapperOut.carbonCopy()
    )
}
