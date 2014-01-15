/**
 * Copyright (C) 2013 Typesafe <http://typesafe.com/>
 */
package console
package handler

import akka.actor.{ ActorRef, Props }
import activator.analytics.data.{ TimeRange, Scope, ActorStats }

object PlayRequestHandler {
  case class PlayRequestModuleInfo(scope: Scope,
    modifiers: ScopeModifiers,
    time: TimeRange,
    dataFrom: Option[Long],
    traceId: Option[String]) extends ScopedModuleInformationBase
}

trait PlayRequestHandlerBase extends RequestHandler[PlayRequestHandler.PlayRequestModuleInfo] {
  import PlayRequestHandler._

  def onModuleInformation(sender: ActorRef, mi: PlayRequestModuleInfo): Unit = {
  }
}

class PlayRequestHandler(builderProps: Props) extends PlayRequestHandlerBase {
  val builder = context.actorOf(builderProps, "playRequestBuilder")

  def useActorStats(sender: ActorRef, stats: ActorStats): Unit = {

  }
}
