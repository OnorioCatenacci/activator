/**
 * Copyright (C) 2013 Typesafe <http://typesafe.com/>
 */
package console.handler.rest

import akka.actor.ActorRef
import console.ClientController.Update
import com.typesafe.trace.TraceEvent

class DeviationJsonBuilder extends JsonBuilderActor {
  import DeviationJsonBuilder._

  def receive = {
    case r: DeviationResult => r.receiver ! Update(null)
  }
  /*
def handle(receiver: ActorRef, mi: ModuleInformation): (ActorRef, JsValue) = {
  val deviationPromise = call(RequestHandler.traceTreeURL + mi.traceId.get, Map.empty[String, String])
  for {
    deviation <- deviationPromise
  } yield {
    val result = validateResponse(deviation) match {
      case ValidResponse =>
        val data = JsObject(Seq("deviation" -> deviation.json))
        JsObject(Seq(
          "type" -> JsString("deviation"),
          "data" -> data))
      case InvalidLicense(jsonLicense) => jsonLicense
      case ErrorResponse(jsonErrorCodes) => jsonErrorCodes
    }

    (receiver, result)
  }
    (receiver, JsObject(Seq("deviation" -> JsString("todo"))))
}
  */
}

object DeviationJsonBuilder {
  case class DeviationResult(receiver: ActorRef, traces:Seq[TraceEvent])
}
