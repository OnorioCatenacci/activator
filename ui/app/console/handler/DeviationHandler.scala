/**
 * Copyright (C) 2013 Typesafe <http://typesafe.com/>
 */
package console
package handler

import akka.actor.{ ActorRef, Props }
import activator.analytics.data.{ TimeRange, Scope, ActorStats }
import com.typesafe.trace.uuid.UUID
import com.typesafe.trace.TraceEvent
import console.handler.rest.DeviationJsonBuilder.DeviationResult

object DeviationHandler {
  case class DeviationModuleInfo(eventID: UUID) extends ModuleInformationBase
}

trait DeviationHandlerBase extends RequestHandlerLike[DeviationHandler.DeviationModuleInfo] {
  import DeviationHandler._

  def useDeviation(sender: ActorRef, traces: Seq[TraceEvent]): Unit

  def onModuleInformation(sender: ActorRef, mi: DeviationModuleInfo): Unit = {
    useDeviation(sender, repository.traceRepository.event(mi.eventID).map(ed => repository.traceRepository.trace(ed.trace))
      .getOrElse(Seq.empty[TraceEvent]))
  }
}

class DeviationHandler(builderProps: Props) extends RequestHandler[DeviationHandler.DeviationModuleInfo] with DeviationHandlerBase {
  val builder = context.actorOf(builderProps, "deviationBuilder")

  def useDeviation(sender: ActorRef, traces: Seq[TraceEvent]): Unit = {
    builder ! DeviationResult(sender, traces)
  }
}
