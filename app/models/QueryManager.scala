package models;

import jp.co.flect.rdb.SelectTokenizer;
import scala.collection.mutable.ListBuffer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileInputStream;

trait QueryManager {
	
	def getGroupList(parent: String): List[String];
	def getQueryList(parent: String): List[QueryInfo];
	
	def save(info:QueryInfo): QueryInfo;
	def getQueryInfo(id: String): Option[QueryInfo];
	def getQueryInfo(kind: QueryKind, group: String, name: String): Option[QueryInfo];
	
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
	
	def moveGroup(oldGroup: String, newGroup: String): String;
	
	def updateGraphSetting(id: String, setting: String): Boolean;
}

import anorm._;
import java.sql.Connection;
import jp.co.flect.play2.utils.DatabaseUtility;

class RdbQueryManager(val databaseName: String) extends QueryManager with DatabaseUtility {
	
	private val SELECT_STATEMENT = """
		SELECT id,
		       kind,
		       name,
		       groupname,
		       description,
		       sqltext,
		       setting
		  FROM sqltool_sql
	""";
	
	private def rowToInfo(row: Row) = {
		val id = row[Int]("id");
		val kind = row[Int]("kind");
		val name = row[String]("name");
		val group = row[String]("groupname");
		val sqltext = row[String]("sqltext");
		val desc = row[Option[String]]("description");
		val setting = row[Option[String]]("setting");
		QueryInfo(Some(id.toString), QueryKind.fromCode(kind), name, group, sqltext, desc, setting);
	}
	
	def save(info:QueryInfo): QueryInfo = withTransaction { implicit con =>
		val now = new java.sql.Timestamp(System.currentTimeMillis);
		if (info.hasId) {
			SQL("""
					UPDATE sqltool_sql
					   SET kind = {kind},
					       name = {name},
					       groupname = {groupname},
					       sqltext = {sqltext},
					       description = {description},
					       setting = {setting},
					       update_date = {update_date}
					 WHERE id = {id}
				"""
				).on(
					"name" -> info.name, 
					"kind" -> info.kind.code,
					"groupname" -> info.group, 
					"sqltext" -> info.sql, 
					"description" -> info.description,
					"setting" -> info.setting,
					"update_date" -> now, 
					"id" -> Integer.parseInt(info.id))
				.executeUpdate();
			info;
		} else {
			val id = SQL("""
					INSERT into sqltool_sql (
					       kind,
					       name,
					       groupname,
					       sqltext,
					       description,
					       setting,
					       insert_date,
					       update_date)
					VALUES({kind},
					       {name},
					       {groupname},
					       {sqltext},
					       {description},
					       {setting},
					       {insert_date},
					       {update_date})
				"""
				).on(
					"kind" -> info.kind.code,
					"name" -> info.name, 
					"groupname" -> info.group, 
					"sqltext" -> info.sql, 
					"description" -> info.description,
					"setting" -> info.setting,
					"insert_date" -> now,
					"update_date" -> now
				).executeInsert();
			info.copy(optId=Some(id.getOrElse(0).toString));
		}
	}
	
	def moveGroup(oldGroup: String, newGroup: String): String = withTransaction { implicit con =>
		val now = new java.sql.Timestamp(System.currentTimeMillis);
		val idx = oldGroup.lastIndexOf("/");
		if (newGroup == "" && idx > 0) {
			SQL("""
				UPDATE sqltool_sql 
				   SET groupname = substring(groupname, {len}),
				       update_date = {update_date}
				 WHERE groupname like {oldGroup}
				"""
				).on(
					"len" -> (idx + 2),
					"oldGroup" -> (oldGroup + "%"),
					"update_date" -> now
				).executeUpdate();
			oldGroup.substring(idx+1);
		} else if (idx == -1) {
			SQL("""
				UPDATE sqltool_sql 
				   SET groupname = {newGroup} || '/' || groupname,
				       update_date = {update_date}
				 WHERE groupname like {oldGroup}
				"""
				).on(
					"newGroup" -> newGroup,
					"oldGroup" -> (oldGroup + "%"),
					"update_date" -> now
				).executeUpdate();
			newGroup + "/" + oldGroup;
		} else {
			SQL("""
				UPDATE sqltool_sql 
				   SET groupname = {newGroup} || substring(groupname, {oldLen}),
				       update_date = {update_date}
				 WHERE groupname like {oldGroup}
				"""
				).on(
					"newGroup" -> newGroup,
					"oldLen" -> (idx + 1),
					"oldGroup" -> (oldGroup + "%"),
					"update_date" -> now
				).executeUpdate();
			newGroup + oldGroup.substring(idx);
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
					SELECT distinct groupname 
					  FROM sqltool_sql
					 WHERE groupname <> ''
					 ORDER BY groupname
				"""
				).apply.map{ row =>
					val g = row[String]("groupname");
					g.split("/")(0);
				}.toSet.toList;
		} else {
			SQL("""
					SELECT distinct groupname FROM sqltool_sql
					 WHERE groupname LIKE {gl}
					 ORDER BY groupname
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
		SQL(SELECT_STATEMENT + "WHERE groupname = {groupname} ORDER BY name").on(
				"groupname" -> parent
			).apply().map(rowToInfo(_)).toList;
	}
	
	def getQueryInfo(id: String): Option[QueryInfo] = withConnection { implicit con =>
		SQL(SELECT_STATEMENT + "WHERE id = {id}").on(
				"id" -> Integer.parseInt(id)
			).apply().map(rowToInfo(_)).headOption;
	}
	
	def getQueryInfo(kind: QueryKind, group: String, name: String): Option[QueryInfo] = withConnection { implicit con =>
		SQL(SELECT_STATEMENT + "WHERE kind = {kind} AND groupname = {group} AND name = {name}").on(
				"kind" -> kind.code,
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
					writer.write(info.kind.prefix);
					if (info.group.length() > 0) {
						writer.write(info.group);
						writer.write("/");
					}
					writer.write(info.name);
					writer.write("\n");
					
					info.setting.map { s =>
						if (s.length > 0) {
							writer.write("-- ");
							writer.write(s);
							writer.write("\n");
						}
					}
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
			val ret = str.reverse.dropWhile(c =>
				c == ' ' || c == '\r' || c == '\n' || c == '\t'
			).reverse.dropWhile(c =>
				c == ' ' || c == '\r' || c == '\n' || c == '\t'
			);
			if (ret.isEmpty) null else ret;
		}
		var insertCount, updateCount = 0;
		def doImport(label: String, desc: String, sql: String, setting: String) = {
			val (kind, nameWithGroup) = if (label.startsWith("/")) {
				QueryKind.split(label);
			} else {
				(QueryKind.QUERY, label);
			}
			val idx = nameWithGroup.lastIndexOf("/");
			val (group, name) = idx match {
				case -1 => ("", nameWithGroup);
				case _ => (nameWithGroup.substring(0, idx), nameWithGroup.substring(idx+1));
			};
			val info = QueryInfo(
				kind=kind,
				name=name,
				group=group,
				sql=trimEx(sql),
				description=Option(trimEx(desc)),
				setting=Option(trimEx(setting))
			);
			getQueryInfo(kind, group, name) match {
				case Some(oldInfo) =>
					save(oldInfo.copy(sql=info.sql, description=info.description, setting=info.setting));
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
			val settingBuf = new StringBuilder();
			
			var str = reader.readLine;
			while (str != null) {
				if (str.startsWith("--")) {
					val nameOrSetting = str.substring(2).trim;
					if (nameBuf.length > 0 && settingBuf.length == 0 && nameOrSetting.startsWith("{")) {
						settingBuf.append(nameOrSetting);
					} else {
						if (nameBuf.length > 0) {
							doImport(nameBuf.toString, descBuf.toString, sqlBuf.toString, settingBuf.toString);
							nameBuf.clear;
							descBuf.clear;
							sqlBuf.clear;
							settingBuf.clear;
						}
						nameBuf.append(nameOrSetting);
					}
				} else if (str.startsWith("/*")) {
					str = reader.readLine;
					while (str != null && !str.startsWith("*/")) {
						descBuf.append(str).append("\n");
						str = reader.readLine;
					}
				} else if (nameBuf.length > 0) {
					sqlBuf.append(str).append("\n");
				}
				str = reader.readLine;
			}
			if (nameBuf.length > 0 && sqlBuf.length > 0) {
				doImport(nameBuf.toString, descBuf.toString, sqlBuf.toString, settingBuf.toString);
			}
		} finally {
			reader.close;
		}
		(insertCount, updateCount);
	}
	
	def updateGraphSetting(id: String, setting: String): Boolean = withTransaction { implicit con =>
		val now = new java.sql.Timestamp(System.currentTimeMillis);
		val ret = SQL("""
				UPDATE sqltool_sql
				   SET setting = {setting},
				       update_date = {update_date}
				 WHERE id = {id}
			"""
			).on(
				"setting" -> setting, 
				"update_date" -> now, 
				"id" -> Integer.parseInt(id)
			).executeUpdate();
		ret == 1;
	}
	
}

