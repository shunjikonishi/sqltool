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

import models.QueryInfo;
import models.QueryManager;
import models.RdbQueryManager;

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
	
	def databaseName = "target";
	
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
			man.save(info);
			Ok("OK");
		}
	}
	
	def queryNode = Action { implicit request =>
		val group = Params(request).get("group").getOrElse("");
println("Group = [" + group + "]");
		val gList = man.getGroupList(group).map(g => (g.substring(1), "group", g));
		val qList = man.getQueryList(group).map( q => (q.name, "query", group));
		val ret = (gList ::: qList ::: Nil).map { case (n, k, g) =>
			JsObject(List(
				"name" -> JsString(n),
				"kind" -> JsString(k),
				"group" -> JsString(g) 
			));
		}
println("Ret = " + ret);
		Ok(JsArray(ret).toString).as("application/json");
	}
}