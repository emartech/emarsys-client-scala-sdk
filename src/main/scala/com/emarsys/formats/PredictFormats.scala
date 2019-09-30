package com.emarsys.formats

import com.emarsys.client.predict.PredictApi._
import spray.json._

object PredictFormats extends DefaultJsonProtocol {
  implicit val recommendationResponseF: RootJsonFormat[RecommendationResponse] = jsonFormat1(
    RecommendationResponse.apply
  )

  implicit val recommendationF: JsonFormat[Recommendation] = jsonFormat2(Recommendation.apply)
}
