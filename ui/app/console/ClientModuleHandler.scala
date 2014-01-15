package console

import scala.util.{ Failure, Success, Try }
import scala.reflect.ClassTag
import akka.event.LoggingAdapter
import scala.language.implicitConversions

object ClientModuleHandler {
  import activator.analytics.rest.http.SortingHelpers._
  import console.handler._
  import OverviewHandler.OverviewModuleInfo, ActorHandler.ActorModuleInfo, ActorsHandler.ActorsModuleInfo, DeviationHandler.DeviationModuleInfo
  import DeviationsHandler.DeviationsModuleInfo, PlayRequestHandler.PlayRequestModuleInfo, PlayRequestsHandler.PlayRequestsModuleInfo

  sealed abstract class Handler(val name: String) {
    override def toString: String = name
  }

  case object OverviewModule extends Handler("overview")
  case object ActorsModule extends Handler("actors")
  case object ActorModule extends Handler("actor")
  case object RequestsModule extends Handler("requests")
  case object RequestModule extends Handler("request")
  case object DeviationsModule extends Handler("deviations")
  case object DeviationModule extends Handler("deviation")

  def fromString(in: String): Option[Handler] = in match {
    case "overview" => Some(OverviewModule)
    case "actors" => Some(ActorsModule)
    case "actor" => Some(ActorModule)
    case "requests" => Some(RequestsModule)
    case "request" => Some(RequestModule)
    case "deviations" => Some(DeviationsModule)
    case "deviation" => Some(DeviationModule)
    case _ => None
  }

  // Add the name of handlers that should only be invoked once to this collection
  final val oneTimeHandlers = Seq(RequestModule, DeviationModule)

  implicit def toOverviewModuleInfo(in: RawModuleInformation): Try[OverviewModuleInfo] =
    Try {
      OverviewModuleInfo(in.scope,
        in.modifiers,
        in.time,
        in.pagingInformation,
        OverviewHandler.extractSortOn(in.sortCommand),
        in.sortDirection.getOrElse(Descending),
        in.dataFrom,
        in.traceId)
    }

  implicit def toActorModuleInfo(in: RawModuleInformation): Try[ActorModuleInfo] =
    Try {
      ActorModuleInfo(in.scope,
        in.modifiers,
        in.time,
        in.dataFrom,
        in.traceId)
    }

  implicit def toActorsModuleInfo(in: RawModuleInformation): Try[ActorsModuleInfo] =
    Try {
      ActorsModuleInfo(in.scope,
        in.modifiers,
        in.time,
        in.pagingInformation,
        ActorsHandler.extractSortOn(in.sortCommand),
        in.sortDirection.getOrElse(Ascending),
        in.dataFrom,
        in.traceId)
    }

  implicit def toDeviationModuleInfo(in: RawModuleInformation): Try[DeviationModuleInfo] =
    Try {
      DeviationModuleInfo(in.eventId.get)
    }

  implicit def toDeviationsModuleInfo(in: RawModuleInformation): Try[DeviationsModuleInfo] =
    Try {
      DeviationsModuleInfo(in.scope,
        in.modifiers,
        in.time,
        in.pagingInformation,
        DeviationsHandler.extractSortOn(in.sortCommand),
        in.sortDirection.getOrElse(Ascending),
        in.dataFrom,
        in.traceId)
    }

  implicit def toPlayRequestModuleInfo(in: RawModuleInformation): Try[PlayRequestModuleInfo] =
    Try {
      PlayRequestModuleInfo(in.scope,
        in.modifiers,
        in.time,
        in.dataFrom,
        in.traceId)
    }

  implicit def toPlayRequestsModuleInfo(in: RawModuleInformation): Try[PlayRequestsModuleInfo] =
    Try {
      PlayRequestsModuleInfo(in.scope,
        in.modifiers,
        in.time,
        in.pagingInformation,
        PlayRequestsHandler.extractSortOn(in.sortCommand),
        in.sortDirection.getOrElse(Ascending),
        in.dataFrom,
        in.traceId)
    }
}

trait ClientModuleHandler {
  import console.handler._
  import ClientModuleHandler._

  def log: LoggingAdapter

  def onOverviewRequest(in: OverviewHandler.OverviewModuleInfo): Unit
  def onActorsRequest(in: ActorsHandler.ActorsModuleInfo): Unit
  def onActorRequest(in: ActorHandler.ActorModuleInfo): Unit
  def onPlayRequestsRequest(in: PlayRequestsHandler.PlayRequestsModuleInfo): Unit
  def onPlayRequestRequest(in: PlayRequestHandler.PlayRequestModuleInfo): Unit
  def onDeviationsRequest(in: DeviationsHandler.DeviationsModuleInfo): Unit
  def onDeviationRequest(in: DeviationHandler.DeviationModuleInfo): Unit

  def tryExtract[M <: ModuleInformationBase](in: RawModuleInformation)(body: M => Unit)(implicit conv: RawModuleInformation => Try[M], ct: ClassTag[M]): Unit =
    conv(in) match {
      case Success(m) => body(m)
      case Failure(f) => log.error(s"failed to decode $in to type ${ct.toString()}")
    }

  def callHandler(mi: RawModuleInformation) {
    mi.module match {
      case OverviewModule => tryExtract[OverviewHandler.OverviewModuleInfo](mi)(onOverviewRequest)
      case ActorsModule => tryExtract[ActorsHandler.ActorsModuleInfo](mi)(onActorsRequest)
      case ActorModule => tryExtract[ActorHandler.ActorModuleInfo](mi)(onActorRequest)
      case RequestsModule => tryExtract[PlayRequestsHandler.PlayRequestsModuleInfo](mi)(onPlayRequestsRequest)
      case RequestModule => tryExtract[PlayRequestHandler.PlayRequestModuleInfo](mi)(onPlayRequestRequest)
      case DeviationsModule => tryExtract[DeviationsHandler.DeviationsModuleInfo](mi)(onDeviationsRequest)
      case DeviationModule => tryExtract[DeviationHandler.DeviationModuleInfo](mi)(onDeviationRequest)
      case _ => log.debug("Unknown module name: {}", mi.module)
    }
  }
}
