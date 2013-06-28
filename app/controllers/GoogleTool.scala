package controllers

import play.api.mvc.Controller;
import play.api.mvc.Action;
import play.api.mvc.AnyContent;
import play.api.mvc.Request;
import play.api.mvc.Result;

import models.GoogleSpreadsheetManager;

object GoogleTool extends Controller {
	
	private lazy val man = GoogleSpreadsheetManager();
	
	def filterAction(f: Request[AnyContent] => Result): Action[AnyContent] = Action { request =>
		if (GoogleSpreadsheetManager.enabled) {
			f(request);
		} else {
			InternalServerError("Google account is not setuped.");
		}
	}
	
	def test = filterAction { implicit request =>
		Ok(man.test);
	}
}