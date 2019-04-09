package org.mauritania.main4ino.api.v1

import java.time.{ZoneId, ZonedDateTime}

import cats.data.{Kleisli, OptionT}
import cats.effect.Sync
import cats.implicits._
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`Content-Type`
import org.http4s.server.AuthMiddleware
import org.http4s.{AuthedService, HttpService, MediaType, Request, Response}
import org.mauritania.main4ino.Repository
import org.mauritania.main4ino.Repository.Table.Table
import org.mauritania.main4ino.api.Translator
import org.mauritania.main4ino.api.Translator.{CountResponse, IdsOnlyResponse}
import org.mauritania.main4ino.helpers.Time
import org.mauritania.main4ino.models.Device.Metadata
import org.mauritania.main4ino.models.Device.Metadata.{Status => MdStatus}
import org.mauritania.main4ino.models._
import org.mauritania.main4ino.security.Authentication.AccessAttempt
import org.mauritania.main4ino.security.{Authentication, User}

import scala.util.{Failure, Success, Try}

class Service[F[_] : Sync](auth: Authentication[F], tr: Translator[F], time: Time[F]) extends Http4sDsl[F] {

  import Service._
  import Url._

  type ErrMsg = String

  private val HelpMsg =

    s"""
       | API HELP
       | --- ----
       |
       | See: https://github.com/mauriciojost/main4ino-server/blob/master/src/main/scala/org/mauritania/main4ino/api/v1/Service.scala
       |
       | HELP
       | ----
       |
       |
       | GET /help
       |
       |    Display this help.
       |
       |    Returns: OK (200)
       |
       |
       | TIME
       | ----
       |
       | All below queries are useful for time synchronization.
       |
       |
       | GET /time?timezone=<tz>
       |
       |    Return the ISO-local-time formatted time at a given timezone.
       |
       |    Examples of valid timezones: UTC, Europe/Paris, ...
       |    Examples of formatted time: 1970-01-01T00:00:00
       |
       |    Returns: OK (200) | BAD_REQUEST (400)
       |
       |
       | USER
       | ----
       |
       |
       | POST /session (with standard basic auth)
       |
       |    Return the session id from the basic auth provided credentials.
       |
       |    The provided token can be used to authenticate without providing user/password.
       |
       |    Returns: OK (200)
       |
       |
       | GET /user
       |
       |    Return the currently logged in user id.
       |
       |    Returns: OK (200)
       |
       |
       | ADMINISTRATOR
       | -------------
       |
       | All below queries apply to both targets and reports (although in the examples they use targets).
       |
       |
       | DELETE /administrator/devices/<dev>/targets/
       |
       |    Delete all targets for the given device.
       |
       |    To use with extreme care.
       |
       |    Returns: OK (200)
       |
       |
       | DEVICES
       | -------
       |
       | All below queries are applicable to both targets and reports (although in the examples they use targets).
       |
       |
       | POST /devices/<dev>/targets/
       |
       |    Create a target, get the request ID.
       |
       |    Mostly used by the device (mode reports).
       |    If not actor-properties provided the request remains in state open, waiting for properties
       |    to be added. It should be explicitly closed so that it is exposed to devices.
       |
       |    Returns: CREATED (201)
       |
       |
       | PUT /devices/<dev>/targets/<request_id>
       |
       |    Update the target given the device and the request ID.
       |
       |    Returns: OK (200)
       |
       |
       | GET /devices/<dev>/targets/<request_id>
       |
       |    Retrieve a target by its request ID.
       |
       |    Returns: OK (200) | NO_CONTENT (204)
       |
       |
       | GET /devices/<dev>/targets/last
       |
       |    Retrieve the last target created (chronologically).
       |
       |    Returns: OK (200) | NO_CONTENT (204)
       |
       |
       | GET /devices/<dev>/targets?from=<timestamp>&to=<timestamp>
       |
       |    Retrieve the list of the targets that where created in between the time range provided (timestamp in [ms] since the epoch).
       |
       |    Returns: OK (200)
       |
       |
       | GET /devices/<dev>/targets/summary?status=<status>&consume=<consume>
       |
       |    Retrieve the list of the targets summarized for the device (most recent actor-prop value wins).
       |
       |    The summarized target is generated only using properties that have the given status.
       |    The flag consume tells if the status of the matching properties should be changed from C (created) to X (consumed).
       |
       |    Returns: OK (200) | NO_CONTENT (204)
       |
       |
       | GET /devices/<dev>/targets/count?status=<status>
       |
       |    Count the amount of target-properties with the given status for the device.
       |
       |    This is useful to know in advance if is worth to perform a heavier query to retrieve actors properties.
       |
       |    Returns: OK (200)
       |
       |
       | ACTORS
       | ------
       |
       | All below queries apply to both targets and reports (although in the examples they use targets).
       |
       |
       | POST /devices/<dev>/targets/<requestid>/actors/<actor>
       |
       |    Create a new target for a given actor with the provided actor properties.
       |
       |    An existent request can be filled in if the request ID is provided.
       |
       |    Returns: CREATED (201)
       |
       |
       | GET /devices/<dev>/targets/<requestid>/actors/<actor>?status=<status>&consume=<consume>
       |
       |    Retrieve the list of the targets for the device-actor (most recent actor-prop value wins)
       |
       |    The list is generated only using properties that have the given status.
       |    The flag consume tells if the status of the matching properties should be changed from C (created) to X (consumed).
       |
       |    Returns: OK (200)
       |
       |
       | GET /devices/<dev>/targets/actors/<actor>/summary?status=<status>&consume=<consume>
       |
       |    Retrieve the summary of the targets for the device-actor (most recent actor-prop value wins)
       |
       |    The summarized target is generated only using properties that have the given status.
       |    The flag consume tells if the status of the matching properties should be changed from C (created) to X (consumed).
       |
       |    Returns: OK (200) | NO_CONTENT (204)
       |
       |
       | GET /devices/<dev>/targets/actors/<actor>/last?status=<status>
       |
       |    Retrieve the last target created for such actor with such status
       |
       |    Returns: OK (200) | NO_CONTENT (204)
       |
       |
    """.stripMargin

  implicit val jsonStringDecoder = JsonEncoding.StringDecoder

  private[v1] val service = AuthedService[User, F] {

    // Help


    // To be used by developers
    case GET -> _ / "help" as _ =>
      Ok(HelpMsg, ContentTypeTextPlain)

    // Date/Time

    // To be used by devices to get sync in time
    case GET -> _ / "time" :? TimezoneParam(tz) as _ => {
      val attempt = Try(tr.nowAtTimezone(tz.getOrElse("UTC")))
      attempt match {
        case Success(v) => Ok(v.map(_.asJson), ContentTypeTextPlain)
        case Failure(f) => BadRequest()
      }
    }

    // User

    // To be used by web ui to retrieve a session token
    case a@POST -> _ / "session" as user => {
      val session = auth.generateSession(user)
      session.flatMap(s => Ok(s))
    }

    // To be used by web ui to verify login
    case a@GET -> _ / "user" as user => {
      Ok(user.name)
    }


    // Administration

    // To be used by web ui to fully remove records for a given device table
    case a@DELETE -> _ / "administrator" / "devices" / Dvc(device) / Tbl(table) as _ => {
      val x = tr.deleteDev(device, table)
      Ok(x.map(_.asJson), ContentTypeAppJson)
    }

    // Targets & Reports (at device level)

    // To be used by devices to start a ReqTran (request transaction)
    case a@POST -> _ / "devices" / Dvc(dn) / Tbl(table) as _ => {
      val am = a.req.decodeJson[ActorMap]
      val d = for {
        a <- am
        t <- time.nowUtc
        ts = Time.asTimestamp(t)
        de = Device(dn, ts, a)
      } yield (de)
      val x = tr.postDev(d, table)
      Created(x.map(_.asJson), ContentTypeAppJson)
    }

    // To be used by web ui to retrieve history of transactions in a given time period
    case a@GET -> _ / "devices" / Dvc(device) / Tbl(table) :? FromParam(from) +& ToParam(to) +& StatusParam(st) +& IdsParam(ids) as _ => {
      val x = tr.getDevAll(device, table, from, to, st)
      val idsOnly = ids.exists(identity)
      if (idsOnly) {
        Ok(x.map(i => IdsOnlyResponse(i.flatMap(_.metadata.id).toSeq.sorted).asJson), ContentTypeAppJson)
      } else {
        Ok(x.map(_.asJson), ContentTypeAppJson)
      }
    }

    // To be used by web ui to retrieve summary of transactions in a given time period
    case a@GET -> _ / "devices" / Dvc(device) / Tbl(table) / "summary" :? FromParam(from) +& ToParam(to) +& StatusParam(st) as _ => {
      val x = tr.getDevAllSummary(device, table, from, to, st)
      x.flatMap {
        case Some(v) => Ok(v.actors.asJson, ContentTypeAppJson)
        case None => NoContent()
      }
    }


    // To be used by devices to commit a request
    case a@PUT -> _ / "devices" / Dvc(device) / Tbl(table) / ReqId(requestId) :? StatusParam(st) as _ => {
      val x = tr.updateRequest(table, device, requestId, st.getOrElse(MdStatus.Closed))
      x.flatMap {
        case Right(v) => Ok(v.asJson, ContentTypeAppJson)
        case Left(_) => NotModified()
      }
    }

    // To be used by ... ? // Useful mainly for testing purposes
    case a@GET -> _ / "devices" / Dvc(device) / Tbl(table) / ReqId(requestId) as _ => {
      val x = tr.getDev(table, device, requestId)
      x.flatMap {
        case Right(v) => Ok(v.asJson, ContentTypeAppJson)
        case Left(v) => NoContent()
      }
    }

    // To be used by devices to retrieve last status upon reboot ??? not used seems
    case a@GET -> _ / "devices" / Dvc(device) / Tbl(table) / "last" :? StatusParam(status) as _ => {
      val x = tr.getDevLast(device, table, status)
      x.flatMap {
        case Some(v) => Ok(v.asJson, ContentTypeAppJson)
        case None => NoContent() // ignore message
      }
    }

      /*
    // To be used by web ui to have a summary for a given device
    // calculate the last status using last status reported, and the new one using a merge of all the device requests with status closed (but not consumed), so keep device level
    case a@GET -> _ / "devices" / Dvc(device) / Tbl(table) / "summary" :? StatusParam(status) +& ConsumeParam(consume) as _ => {
      val x = tr.getDevActorTups(device, None, table, status, consume).map(t => ActorMapV1.fromTups(t))
      x.flatMap { m =>
        if (m.isEmpty) {
          NoContent()
        } else {
          Ok(m.asJson, ContentTypeAppJson)
        }
      }
    }
    */

      /*
    // To be used by devices to check if it is worth to request existent transactions
    case a@GET -> _ / "devices" / Dvc(device) / Tbl(table) / "count" :? StatusParam(status) as _ => {
      val x = tr.getDevActorCount(device, None, table, status)
      Ok(x.map(_.asJson), ContentTypeAppJson)
    }
    */

    // Targets & Reports (at device-actor level)

    // To be used by tests to push actor reports (actor by actor) creating a new ReqTran
    case a@POST -> _ / "devices" / Dvc(device) / Tbl(table) / "actors" / Dvc(actor) as _ => {
      val pm = a.req.decodeJson[PropsMap]
      val x = tr.postDevActor(pm, device, actor, table, time.nowUtc)
      Created(x.map(_.asJson), ContentTypeAppJson)
    }

    // To be used by devices to push actor reports (actor by actor) to a given existent ReqTran
    case a@POST -> _ / "devices" / Dvc(device) / Tbl(table) / ReqId(rid) / "actors" / Dvc(actor) as _ => {
      val pm = a.req.decodeJson[PropsMap]
      val x = tr.postDevActor(pm, device, actor, table, rid)
      x.flatMap {
        case Right(v) => Created(CountResponse(v).asJson, ContentTypeAppJson)
        case Left(v) => NotModified()
      }
    }

      /*
    // To be used for testing mainly
    case a@GET -> _ / "devices" / Dvc(device) / Tbl(table) / "actors" / Dvc(actor) / "count" :? StatusParam(status) as _ => {
      val x = tr.getDevActorCount(device, Some(actor), table, status)
      Ok(x.map(_.asJson), ContentTypeAppJson)
    }
    */

    // To be used by devices to pull actor targets (actor by actor)
    // replaced by retrieving the actors of a given request, no summary anymore
      /*
    case a@GET -> _ / "devices" / Dvc(device) / Tbl(table) / "actors" / Dvc(actor) :? StatusParam(status) +& ConsumeParam(consume) as _ => {
      val x = tr.getDevActors(device, actor, table, status, consume)
      Ok(x.map(_.asJson), ContentTypeAppJson)
    }
    */

      /*
    // To be used by devices to pull actor targets (actor by actor) as a summary
    // replaced by retrieving the actors of a given request, no summary anymore
    case a@GET -> _ / "devices" / Dvc(device) / Tbl(table) / "actors" / Dvc(actor) / "summary" :? StatusParam(status) +& ConsumeParam(consume) as _ => {
      val x = tr.getDevActorTups(device, Some(actor), table, status, consume).map(PropsMapV1.fromTups)
      x.flatMap { m =>
        if (m.isEmpty) {
          NoContent()
        } else {
          Ok(m.asJson, ContentTypeAppJson)
        }
      }
    }
    */

    // To be used by devices to see the last status of a given actor (used upon restart)
    case a@GET -> _ / "devices" / Dvc(device) / Tbl(table) / "actors" / Dvc(actor) / "last" :? StatusParam(status) as _ => {
      val x = tr.getDevLast(device, table, status)
      x.flatMap { m =>
        val v = m.flatMap(_.actor(actor))
        v match {
          case Some(x) => Ok(x.asJson, ContentTypeAppJson)
          case None =>  NoContent()
        }
      }
    }

    // To be used by devices to pull actor targets (actor by actor)
    case a@GET -> _ / "devices" / Dvc(device) / Tbl(table) / ReqId(rid) / "actors" / Dvc(actor) as _ => {
      val x = tr.getDev(table, device, rid)
      val v = x.map(e => e.flatMap(d => d.actor(actor).toRight(s"No such actor: $actor")))
      v.flatMap {
        case Right(d) => Ok(d.asJson, ContentTypeAppJson)
        case Left(_) => NoContent()
      }
    }

  }


  private[v1] def logAuthentication(user: AccessAttempt): F[AccessAttempt] = {
    for {
      logger <- Slf4jLogger.fromClass[F](Service.getClass)
      msg = user match {
        case Right(i) => s">>> Authenticated: ${i.name} ${i.email}"
        case Left(m) => s">>> Failed to authenticate: $m"
      }
      _ <- logger.debug(msg)
    } yield (user)
  }

  private[v1] val onFailure: AuthedService[String, F] = Kleisli(req => OptionT.liftF(Forbidden(req.authInfo)))
  private[v1] val customAuthMiddleware: AuthMiddleware[F, User] =
    AuthMiddleware(Kleisli(auth.authenticateAndCheckAccessFromRequest) andThen Kleisli(logAuthentication), onFailure)
  val serviceWithAuthentication: HttpService[F] = customAuthMiddleware(service)

  private[v1] def request(r: Request[F]): F[Response[F]] = serviceWithAuthentication.orNotFound(r)

}

object Service {

  final val ContentTypeAppJson = `Content-Type`(MediaType.`application/json`)
  final val ContentTypeTextPlain = `Content-Type`(MediaType.`text/plain`)

}

