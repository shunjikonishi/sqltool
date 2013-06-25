package jp.co.flect.play2.utils;

import scala.collection.mutable.ListBuffer;

import java.sql.SQLException;
import java.sql.Types;
import java.sql.ResultSet;

import play.api.db.DB;
import play.api.Play.current;
import play.api.mvc.Controller;
import play.api.mvc.Action;
import play.api.mvc.Request;
import play.api.mvc.AnyContent;

import play.api.libs.json.Json;
import play.api.libs.json.JsArray;

import java.text.SimpleDateFormat;
import jp.co.flect.sql.DBTool;

/**
 * Return results of select statement with graph format.
 */
class GraphTool(val databaseName: String) extends Controller with DatabaseUtility {
	
	private def isNumberType(n: Int) =
		n == Types.BIGINT ||
		n == Types.DECIMAL ||
		n == Types.DOUBLE ||
		n == Types.FLOAT ||
		n == Types.INTEGER ||
		n == Types.NUMERIC ||
		n == Types.REAL ||
		n == Types.SMALLINT ||
		n == Types.TINYINT;
		
	case class GraphData(label: String, numbers: List[BigDecimal])
	
	case class GraphInfo(numberCount: Int, data: List[GraphData])
	
	implicit val graphDataFormat = Json.format[GraphData];
	implicit val graphInfoFormat = Json.format[GraphInfo];
	
	class GraphInfoCreator extends DBTool.Creator[GraphInfo] {
		def create(rs: ResultSet): GraphInfo = {
			val meta = rs.getMetaData;
			val count = meta.getColumnCount;
			for (idx <- 2 to count) {
				val n = meta.getColumnType(idx);
				if (!isNumberType(n)) {
					throw new IllegalArgumentException("Invalid datatype: " + meta.getColumnLabel(idx) + ": " + meta.getColumnTypeName(idx));
				}
			}
			val buf = new ListBuffer[GraphData];
			while (rs.next) {
				val label = rs.getString(1);
				val numbers = for (idx <- 2 to count) yield {
					new BigDecimal(rs.getBigDecimal(idx));
				}
				buf += GraphData(label, numbers.toList);
			}
			GraphInfo(count - 1, buf.toList);
		}
	}
	
	def data = Action { implicit request =>
		try {
			val sql = Params(request).get("sql");
			if (sql.isEmpty) {
				BadRequest;
			} else {
				val sqlParams = getSQLParams(request);
				withConnection { con =>
					val db = new DBTool(con);
					val ret = db.create(sql.get, new GraphInfoCreator(), sqlParams.toArray);
					Ok(Json.toJson(ret));
				}
			}
		} catch {
			case e: SQLException => 
				e.printStackTrace;
				BadRequest(e.getMessage);
		}
	}
	
	private def getSQLParams(implicit request: Request[AnyContent]) = {
		Params(request).get("sql-param") match {
			case Some(json) =>
				Json.parse(json) match {
					case arr: JsArray =>
						arr.value.map { v =>
							val datatype = (v \ "type").as[String];
							val value = (v \ "value").as[String];
							datatype match {
								case "boolean" => value.toBoolean;
								case "int" => Integer.parseInt(value);
								case "date" => new java.sql.Date(new SimpleDateFormat("yyyy-MM-dd").parse(value).getTime);
								case "datetime" => new java.sql.Timestamp(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(value).getTime);
								case "string" => value;
								case _ => throw new IllegalStateException(datatype + ", " + value);
							}
						};
					case _ => Nil;
				}
			case None => Nil;
		}
	}
	
}

