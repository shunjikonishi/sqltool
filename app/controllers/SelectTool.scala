package controllers

import play.api.mvc.Controller;
import play.api.mvc.Action;
import jp.co.flect.play2.utils.DatabaseUtility;
import jp.co.flect.play2.utils.Params;

import play.api.data.Form;
import play.api.data.Forms.mapping;
import play.api.data.Forms.text;
import play.api.data.Forms.default;
import play.api.data.Forms.optional;
import play.api.libs.json.JsArray;
import play.api.libs.json.JsObject;
import play.api.libs.json.JsString;
import play.api.libs.json.Json;

import models.QueryInfo;
import models.QueryParam;
import models.ParsedQuery;
import models.QueryManager;
import models.RdbQueryManager;
import models.SqlToolImplicits._;

import java.io.File;

object SelectTool extends jp.co.flect.play2.utils.SelectTool("target")
object SchemaTool extends jp.co.flect.play2.utils.SchemaTool("target")

object QueryTool extends Controller with DatabaseUtility {
	
	private val man: QueryManager = new RdbQueryManager(databaseName);
	
	private val queryForm = Form(mapping(
		"id" -> optional(text),
		"name" -> text,
		"group" -> default(text, ""),
		"sql" -> text,
		"desc" -> optional(text)
	)(QueryInfo.apply)(QueryInfo.unapply));
	
	def databaseName = "default";
	
	def prepareSql = Action { implicit request =>
		Ok("OK");
	}
	
	def save = Action { implicit request =>
		val data = queryForm.bindFromRequest;
		if (data.hasErrors) {
			BadRequest;
		} else {
			val info = data.get;
			println(info);
			val newInfo = man.save(info);
			Ok(JsObject(List(
				"id" -> JsString(newInfo.id),
				"status" -> JsString("OK")
			)).toString).as("application/json");
		}
	}
	
	def delete = Action { implicit request =>
		val id = Params(request).get("id");
		if (id.isEmpty) {
			BadRequest;
		} else {
			man.delete(id.get);
			Ok("OK");
		}
	}
	
	def queryNode = Action { implicit request =>
		val group = Params(request).get("group").getOrElse("");
		val gList = man.getGroupList(group).map(g => ("", g, "group", if (group == "") g else group + "/" + g));
		val qList = man.getQueryList(group).map( q => (q.id, q.name, "query", group));
		val ret = (gList ::: qList ::: Nil).map { case (i, n, k, g) =>
			JsObject(List(
				"id" -> JsString(i),
				"name" -> JsString(n),
				"kind" -> JsString(k),
				"group" -> JsString(g) 
			));
		}
		Ok(JsArray(ret).toString).as("application/json");
	}
	
	def queryInfo = Action { implicit request =>
		val id = Params(request).get("id").getOrElse("0");
		val ret = man.getQueryInfo(id);
		ret match {
			case Some(info) => Ok(Json.toJson(info));
			case None => NotFound("Not found");
		}
	}
	
	def queryParams = Action { implicit request =>
		import anorm.SqlStatementParser;
		
		val sql = Params(request).get("sql").getOrElse("");
		val parsedInfo = man.parse(sql);
		Ok(Json.toJson(parsedInfo));
	}
	
	def test = Action { implicit request => 
		Ok("OK");
	}
	
	def exportSql = Action { implicit request =>
		val file = File.createTempFile("temp", ".sql");
		try {
			man.exportTo(file);
			Ok.sendFile(file, fileName={ f=> "export.sql"}, onClose={ () => file.delete()});
		} catch {
			case e: Exception =>
				e.printStackTrace;
				Ok(e.toString);
		}
	}
	
	def importSql = Action { implicit request =>
		request.body.asMultipartFormData match {
			case Some(mdf) =>
				mdf.file("file") match {
					case Some(file) => 
						try  {
							val (insertCount, updateCount) = man.importFrom(file.ref.file);
							Redirect("/main").flashing(
								"Import-Insert" -> insertCount.toString,
								"Import-Update" -> updateCount.toString
							);
						} catch {
							case e: Exception => 
								e.printStackTrace;
								Ok(e.toString);
						}
					case None => BadRequest;
				}
			case None => BadRequest;
		}
	}
	
	def moveGroup = Action { implicit request =>
		val params = Params(request);
		val oldGroup = params.get("oldGroup");
		val newGroup = params.get("newGroup");
println("test: " + oldGroup + ", " + newGroup);
		if (oldGroup.isEmpty || newGroup.isEmpty) {
			BadRequest;
		} else {
			val targetGroup = man.moveGroup(oldGroup.get, newGroup.get);
			Ok("OK").flashing(
				"targetGroup" -> targetGroup
			);
		}
	}
}