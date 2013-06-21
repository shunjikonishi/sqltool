package models;

import play.api.libs.json.JsValue;
import play.api.libs.json.JsObject;
import play.api.libs.json.JsString;
import play.api.libs.json.JsSuccess;
import play.api.libs.json.Format;

import jp.co.flect.rdb.SelectTokenizer;
import scala.collection.mutable.ListBuffer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileInputStream;


case class QueryInfo(optId: Option[String] = None, name: String, group: String, sql: String, description: Option[String]) {
	
	def id = optId.getOrElse("");
	def hasId = !optId.isEmpty;
	
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
	def getQueryInfo(group: String, name: String): Option[QueryInfo];
	
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
	
	def exportTo(file: File): Unit;
	def importFrom(file: File): (Int, Int);
	
	def moveGroup(oldGroup: String, newGroup: String): Unit;
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
	
	def moveGroup(oldGroup: String, newGroup: String): Unit = withTransaction { implicit con =>
		val idx = oldGroup.lastIndexOf("/");
		if (newGroup == "" && idx > 0) {
			SQL("""
				UPDATE sqltool_sql SET groupname = substring(groupname, {len})
				 WHERE groupname like {oldGroup}
				"""
				).on(
					"len" -> (idx + 2),
					"oldGroup" -> (oldGroup + "%")
				).executeUpdate();
		} else if (idx == -1) {
			SQL("""
				UPDATE sqltool_sql SET groupname = {newGroup} || '/' || groupname
				 WHERE groupname like {oldGroup}
				"""
				).on(
					"newGroup" -> newGroup,
					"oldGroup" -> (oldGroup + "%")
				).executeUpdate();
		} else {
			SQL("""
				UPDATE sqltool_sql SET groupname = {newGroup} || substring(groupname, {oldLen})
				 WHERE groupname like {oldGroup}
				"""
				).on(
					"newGroup" -> newGroup,
					"oldLen" -> (idx + 1),
					"oldGroup" -> (oldGroup + "%")
				).executeUpdate();
		}
	}
	
	def delete(id: String): Unit = withTransaction { implicit con =>
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
	
	def getQueryInfo(group: String, name: String): Option[QueryInfo] = withConnection { implicit con =>
		SQL(SELECT_STATEMENT + "WHERE groupname = {group} AND name = {name}").on(
				"group" -> group,
				"name" -> name
			).apply().map(rowToInfo(_)).headOption;
	}
	
	def exportTo(file: File): Unit = withConnection { implicit con =>
		val writer = new OutputStreamWriter(new FileOutputStream(file), "utf-8");
		try {
			SQL(SELECT_STATEMENT + "ORDER BY groupname, name")
				.apply().map(rowToInfo(_)).foreach { info =>
					writer.write("-- ");
					if (info.group.length() > 0) {
						writer.write(info.group);
						writer.write("/");
					}
					writer.write(info.name);
					writer.write("\n");
					
					info.description.map { s =>
						writer.write("/*\n");
						writer.write(s);
						writer.write("\n*/\n\n");
					}
					
					writer.write(info.sql);
					writer.write("\n\n");
				}
		} finally {
			writer.close;
		}
	}
	
	def importFrom(file: File): (Int, Int) = {
		def trimEx(str: String) = {
			str.reverse.dropWhile(c =>
				c == ' ' || c == '\r' || c == '\n' || c == '\t'
			).reverse.dropWhile(c =>
				c == ' ' || c == '\r' || c == '\n' || c == '\t'
			);
		}
		var insertCount, updateCount = 0;
		def doImport(nameWithGroup: String, desc: String, sql: String) = {
			val idx = nameWithGroup.lastIndexOf("/");
			val (group, name) = idx match {
				case -1 => ("", nameWithGroup);
				case _ => (nameWithGroup.substring(0, idx), nameWithGroup.substring(idx+1));
			};
			val info = QueryInfo(
				name=name,
				group=group,
				sql=trimEx(sql),
				description=Option(trimEx(desc))
			);
			getQueryInfo(group, name) match {
				case Some(oldInfo) =>
					save(oldInfo.copy(sql=info.sql, description=info.description));
					updateCount += 1;
				case None =>
					save(info);
					insertCount += 1;
			}
		}
		val reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "utf-8"));
		try {
			val nameBuf = new StringBuilder();
			val descBuf = new StringBuilder();
			val sqlBuf = new StringBuilder();
			
			var str = reader.readLine;
			while (str != null) {
				if (str.startsWith("--")) {
					if (nameBuf.length > 0) {
						doImport(nameBuf.toString, descBuf.toString, sqlBuf.toString);
						nameBuf.clear;
						descBuf.clear;
						sqlBuf.clear;
					}
					nameBuf.append(str.substring(2).trim);
				} else if (str.startsWith("/*")) {
					str = reader.readLine;
					while (str != null && !str.startsWith("*/")) {
						descBuf.append(str).append("\n");
						str = reader.readLine;
					}
				} else {
					sqlBuf.append(str).append("\n");
				}
				str = reader.readLine;
			}
			if (nameBuf.length > 0 && sqlBuf.length > 0) {
				doImport(nameBuf.toString, descBuf.toString, sqlBuf.toString);
			}
		} finally {
			reader.close;
		}
		(insertCount, updateCount);
	}
}

