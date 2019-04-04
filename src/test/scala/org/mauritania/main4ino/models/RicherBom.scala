package org.mauritania.main4ino.models

import org.mauritania.main4ino.models.ActorTup.Status

object RicherBom {

  implicit class DeviceRich(val d: Device) extends AnyVal {

    def withId(i: Option[RecordId]): Device = d.copy(metadata = d.metadata.copy(id = i))

    def withDeviceName(n: DeviceName): Device = d.copy(metadata = d.metadata.copy(device = n))

    def withStatus(s: Status): Device = Device.fromActorTups(d.metadata, d.asActorTups.map(_.copy(status = s)))

    def withTimestamp(t: EpochSecTimestamp): Device = d.copy(metadata = d.metadata.copy(creation = Some(t)))

    def withouIdNortTimestamp(): Device = d.copy(metadata = d.metadata.copy(id = None, creation = None))

  }

}
