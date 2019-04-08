package org.mauritania.main4ino.models

object RicherBom {

  implicit class DeviceRich(val d: Device) extends AnyVal {

    def withId(i: Option[RecordId]): Device = d.copy(metadata = d.metadata.copy(id = i))

    def withDeviceName(n: DeviceName): Device = d.copy(metadata = d.metadata.copy(device = n))

    /*
    def withStatus(s: Status): Device = Device(d.metadata, d.asActorTups.map(_.copy(status = s)))
    */

    def withTimestamp(t: EpochSecTimestamp): Device = d.copy(metadata = d.metadata.copy(creation = Some(t)))

    def withouIdNortTimestamp(): Device = d.copy(metadata = d.metadata.copy(id = None, creation = None))

  }

}
