package jp.co.flect.play2.utils;

import play.api.mvc.Action;
import play.api.mvc.Controller;
import play.api.libs.json.JsArray;
import play.api.libs.json.JsObject;
import play.api.libs.json.JsString;
import play.api.libs.json.JsNumber;
import scala.collection.mutable.ListBuffer;

/**
 * Return results of select statement with jqGrid JSON format.
 */
class SchemaTool(val databaseName: String) extends Controller with DatabaseUtility {
	
	def tables = doGetTables(Array("TABLE"));
	def views = doGetTables(Array("VIEW"));
	
	private def doGetTables(types: Array[String]) = Action { implicit request =>
		withConnection { con =>
			val meta = con.getMetaData;
			val list = using(meta.getTables(null, null, "%", types)) { rs =>
				val buf = new ListBuffer[JsObject];
				while (rs.next) {
					val schema = rs.getString(2);
					val name = rs.getString(3);
					val kind = rs.getString(4);
					
					buf += JsObject(List(
						"schema" -> JsString(schema),
						"name" -> JsString(name),
						"kind" -> JsString(kind)
					));
				}
				buf
			} 
			Ok(JsArray(list).toString).as("application/json");
		}
	}
	
	def columns(name: String) = Action { implicit request =>
		withConnection { con =>
			val meta = con.getMetaData;
			val list = using(meta.getColumns(null, null, name, null)) { rs =>
				val buf = new ListBuffer[JsObject];
				while (rs.next) {
					val name = rs.getString(4);
					val dataType = rs.getInt(5);
					val typeName = rs.getString(6);
					
					buf += JsObject(List(
						"name" -> JsString(name),
						"dataType" -> JsNumber(dataType),
						"typeName" -> JsString(typeName)
					));
				}
				buf
			} 
			Ok(JsArray(list).toString).as("application/json");
		}
	}
}

