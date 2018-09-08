package org.mauritania.main4ino

import cats.effect.IO
import fs2.StreamApp.ExitCode
import fs2.{Stream, StreamApp}
import org.http4s.dsl.Http4sDsl
import org.http4s.server.blaze.BlazeBuilder
import org.mauritania.main4ino.api.v1
import org.mauritania.main4ino.config.Config
import org.mauritania.main4ino.db.Database
import org.mauritania.main4ino.security.Authentication

import scala.concurrent.ExecutionContext.Implicits.global

object Server extends StreamApp[IO] with Http4sDsl[IO] {

  def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, ExitCode] = {
    for {
      configApp <- Stream.eval(config.Config.load("application.conf"))
      configUsers <- Stream.eval(security.Config.load("users.conf"))
      transactor <- Stream.eval(Database.transactor(configApp.database))
      _ <- Stream.eval(Database.initialize(transactor))
      exitCode <- BlazeBuilder[IO]
        .bindHttp(configApp.server.port, configApp.server.host)
        .mountService(new webapp.Service().service, webapp.Service.ServicePrefix)
        .mountService(new v1.Service(new Authentication(configUsers), new Repository(transactor)).serviceWithAuthentication, v1.Service.ServicePrefix)
        .serve
    } yield exitCode
  }
}