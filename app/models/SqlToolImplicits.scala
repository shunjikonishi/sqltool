package models;

import play.api.libs.json.Json;

object SqlToolImplicits {
	
	implicit val queryInfoFormat = QueryInfoFormat;
	implicit val queryParamFormat = QueryParamFormat;
	implicit val parsedQueryFormat = Json.format[ParsedQuery];
}
