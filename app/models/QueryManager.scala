package models;

import play.api.libs.json.JsObject;
import play.api.libs.json.JsString;

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

trait QueryManager {
	
	def getGroupList(parent: String): List[String];
	def getQueryList(parent: String): List[QueryInfo];
	
	def save(info:QueryInfo): QueryInfo;
	def getQueryInfo(id: String): Option[QueryInfo];
	
	def delete(id: String): Unit;
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

