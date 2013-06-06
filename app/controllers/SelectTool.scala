package controllers

import play.api.mvc.Action;
import play.api.libs.json.JsArray;
import play.api.libs.json.JsObject;
import play.api.libs.json.JsString;
import scala.collection.mutable.ListBuffer;

object SelectTool extends jp.co.flect.play2.utils.SelectTool("target") {
	
	def tables = doGetTables(Array("TABLE"));
	def views = doGetTables(Array("VIEW"));
	
	private def doGetTables(types: Array[String]) = Action { implicit request =>
		withConnection { con =>
			val meta = con.getMetaData;
			val list = using(meta.getTables(null, null, "%", types)) { rs =>
				val buf = new ListBuffer[String];
				while (rs.next) {
					println("Table: " +
						rs.getString(1) + ", " +
						rs.getString(2) + ", " +
						rs.getString(3) + ", " +
						rs.getString(4) + ", " +
						"");
					buf += rs.getString(3);
				}
				buf.toList.map { v =>
					JsObject(List(("title", JsString(v))));
				};
			} 
			Ok(JsArray(list).toString).as("application/json");
		}
	}
	
}
