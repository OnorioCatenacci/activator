/**
 * Copyright (C) 2013 Typesafe <http://typesafe.com/>
 */
package controllers.api

import play.api.libs.json._
import com.typesafe.sbtrc._
import play.api.mvc._
import snap.AppManager
import akka.pattern._
import akka.actor._
import scala.concurrent.ExecutionContext.Implicits.global
import play.Logger
import scala.concurrent.Future
import snap.GetTaskActor
import snap.TaskActorReply
import snap.NotifyWebSocket
import java.net.URLEncoder
import snap.UpdateSourceFiles
import snap.JsonHelper._

object Sbt extends Controller {
  implicit val timeout = snap.Akka.longTimeoutThatIsAProblem

  def makeRequestManager(app: snap.App, taskId: String, taskActor: ActorRef, respondWhenReceived: Boolean) =
    new snap.RequestManagerActor(taskId, taskActor, respondWhenReceived)(json => app.actor ! NotifyWebSocket(json))

  def sendRequestGettingEvents(factory: ActorRefFactory, app: snap.App, taskId: String, taskActor: ActorRef, request: protocol.Request, fireAndForget: Boolean): Future[protocol.Message] = {
    val actor = factory.actorOf(
      Props(makeRequestManager(app, taskId, taskActor, fireAndForget)),
      name = "event-tee-" + URLEncoder.encode(taskId, "UTF-8"))
    (actor ? request) map {
      case message: protocol.Message => message
      case whatever => throw new RuntimeException("only expected messages here: " + whatever)
    }
  }

  def sendRequestGettingEventsAndResponse(factory: ActorRefFactory, app: snap.App, taskId: String, taskActor: ActorRef, request: protocol.Request): Future[protocol.Response] = {
    sendRequestGettingEvents(factory, app, taskId, taskActor, request, fireAndForget = false) map {
      case response: protocol.Response => response
      case whatever => throw new RuntimeException("unexpected response: " + whatever)
    }
  }

  def sendRequestGettingAckAndEvents(factory: ActorRefFactory, app: snap.App, taskId: String, taskActor: ActorRef, request: protocol.Request): Future[protocol.Message] = {
    sendRequestGettingEvents(factory, app, taskId, taskActor, request, fireAndForget = true) map {
      case protocol.RequestReceivedEvent => protocol.RequestReceivedEvent
      case response: protocol.ErrorResponse => response
      case whatever => throw new RuntimeException("unexpected response: " + whatever)
    }
  }

  // Incoming JSON should be:
  //  { "appId" : appid, "description" : human-readable,
  //    "taskId" : uuid,
  //    "task" : json-as-we-define-in-sbtchild.protocol }
  // And the reply will be RequestReceivedEvent or ErrorResponse
  def task() = jsonAction { json =>
    val appId = (json \ "appId").as[String]
    val taskId = (json \ "taskId").as[String]
    val taskDescription = (json \ "description").as[String]
    val taskJson = (json \ "task")

    val taskMessage = protocol.WireProtocol.fromRaw(playJsonToRawMap(taskJson)) match {
      case Some(req: protocol.Request) => req
      case whatever => throw new RuntimeException("not a request: " + taskJson)
    }

    val resultFuture = AppManager.loadApp(appId) flatMap { app =>
      withTaskActor(taskId, taskDescription, app) { taskActor =>
        sendRequestGettingAckAndEvents(snap.Akka.system, app, taskId, taskActor, taskMessage) map {
          case protocol.RequestReceivedEvent => protocol.RequestReceivedEvent
          case error: protocol.ErrorResponse => error
          case whatever => throw new RuntimeException("unexpected response: " + whatever)
        } map { message =>
          Ok(rawToPlayJson(protocol.WireProtocol.toRaw(message)))
        }
      }
    }
    resultFuture
  }

  // Incoming JSON { "appId" : appId, "taskId" : uuid }
  // reply is empty
  def killTask() = jsonAction { json =>
    val appId = (json \ "appId").as[String]
    val taskId = (json \ "taskId").as[String]
    val resultFuture = AppManager.loadApp(appId) map { app =>
      app.actor ! snap.ForceStopTask(taskId)
      Ok(JsObject(Nil))
    }
    resultFuture
  }

  // Incoming JSON { "appId" : appId, "taskId" : uuid }
  // the request causes us to reload the sources from sbt and watch them.
  // This is only its own API (vs. "task" above) because we are
  // trying to avoid sending the potentially big list of source files
  // to the client and back. We still want the client to control when
  // we refresh the list, though.
  def watchSources() = jsonAction { json =>
    val appId = (json \ "appId").as[String]
    val taskId = (json \ "taskId").as[String]

    val resultFuture = AppManager.loadApp(appId) flatMap { app =>
      withTaskActor(taskId, "Finding sources to watch for changes", app) { taskActor =>
        sendRequestGettingEventsAndResponse(snap.Akka.system, app, taskId, taskActor,
          protocol.WatchTransitiveSourcesRequest(sendEvents = true)) map {
            case protocol.WatchTransitiveSourcesResponse(files) =>
              val filesSet = files.toSet
              Logger.debug(s"Sending app actor ${filesSet.size} source files")
              app.actor ! UpdateSourceFiles(filesSet)
              // TODO - Should we just send the response back completely?
              Ok(JsObject(Seq("response" -> JsString("WatchTransitiveSourcesResponse"),
                "count" -> JsNumber(filesSet.size))))
            case message: protocol.Message =>
              Ok(rawToPlayJson(protocol.WireProtocol.toRaw(message)))
          }
      }
    }
    resultFuture
  }

  private def jsonAction(f: JsValue => Future[SimpleResult]): Action[AnyContent] = Action.async { request =>
    request.body.asJson.map({ json =>
      try f(json)
      catch {
        case e: Exception =>
          Logger.info("json action failed: " + e.getMessage(), e)
          Future.successful(BadRequest(e.getClass.getName + ": " + e.getMessage))
      }
    }).getOrElse(Future.successful(BadRequest("expecting JSON body")))
  }

  private def withTaskActor[T](taskId: String, taskDescription: String, app: snap.App)(body: ActorRef => Future[T]): Future[T] = {
    (app.actor ? GetTaskActor(taskId, taskDescription)) flatMap {
      case TaskActorReply(taskActor) => body(taskActor)
    }
  }
}
