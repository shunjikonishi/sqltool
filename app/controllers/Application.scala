package controllers

import play.api.mvc.Controller;
import play.api.mvc.Action;

import jp.co.flect.play2.utils.Params;

object Application extends Controller {
	
	def index = Action { implicit request =>
		Ok(views.html.index());
	}
	
	def main = Action { implicit request =>
		Ok(views.html.main());
	}
}