package controllers

import play.api.mvc.Controller;
import play.api.mvc.Action;
import play.api.mvc.AnyContent;
import play.api.mvc.Request;
import play.api.mvc.Result;

import play.api.data.Form;
import play.api.data.Forms.tuple;
import play.api.data.Forms.text;

import models.GoogleSpreadsheetManager;
import jp.co.flect.play2.utils.DatabaseUtility;

object GoogleTool extends Controller with DatabaseUtility {
	
	def databaseName = "target";
	
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
						val url = book.getSpreadsheetLink.getHref;
						Redirect(url);
					case None =>
						Ok("Worksheet not found: " + bookName + "." + sheetName);
				}
			case None =>
				Ok("Spreadsheet not found: " + bookName);
		}
	}
	
	private val executeForm = Form(tuple(
		"id" -> text,
		"sql" -> text
	));
	
	def execute = filterAction { implicit request =>
		val data = executeForm.bindFromRequest;
		if (data.hasErrors) {
			BadRequest;
		} else {
			val (id, sql) = data.get;
			QueryTool.man.getQueryInfo(id) match {
				case Some(info) =>
					doExecute(info.spreadsheet, info.worksheet, sql);
					Ok("OK");
				case None =>
					NotFound(id);
			}
		}
	}
	
	private def doExecute(bookName: String, sheetName: String, sql: String): Unit = withConnection { con =>
		using(con.prepareStatement(sql)) { stmt =>
			using(stmt.executeQuery) { rs =>
				man.addResultSet(bookName, sheetName, rs);
			}
		}
	}
}