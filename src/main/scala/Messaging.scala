import com.google.api.services.gmail.model.Message
import org.apache.commons.codec.binary.Base64
import zio.ZIO

import java.io.ByteArrayOutputStream
import java.util.Properties
import javax.mail.Session
import javax.mail.internet.{InternetAddress, MimeMessage}

trait Messaging {

  def createEmail(toEmailAddresses: List[String], fromEmailAddress: String, subject: String, bodyText: String): ZIO[Any, Throwable, MimeMessage] =
    for {
      props <- ZIO.succeed(new Properties())
      session <- ZIO.attempt(Session.getDefaultInstance(props, null))
      from <- ZIO.attempt(new InternetAddress(fromEmailAddress))
      tos <- ZIO.collectAll(toEmailAddresses.map(email => ZIO.attempt(new InternetAddress(email))))
      email <- ZIO.attempt {
        val email = new MimeMessage(session)
        email.setFrom(from)
        email.addRecipients(javax.mail.Message.RecipientType.BCC, tos.mkString(","))
        email.setSubject(subject)
        email.setText(bodyText)
        email
      }
    } yield email

  def createMessage(emailContent: MimeMessage): ZIO[Any, Throwable, Message] =
    for {
      buffer <- ZIO.attempt(new ByteArrayOutputStream())
      bytes <- ZIO.attemptBlockingIO {
        emailContent.writeTo(buffer)
        buffer.toByteArray
      }
      encodedEmail <- ZIO.attempt(Base64.encodeBase64URLSafeString(bytes))
      message <- ZIO.attempt {
        val message = new Message()
        message.setRaw(encodedEmail)
      }
    } yield message
}
