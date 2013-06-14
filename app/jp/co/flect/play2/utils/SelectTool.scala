package jp.co.flect.play2.utils;

import java.sql.SQLException;
import play.api.db.DB;
import play.api.Play.current;
import play.api.mvc.Controller;
import play.api.mvc.Action;
import play.api.mvc.Request;
import play.api.mvc.AnyContent;
import play.api.cache.Cache;

import play.api.libs.json.Json;
import play.api.libs.json.JsArray;

import jp.co.flect.javascript.jqgrid.ColModel;
import jp.co.flect.javascript.jqgrid.RdbColModelFactory;
import jp.co.flect.javascript.jqgrid.RdbQueryModel;

import java.text.SimpleDateFormat;
import java.sql.Connection;

/**
 * Return results of select statement with jqGrid JSON format.
 */
class SelectTool(val databaseName: String) extends Controller with DatabaseUtility {
	
	private val CACHE_DURATION = 60 * 60;
	
	def colModel = Action { implicit request =>
		try {
			val (sql, model) = getSQLandModel;
			Ok(model.toJson).as("application/json");
		} catch {
			case e: SQLException => 
				e.printStackTrace;
				BadRequest(e.getMessage);
		}
	}
	
	def data = Action { implicit request =>
		try {
			val (sql, model) = getSQLandModel;
			val gridParam = JqGrid.JqGridForm.bindFromRequest.get;
			val sqlParams = getSQLParams(request);
			
			withConnection { con =>
				val queryModel = new RdbQueryModel(con, sql, model);
				queryModel.setUseOffset(true);
				if (gridParam.sortColumn.length > 0) {
					queryModel.setOrder(gridParam.sortColumn, gridParam.sortAsc);
				}
				val data = queryModel.getGridData(gridParam.page, gridParam.rows, 
					scala.collection.JavaConversions.seqAsJavaList(sqlParams));
				Ok(data.toJson).as("application/json");
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
						}.toList;
					case _ => Nil;
				}
			case None => Nil;
		}
	}
	
	private def getSQLandModel(implicit request: Request[AnyContent]) = {
		Params(request).get("sql") match {
			case Some(sql) =>
				val model = Cache.getOrElse[ColModel](sql, CACHE_DURATION) {
					withConnection { con =>
						val factory = new RdbColModelFactory(con);
println("getSQLandModel: " + sql);
						factory.getQueryModel(sql);
					}
				}
				(sql, model);
			case None => throw new SQLException("SQL not specified");
		}
	}
	
}

