import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.{GoogleAuthorizationCodeFlow, GoogleClientSecrets}
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.gmail.model.ListLabelsResponse
import com.google.api.services.gmail.{Gmail, GmailScopes}
import zio.*

import java.io.{FileInputStream, IOException, InputStreamReader}
import scala.io.Source
import scala.jdk.CollectionConverters.*

object MainApp extends ZIOAppDefault with Messaging {

  /** Application name. */
  private val APPLICATION_NAME = "Booking Emailer"

  /** Global instance of the JSON factory. */
  private val JSON_FACTORY = GsonFactory.getDefaultInstance

  /** Directory to store authorization tokens for this application. */
  private val TOKENS_DIRECTORY_PATH = "tokens"

  /**
    * Global instance of the scopes required by this quickstart.
    * If modifying these scopes, delete your previously saved tokens/folder.
    */
  private val SCOPES = List(GmailScopes.GMAIL_SEND)
  private val CREDENTIALS_FILE_PATH = "/credentials.json"

  def run: ZIO[Any, Throwable, Unit] =
    for {
      _ <- Console.print("Please enter your sender gmail account: ")
      sender <- Console.readLine
      source <- ZIO.attemptBlockingIO(Source.fromFile(getClass.getResource("/fr_booking_emails.db").getFile))
      recipientsStr <- ZIO.attemptBlockingIO(source.mkString)
      recipientGroups = recipientsStr
        .split("\n")
        .filterNot(s => s.head == '#')
        .grouped(100)
        .toList
      _ <- ZIO.attemptBlockingIO(source.close())
      httpTransport <- ZIO.attemptBlockingIO(GoogleNetHttpTransport.newTrustedTransport())
      credentials <- getCredentials(httpTransport)
      gmailService <- ZIO.attemptUnsafe(_ => new Gmail.Builder(httpTransport, JSON_FACTORY, credentials).setApplicationName(APPLICATION_NAME).build())
      emails <- ZIO.collectAll(
        recipientGroups.map { g =>
          createEmail(
            g.toList,
            sender,
            "Concert punk rock Octobre 22",
            s"""Hello,
        je représente le groupe de punk rock corrézien Breaking Tag (https://www.facebook.com/breakingtagband)\n
        Ils seront en tournée en cette fin d'année pour promouvoir la sortie de leur nouvel EP Sticks and Stones (https://breakingtag.bandcamp.com/album/sticks-and-stones)\n
        À la recherche d'une date de concert les w.e. du 14/15 et 21/22 Octobre 2022.\n
        N'hésitez pas à revenir vers moi si l'organisation d'un concert dans ces créneaux voire même les mois  d'après vous intéresserait.\n
        Merci d'avance et à très vite.\n\n
        Alex (0603909271) pour Breaking Tag"""
          )
        }
      )
      messages <- ZIO.collectAll(emails.map(email => createMessage(email)))
      responses <- ZIO.collectAll(messages.map(message => ZIO.attemptBlockingIO(gmailService.users().messages().send("me", message).execute())))
      _ <- ZIO.collectAll(responses.map(response => Console.print(response.toPrettyString)))
      _ <- Console.print(s"Attempted to send ${recipientGroups.flatten.length} emails")
    } yield ()

  private def getCredentials(httpTransport: NetHttpTransport): ZIO[Any, Throwable, Credential] =
    for {
      stream <- ZIO.attemptBlockingIO(getClass.getResourceAsStream(CREDENTIALS_FILE_PATH))
      clientSecrets <- ZIO.attemptBlockingIO(GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(stream)))
      flow <- getFlow(httpTransport, clientSecrets)
      receiver <- ZIO.attemptUnsafe(_ => new LocalServerReceiver.Builder().setPort(8888).build)
      credentials <- ZIO.attemptBlockingIO(new AuthorizationCodeInstalledApp(flow, receiver).authorize("user"))
    } yield credentials

  private def getFlow(httpTransport: NetHttpTransport, clientSecrets: GoogleClientSecrets): ZIO[Any, Throwable, GoogleAuthorizationCodeFlow] =
    for {
      file <- ZIO.attempt(new java.io.File(TOKENS_DIRECTORY_PATH))
      factory <- ZIO.attemptBlockingIO(new FileDataStoreFactory(file))
      flow <- ZIO.attemptBlockingIO(
        new GoogleAuthorizationCodeFlow.Builder(httpTransport, JSON_FACTORY, clientSecrets, SCOPES.asJavaCollection)
          .setDataStoreFactory(factory)
          .setAccessType("offline")
          .build()
      )
    } yield flow
}
