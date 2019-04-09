package org.mauritania.main4ino.models

import org.mauritania.main4ino.Repository.{ActorTup, ActorTupIdLess}
import org.mauritania.main4ino.models.Device.Metadata
import org.mauritania.main4ino.models.Device.Metadata.Status

case class Device(
  metadata: Metadata,
  actors: DeviceProps = Device.EmptyActorMap
) {

  def asActorTups: Iterable[ActorTupIdLess] =
    for {
      (actor, ps) <- actors.toSeq
      (propName, propValue) <- ps.toSeq
    } yield (ActorTupIdLess(actor, propName, propValue))

  def actor(s: ActorName): Option[ActorProps] = actors.get(s)

}

object Device {

  val EmptyActorMap: DeviceProps = Map.empty[ActorName, ActorProps]

  /**
    * It represents a request that relates to a specific device
    */
  case class Metadata(
    id: Option[RequestId],
    creation: Option[EpochSecTimestamp],
    device: DeviceName,
    status: Status
  )

  object Metadata {

    type Status = String // TODO have type safety here

    object Status {
      // Natural progression: O -> C -> X
      val Open: Status = "O" // modifications can be done on it (add properties)
      val Closed: Status = "C" // modifications cannot be done on it anymore
      val Consumed: Status = "X" // it has been already consumed (no modifications can be done on it)

      val Unknown: Status = "?" // status not defined
    }

  }

  def apply(dev: DeviceName, ts: EpochSecTimestamp, am: DeviceProps): Device = {
    val status = if (am.isEmpty) Status.Open else Status.Closed
    Device(Metadata(None, Some(ts), dev, status), am)
  }

  /**
    * Merge several [[ActorTup]] of the same device
    *
    * Perform grouping of all [[ActorTup]] belonging by actor-property and
    * keep the one provided in the latest request ID.
    *
    * @param actorTups all [[ActorTup]] to be merged belonging to the same device
    * @return a merged [[DeviceProps]] with only one value per actor-property
    */
  def merge(d1: Device, d2: Device): Device = {
    assert(d1.metadata.device == d2.metadata.device)
    val dname = d2.metadata.device
    Device(Metadata(None, None, dname, Status.Unknown), resolve(d1, d2))
  }

  private def resolve(d1: Device, d2: Device): DeviceProps = {
    def flat(r: Option[RequestId], am: DeviceProps): Iterable[(ActorName, PropName, PropValue, Option[RequestId])] = am.flatMap{case (an, ps) => ps.map{case (pn, pv) => (an, pn, pv, r)}}
    val as1 = flat(d1.metadata.id, d1.actors)
    val as2 = flat(d2.metadata.id, d2.actors)
    val as = as1 ++ as2
    val props = as.groupBy(_._1)
      .mapValues { byActorActorTups =>
        byActorActorTups.groupBy(_._2)
          .mapValues { byActorPropActorTups =>
            val eligibleActorTupPerActorProp = byActorPropActorTups.maxBy(_._4)
            eligibleActorTupPerActorProp._3
          }
      }
    props
  }



}

