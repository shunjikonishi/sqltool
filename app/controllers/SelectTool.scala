package controllers

import play.api.mvc.Controller;
import play.api.mvc.Action;
import jp.co.flect.play2.utils.DatabaseUtility;

object SelectTool extends jp.co.flect.play2.utils.SelectTool("target")
object SchemaTool extends jp.co.flect.play2.utils.SchemaTool("target")

object QueryTool extends Controller with DatabaseUtility {
	
	def databaseName = "target";
	
	def prepareSql = Action { implicit request =>
		Ok("OK");
	}
	
	def saveSql = Action { implicit request =>
		Ok("OK");
	}
}