package jp.co.flect.play2.utils;

import java.sql.SQLException;
import play.api.db.DB;
import play.api.Play.current;
import play.api.mvc.Controller;
import play.api.mvc.Action;
import play.api.mvc.Request;
import play.api.mvc.AnyContent;
import play.api.cache.Cache;
import play.api.Logger;

import jp.co.flect.javascript.jqgrid.ColModel;
import jp.co.flect.javascript.jqgrid.RdbColModelFactory;
import jp.co.flect.javascript.jqgrid.RdbQueryModel;
import jp.co.flect.csv.CSVUtils;
import jp.co.flect.excel2canvas.ExcelUtils;

import java.io.File;
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
	
	def download = Action { implicit request =>
		val params = Params(request);
		val sql = params.get("sql");
		val downloadType = params.get("type").getOrElse("CSV");
		val sqlParams = getSQLParams(request);
		if (sql.isEmpty) {
			BadRequest;
		} else {
			val ext = if (downloadType == "Excel") ".xlsx" else ".csv";
			val file = File.createTempFile("temp", ext);
			withConnection { con =>
				using(con.prepareStatement(sql.get)) { stmt =>
					sqlParams.zipWithIndex.foreach { case (x, i) =>
						stmt.setObject(i+1, x);
					}
					using(stmt.executeQuery) { rs =>
						if (downloadType == "Excel") {
							ExcelUtils.resultSetToExcel(rs, file);
						} else {
							CSVUtils.resultSetToCsv(rs, file);
						}
					}
				}
			}
			
			Ok.sendFile(file, fileName={ f=> "download" + ext}, onClose={ () => file.delete()});
		}
	}
	
	private def getSQLandModel(implicit request: Request[AnyContent]) = {
		Params(request).get("sql") match {
			case Some(sql) =>
				try {
					val model = Cache.getOrElse[ColModel](sql, CACHE_DURATION) {
						withConnection { con =>
							val factory = new RdbColModelFactory(con);
							factory.getQueryModel(sql);
						}
					}
					(sql, model);
				} catch {
					case e: Exception =>
						Logger.error("getSQLandModel: " + sql);
						throw e;
				}
			case None => throw new SQLException("SQL not specified");
		}
	}
	
}

