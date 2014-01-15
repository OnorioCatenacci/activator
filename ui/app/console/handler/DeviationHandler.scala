/**
 * Copyright (C) 2013 Typesafe <http://typesafe.com/>
 */
package console
package handler

import akka.actor.{ ActorRef, Props }
import activator.analytics.data.{ TimeRange, Scope, ActorStats }
import com.typesafe.trace.uuid.UUID

object DeviationHandler {
  case class DeviationModuleInfo(eventID: UUID) extends ModuleInformationBase
}

trait DeviationHandlerBase extends RequestHandlerLike[DeviationHandler.DeviationModuleInfo] {
  import DeviationHandler._

  def useDeviation(sender: ActorRef, )

  def onModuleInformation(sender: ActorRef, mi: DeviationModuleInfo): Unit = {
    useDeviation(sender, ActorStats.concatenate(repository.traceRepository.event().findWithinTimePeriod(mi.time, mi.scope), mi.time, mi.scope))
  }
}

class DeviationHandler(builderProps: Props) extends RequestHandler[ActorHandler.DeviationModuleInfo] with DeviationHandlerBase {
  val builder = context.actorOf(builderProps, "deviationBuilder")

  def useActorStats(sender: ActorRef, stats: ActorStats): Unit = {
    stats.metrics.deviationDetails.
  }
}
