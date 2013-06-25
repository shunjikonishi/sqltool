package models;

import play.api.libs.json.JsValue;
import play.api.libs.json.JsObject;
import play.api.libs.json.JsString;
import play.api.libs.json.JsNumber;
import play.api.libs.json.JsSuccess;
import play.api.libs.json.Format;

object QueryKind {
	
	case object GROUP extends QueryKind(-1, "Group");
	case object QUERY extends QueryKind(1, "Query");
	case object PIE_GRAPH extends QueryKind(11, "PieGraph");
	case object BAR_GRAPH extends QueryKind(12, "BarGraph");
	case object LINE_GRAPH extends QueryKind(13, "LineGraph");
	
	val values = Array(
//		GROUP,
		QUERY,
		PIE_GRAPH,
		BAR_GRAPH,
		LINE_GRAPH
	);
	
	def fromCode(code: Int) = values.filter(_.code == code).head;
	
	def split(label: String): (QueryKind, String) = {
		val kind = values.filter(kind => label.startsWith(kind.prefix)).headOption;
		if (kind.isEmpty) {
			throw new IllegalArgumentException(label);
		}
		(kind.get, label.substring(kind.get.prefix.length));
	}
}

sealed abstract class QueryKind(val code: Int, val name: String) {
	
	def prefix = "/" + name + "/";
	
}

case class QueryInfo(
	optId: Option[String] = None, 
	kind: QueryKind, 
	name: String, 
	group: String, 
	sql: String, 
	description: Option[String], 
	setting: Option[String]) {
	
	def id = optId.getOrElse("");
	def hasId = !optId.isEmpty;
	
}

object QueryInfoFormat extends Format[QueryInfo] {
	
	def reads(json: JsValue) = JsSuccess(
		QueryInfo(
			(json \ "id").asOpt[String],
			QueryKind.fromCode((json \ "kind").as[Int]),
			(json \ "name").as[String],
			(json \ "group").as[String],
			(json \ "sql").as[String],
			(json \ "desc").asOpt[String],
			(json \ "setting").asOpt[String]
		)
	);
	
	def writes(obj: QueryInfo) = JsObject(
		List(
			"id" -> JsString(obj.id),
			"kind" -> JsNumber(obj.kind.code),
			"name" -> JsString(obj.name),
			"group" -> JsString(obj.group),
			"sql" -> JsString(obj.sql),
			"desc" -> JsString(obj.description.getOrElse("")),
			"setting" -> JsString(obj.setting.getOrElse(""))
		)
	);
}

case class QueryParam(definedName: String) {
	
	val (name, dataType) = {
		definedName.split(":").toList match {
			case n :: t :: Nil => (n, t);
			case n :: Nil => (n, "string");
			case _ => (definedName, "invalid");
		}
	}
}

object QueryParamFormat extends Format[QueryParam] {
	
	def reads(json: JsValue) = JsSuccess(
		QueryParam(
			(json \ "name").as[String] + ":" + 
			(json \ "type").as[String]
		)
	);
	
	def writes(obj: QueryParam) = JsObject(
		List(
			"name" -> JsString(obj.name),
			"type" -> JsString(obj.dataType)
		)
	);
}

case class ParsedQuery(sql: String, params: List[QueryParam])

