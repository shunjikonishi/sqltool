package models;

import play.api.libs.json.JsValue;
import play.api.libs.json.JsObject;
import play.api.libs.json.JsString;
import play.api.libs.json.JsSuccess;
import play.api.libs.json.Format;

import jp.co.flect.rdb.SelectTokenizer;
import scala.collection.mutable.ListBuffer;

case class QueryInfo(optId: Option[String] = None, name: String, group: String, sql: String, description: Option[String]) {
	
	def id = optId.getOrElse("");
	def hasId = !optId.isEmpty;
	
	def toJson = {
		JsObject(List(
			"id" -> JsString(id),
			"name" -> JsString(name),
			"group" -> JsString(group),
			"sql" -> JsString(sql),
			"desc" -> JsString(description.getOrElse(""))
		));
	}
}

object QueryInfoFormat extends Format[QueryInfo] {
	
	def reads(json: JsValue) = JsSuccess(
		QueryInfo(
			(json \ "id").asOpt[String],
			(json \ "name").as[String],
			(json \ "group").as[String],
			(json \ "sql").as[String],
			(json \ "desc").asOpt[String]
		)
	);
	
	def writes(obj: QueryInfo) = JsObject(
		List(
			"id" -> JsString(obj.id),
			"name" -> JsString(obj.name),
			"group" -> JsString(obj.group),
			"sql" -> JsString(obj.sql),
			"desc" -> JsString(obj.description.getOrElse(""))
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

trait QueryManager {
	
	def getGroupList(parent: String): List[String];
	def getQueryList(parent: String): List[QueryInfo];
	
	def save(info:QueryInfo): QueryInfo;
	def getQueryInfo(id: String): Option[QueryInfo];
	
	def delete(id: String): Unit;
	
	def parse(sql: String): ParsedQuery = {
		val st = new SelectTokenizer(sql);
		val buf = new java.lang.StringBuilder();
		val ret = new java.lang.StringBuilder();
		val list = new ListBuffer[QueryParam]();
		
		var n = st.next(buf);
		while (n != SelectTokenizer.T_END) {
			val str = buf.toString;
			n match {
				case SelectTokenizer.T_LITERAL if (str.startsWith(":")) =>
					list += QueryParam(str.substring(1));
					ret.append("?");
				case SelectTokenizer.T_STRING =>
					ret.append("'").append(str).append("'");
				case SelectTokenizer.T_ERROR =>
					throw new IllegalArgumentException(sql);
				case _ =>
					ret.append(str);
			}
			ret.append(" ");
			n = st.next(buf);
		}
		ParsedQuery(ret.toString, list.toList);
	}
}

import anorm._;
import java.sql.Connection;
import jp.co.flect.play2.utils.DatabaseUtility;

class RdbQueryManager(val databaseName: String) extends QueryManager with DatabaseUtility {
	
	private val SELECT_STATEMENT = """
		SELECT id,
		       name,
		       groupname,
		       description,
		       sqltext
		  FROM sqltool_sql
	""";
	
	private def rowToInfo(row: Row) = {
		val id = row[Int]("id");
		val name = row[String]("name");
		val group = row[String]("groupname");
		val sqltext = row[String]("sqltext");
		val desc = row[Option[String]]("description");
		QueryInfo(Some(id.toString), name, group, sqltext, desc);
	}
	
	def save(info:QueryInfo): QueryInfo = withTransaction { implicit con =>
		val now = new java.sql.Timestamp(System.currentTimeMillis);
		if (info.hasId) {
			SQL("""
					UPDATE sqltool_sql
					   SET name = {name},
					       groupname = {groupname},
					       sqltext = {sqltext},
					       description = {description},
					       update_date = {update_date}
					 WHERE id = {id}
				"""
				).on(
					"name" -> info.name, 
					"groupname" -> info.group, 
					"sqltext" -> info.sql, 
					"description" -> info.description,
					"update_date" -> now, 
					"id" -> Integer.parseInt(info.id))
				.executeUpdate();
			info;
		} else {
			val id = SQL("""
					INSERT into sqltool_sql (
					       name,
					       groupname,
					       sqltext,
					       description,
					       insert_date,
					       update_date)
					VALUES({name},
					       {groupname},
					       {sqltext},
					       {description},
					       {insert_date},
					       {update_date})
				"""
				).on(
					"name" -> info.name, 
					"groupname" -> info.group, 
					"sqltext" -> info.sql, 
					"description" -> info.description,
					"insert_date" -> now,
					"update_date" -> now
				).executeInsert();
			info.copy(optId=Some(id.getOrElse(0).toString));
		}
	}
	
	def delete(id: String): Unit = withConnection { implicit con =>
		SQL("DELETE FROM sqltool_sql WHERE id = {id}")
			.on(
				"id" -> Integer.parseInt(id)
			).executeUpdate();
	}
	
	def getGroupList(parent: String): List[String] = withConnection { implicit con =>
		if (parent == "") {
			SQL("""
					SELECT distinct groupname FROM sqltool_sql WHERE groupname <> ''
				"""
				).apply.map{ row =>
					val g = row[String]("groupname");
					g.split("/")(0);
				}.toSet.toList;
		} else {
			SQL("""
					SELECT distinct groupname FROM sqltool_sql
					 WHERE groupname LIKE {gl}
				"""
				).on(
					"gl" -> (parent + "/%")
				).apply.map{ row =>
					val g = row[String]("groupname").substring(parent.length + 1);
					g.split("/")(0);
				}.toSet.toList;
		}
	}
	
	def getQueryList(parent: String): List[QueryInfo] = withConnection { implicit con =>
		SQL(SELECT_STATEMENT + "WHERE groupname = {groupname}").on(
				"groupname" -> parent
			).apply().map(rowToInfo(_)).toList;
	}
	
	def getQueryInfo(id: String): Option[QueryInfo] = withConnection { implicit con =>
		SQL(SELECT_STATEMENT + "WHERE id = {id}").on(
				"id" -> Integer.parseInt(id)
			).apply().map(rowToInfo(_)).headOption;
	}
}

