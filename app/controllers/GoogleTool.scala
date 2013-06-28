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
		val labels = List("hoge", "FUGA", "はは　は", "ひひ ひひ");
		val sheet = man.getOrCreateSpreadsheet("SqlToolTest");
		val worksheet = man.getOrCreateWorksheet(sheet, "fugafuga", labels);
		man.addRow(worksheet, labels.map { s =>
				s.toLowerCase.replaceAll("[ 　]", "");
			}.zip(List("1", "2", "3", "4")));
		Ok(worksheet.getTitle.getPlainText);
	}
	
	def showSheet(bookName: String, sheetName: String) = filterAction { implicit request =>
		man.getSpreadsheet(bookName) match {
			case Some(book) =>
				man.getWorksheet(book, sheetName) match {
					case Some(sheet) =>
//						val url = book.getSpreadsheetLink.getHref + "#" + sheet.getId;
						val url = sheet.getEtag;
//println("link: " + sheet.getLink);
println("editlink: " + sheet.getEditLink.getHref);
println("editlink: " + sheet.getSelfLink.getHref);
//println("editlink: " + sheet.getMediaEditLink.getHref);
println("htmllink: " + sheet.getHtmlLink);
println("summary: " + sheet.getSummary);
println("source: " + sheet.getVersionId);
						Ok(url);
					case None =>
						Ok("Worksheet not found: " + bookName + "." + sheetName);
				}
			case None =>
				Ok("Spreadsheet not found: " + bookName);
		}
	}
}