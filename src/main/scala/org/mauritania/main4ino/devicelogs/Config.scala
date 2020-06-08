package org.mauritania.main4ino.devicelogs

import java.nio.file.Path

import eu.timepit.refined.types.numeric.PosInt

case class Config(
  logsBasePath: Path,
  maxLengthLogs: PosInt = Config.DefaultMaxLengthLogs,
  partitionPos: PosInt = Config.DefaultPartitionPos
)

object Config {
  final lazy val DefaultMaxLengthLogs = PosInt(524288) // 512 KB
  final lazy val DefaultPartitionPos = PosInt(21600) // 6 hours
}
