package org.mauritania.main4ino.security

import org.http4s.headers.Authorization
import org.http4s.{BasicCredentials, Headers, Uri}
import org.mauritania.main4ino.security.Authentication.EncryptionConfig
import org.mauritania.main4ino.security.Fixtures._
import org.reactormonk.{CryptoBits, PrivateKey}
import org.scalatest.{Matchers, WordSpec}

class AuthenticationSpec extends WordSpec with Matchers {

  def Crypto = CryptoBits(PrivateKey(scala.io.Codec.toUTF8(" " * 20)))

  val encConfig = EncryptionConfig(Crypto, Salt)
  val User1Token = BasicCredentials(User1.id, User1Pass).token

  "The authentication" should {

    "fail when no token is provided in the request" in {
      val h = Headers()
      val u = Uri.unsafeFromString("http://main4ino.com/api/v1/")
      val t = Authentication.userCredentialsFromRequest(encConfig, h, u)
      t shouldBe None
    }

    "retrieve token from header as Authorization: token <token>" in {
      val headers = Headers(Authorization(BasicCredentials(User1.id, User1Pass)))
      val uri = Uri.unsafeFromString("http://main4ino.com/api/v1/device/...")
      val creds = Authentication.userCredentialsFromRequest(encConfig, headers, uri)
      creds shouldBe Some((User1.id, User1.hashedPass))
    }

    "retrieve token from uri as .../token/<token/..." in {
      val headers = Headers()
      val uri = Uri.unsafeFromString(s"http://main4ino.com/api/v1/token/${User1Token}/device/...")
      val creds = Authentication.userCredentialsFromRequest(encConfig, headers, uri)
      creds shouldBe Some((User1.id, User1.hashedPass))
    }

    "correctly identify not allowed users to certain uris" in {
      val user = User1.copy(permissionPatterns = List("/api/v1/"))
      val uriPath = "/admin"
      val authorizationAttempt = Authentication.checkAccess(user, uriPath)
      authorizationAttempt shouldBe Left(s"User ${user.id} is not authorized to access resource /admin")
    }

    "correctly identify allowed users to certain uris" in {
      val user = User1.copy(permissionPatterns = List("/api/v1/"))
      val uriPath = "/api/v1/smth"
      val authorizationAttempt = Authentication.checkAccess(user, uriPath)
      authorizationAttempt shouldBe Right(user)
    }

  }
}