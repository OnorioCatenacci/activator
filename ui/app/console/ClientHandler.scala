/**
 * Copyright (C) 2013 Typesafe <http://typesafe.com/>
 */
package console

import scala.language.implicitConversions
import akka.actor.{ Props, ActorLogging, Actor, ActorRef }
import console.handler._
import play.api.libs.iteratee.Concurrent
import play.api.libs.json._
import scala.collection.Seq
import activator.analytics.data.{ TimeRange, Scope }
import scala.reflect.ClassTag
import activator.analytics.rest.http.SortingHelpers.{ Descending, Ascending, SortDirection }
import scala.util.{ Failure, Success, Try }
import akka.event.LoggingAdapter
import scala.util.matching.Regex
import console.ClientModuleHandler.{ DeviationModule, RequestModule }
import akka.event.LoggingAdapter

trait ClientHandlerBase extends Actor with ActorLogging with ClientModuleHandler {
  import ClientController._

  def jsonHandlerProps: Props
  def overviewHandlerProps: Props
  def actorsHandlerProps: Props
  def actorHandlerProps: Props
  def playRequestsHandlerProps: Props
  def playRequestHandlerProps: Props
  def deviationsHandlerProps: Props
  def deviationHandlerProps: Props

  var modules = Seq.empty[RawModuleInformation]
  val (enum, channel) = Concurrent.broadcast[JsValue]

  val jsonHandler = context.actorOf(jsonHandlerProps, "jsonHandler")
  val overviewHandler = context.actorOf(overviewHandlerProps, "overviewHandler")
  val actorsHandler = context.actorOf(actorsHandlerProps, "actorsHandler")
  val actorHandler = context.actorOf(actorHandlerProps, "actorHandler")
  val playRequestsHandler = context.actorOf(playRequestsHandlerProps, "playRequestsHandler")
  val playRequestHandler = context.actorOf(playRequestHandlerProps, "playRequestHandler")
  val deviationsHandler = context.actorOf(deviationsHandlerProps, "deviationsHandler")
  val deviationHandler = context.actorOf(deviationHandlerProps, "deviationHandler")

  def onOverviewRequest(in: OverviewHandler.OverviewModuleInfo): Unit = overviewHandler ! in
  def onActorsRequest(in: ActorsHandler.ActorsModuleInfo): Unit = actorsHandler ! in
  def onActorRequest(in: ActorHandler.ActorModuleInfo): Unit = actorHandler ! in
  def onPlayRequestsRequest(in: PlayRequestsHandler.PlayRequestsModuleInfo): Unit = playRequestsHandler ! in
  def onPlayRequestRequest(in: PlayRequestHandler.PlayRequestModuleInfo): Unit = playRequestHandler ! in
  def onDeviationsRequest(in: DeviationsHandler.DeviationsModuleInfo): Unit = deviationsHandler ! in
  def onDeviationRequest(in: DeviationHandler.DeviationModuleInfo): Unit = deviationHandler ! in

  def receive = {
    case Tick => modules filter { m => !ClientModuleHandler.oneTimeHandlers.contains(m.module) } foreach callHandler
    case Update(js) => channel.push(js)
    case r: HandleRequest => jsonHandler ! r
    case mi: RawModuleInformation => callHandler(mi)
    case InitializeCommunication => sender ! Connection(self, enum)
    case RegisterModules(newModules) =>
      modules = newModules
      for { mi <- newModules } self ! mi
  }
}

class ClientHandler(val jsonHandlerProps: Props,
  val overviewHandlerProps: Props,
  val actorsHandlerProps: Props,
  val actorHandlerProps: Props,
  val playRequestsHandlerProps: Props,
  val playRequestHandlerProps: Props,
  val deviationsHandlerProps: Props,
  val deviationHandlerProps: Props) extends ClientHandlerBase

class JsonHandler extends Actor with ActorLogging with RequestHelpers {
  import ClientController._
  import JsonHandler._

  implicit val reader = innerModuleReads

  def receive = {
    case HandleRequest(js) => sender ! RegisterModules(parseRequest(js, log))
  }
}

trait RequestHelpers { this: ActorLogging =>
  import JsonHandler._

  def parseRequest(js: JsValue, log: LoggingAdapter): Seq[RawModuleInformation] = {
    val time = toTimeRange((js \ "time" \ "rolling").asOpt[String], log)

    val innerModules = (js \ "modules").as[List[InnerModuleInformation]]
    innerModules map { i =>
      ClientModuleHandler.fromString(i.name) match {
        case Some(name) => RawModuleInformation(
          module = name,
          scope = toScope(i.scope, log),
          time = time,
          pagingInformation = i.pagingInformation,
          dataFrom = i.dataFrom,
          sortCommand = i.sortCommand,
          sortDirection = i.sortDirection,
          traceId = i.traceId)
        case None => sys.error(s"Could not find requested module: ${i.name}")
      }
    }
  }

  def toScope(i: InternalScope, log: LoggingAdapter): Scope =
    Scope(
      path = i.actorPath,
      tag = i.tag,
      node = i.node,
      dispatcher = i.dispatcher,
      actorSystem = i.actorSystem,
      playPattern = i.playPattern,
      playController = i.playController)

  def toTimeRange(rolling: Option[String], log: LoggingAdapter): TimeRange = rolling match {
    case RollingMinutePattern(value) => TimeRange.minuteRange(System.currentTimeMillis, value.toInt)
    case x =>
      log.warning("Can not use parsed time range (using default 20 minutes instead): %s", x)
      TimeRange.minuteRange(System.currentTimeMillis, 20)
  }
}

object JsonHandler {
  import play.api.libs.json._
  import play.api.libs.functional.syntax._
  import SortDirections._

  implicit val scopeReads = (
    (__ \ "node").readNullable[String] and
    (__ \ "actorSystem").readNullable[String] and
    (__ \ "dispatcher").readNullable[String] and
    (__ \ "tag").readNullable[String] and
    (__ \ "actorPath").readNullable[String] and
    (__ \ "playPattern").readNullable[String] and
    (__ \ "playController").readNullable[String])(InternalScope)

  implicit val pagingReads = (
    (__ \ "offset").read[Int] and
    (__ \ "limit").read[Int])(PagingInformation)

  implicit val innerModuleReads = (
    (__ \ "name").read[String] and
    (__ \ "traceId").readNullable[String] and
    (__ \ "paging").readNullable[PagingInformation] and
    (__ \ "sortCommand").readNullable[String] and
    (__ \ "sortDirection").readNullable[SortDirection] and
    (__ \ "dataFrom").readNullable[Long] and
    (__ \ "scope").read[InternalScope])(InnerModuleInformation)

  final val RollingMinutePattern = """^.*rolling=([1-9][0-9]?)minute[s]?.*""".r
}

case class RegisterModules(moduleInformation: Seq[RawModuleInformation])

case class ScopeModifiers(
  anonymous: Boolean = false,
  temporary: Boolean = false)

case class InternalScope(
  node: Option[String] = None,
  actorSystem: Option[String] = None,
  dispatcher: Option[String] = None,
  tag: Option[String] = None,
  actorPath: Option[String] = None,
  playPattern: Option[String] = None,
  playController: Option[String] = None) {
}

case class InnerModuleInformation(
  name: String,
  traceId: Option[String],
  pagingInformation: Option[PagingInformation],
  sortCommand: Option[String],
  sortDirection: Option[SortDirection],
  dataFrom: Option[Long],
  scope: InternalScope)

trait ModuleInformationBase {
  def scope: Scope
  def modifiers: ScopeModifiers
  def time: TimeRange
  def dataFrom: Option[Long]
  def traceId: Option[String]
}

object SortDirections {
  implicit val reads: Reads[SortDirection] = Reads {
    case JsString(s) => JsSuccess(SortDirection(s))
    case x @ _ => JsError(s"invalid input value for sort direction: $x")
  }

  implicit class SortDirectionsHelpers(in: activator.analytics.rest.http.SortingHelpers.SortDirection) {
    def toLegacy: activator.analytics.data.Sorting.SortDirection = in match {
      case Ascending => activator.analytics.data.Sorting.ascendingSort
      case Descending => activator.analytics.data.Sorting.descendingSort
      case x @ _ => sys.error(s"Unable to convert $x to a legacy sort value")
    }
  }
}

trait MultiValueModuleInformation[S] extends ModuleInformationBase {
  def pagingInformation: Option[PagingInformation]
  def sortOn: S
  def sortDirection: SortDirection
}

case class RawModuleInformation(
  module: ClientModuleHandler.Handler,
  scope: Scope,
  modifiers: ScopeModifiers = ScopeModifiers(),
  time: TimeRange,
  pagingInformation: Option[PagingInformation] = None,
  sortCommand: Option[String],
  sortDirection: Option[SortDirection],
  dataFrom: Option[Long] = None,
  traceId: Option[String] = None) extends ModuleInformationBase {
}

case class PagingInformation(offset: Int, limit: Int)

trait WithPaging[S <: MultiValueModuleInformation[_]] {
  def defaultLimit: Int
  lazy val defaultOffset: Int = 0

  def withPagingDefaults[B](moduleInformation: S, defaultLimit: Int = defaultLimit, defaultOffset: Int = defaultOffset)(body: (Int, Int) => B): B = {
    val (offset, limit) = moduleInformation.pagingInformation.map(x => (x.offset, x.limit)).getOrElse((defaultOffset, defaultLimit))
    body(offset, limit)
  }
}
