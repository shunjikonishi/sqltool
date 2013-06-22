package controllers

import play.api.mvc.Controller;
import play.api.mvc.Action;

import jp.co.flect.play2.utils.Params;

object Application extends Controller {
	
	def index = Action { implicit request =>
		Ok(views.html.index());
	}
	
	def main = Action { implicit request =>
		val importInsert = flash.get("Import-Insert").getOrElse("0").toInt;
		val importUpdate = flash.get("Import-Update").getOrElse("0").toInt;
		val targetGroup = flash.get("targetGroup").getOrElse("");
		Ok(views.html.main(importInsert, importUpdate, targetGroup));
	}
}