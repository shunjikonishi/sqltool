package controllers

import play.api.mvc.Controller;
import play.api.mvc.Action;
import play.api.mvc.AnyContent;
import play.api.mvc.Request;
import play.api.mvc.Result;
import play.api.i18n.Messages;

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
		val list = List(
			"od6",
			"od7",
			"od4",
			"od5",
			"oda",
			"odb",
			"od8",
			"od9",
			"ocy",
			"ocz",
			"ocw",
			"ocx",
			"od2",
			"od3",
			"od0",
			"od1",
			"ocq",
			"ocr",
			"oco",
			"ocp",
			"ocu",
			"ocv",
			"ocs",
			"oct",
			"oci",
			"ocj",
			"ocg",
			"och",
			"ocm",
			"ocn",
			"ock",
			"ocl",
			"oe2",
			"oe3",
			"oe0",
			"oe1",
			"oe6",
			"oe7",
			"oe4",
			"oe5",
			"odu",
			"odv",
			"ods",
			"odt",
			"ody",
			"odz",
			"odw",
			"odx",
			"odm",
			"odn",
			"odk",
			"odl",
			"odq",
			"odr",
			"odo",
			"odp",
			"ode",
			"odf",
			"odc",
			"odd",
			"odi",
			"odj",
			"odg",
			"odh",
			"obe",
			"obf",
			"obc",
			"obd",
			"obi",
			"obj",
			"obg",
			"obh",
			"ob6",
			"ob7",
			"ob4",
			"ob5",
			"oba",
			"obb",
			"ob8",
			"ob9",
			"oay",
			"oaz",
			"oaw",
			"oax",
			"ob2",
			"ob3",
			"ob0",
			"ob1",
			"oaq",
			"oar",
			"oao",
			"oap",
			"oau",
			"oav",
			"oas",
			"oat",
			"oca",
			"ocb",
			"oc8",
			"oc9"
		);
		Ok(list.map { id =>
			val ret = convertGid("hoge/" + id);
			id + ": " + ret;
		}.mkString("\n"));
	}
	
	def showSheet(bookName: String, sheetName: String) = filterAction { implicit request =>
		man.getSpreadsheet(bookName) match {
			case Some(book) =>
				man.getWorksheet(book, sheetName) match {
					case Some(sheet) =>
						val gid = convertGid(sheet.getId);
						val url = book.getSpreadsheetLink.getHref + "&rm=minimal#gid=" + gid;
						Redirect(url);
					case None =>
						Ok("Worksheet not found: " + bookName + "." + sheetName);
				}
			case None =>
				Ok("Spreadsheet not found: " + bookName);
		}
	}
	
	private def convertGid(id: String) = {
		val suffix = id.substring(id.lastIndexOf('/') + 1);
		Integer.parseInt(suffix, 36) ^ 31578;
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
			try {
				QueryTool.man.getQueryInfo(id) match {
					case Some(info) =>
						doExecute(info.spreadsheet, info.worksheet, sql);
						Ok("OK");
					case None =>
						NotFound(id);
				}
			} catch {
				case e: Exception =>
					e.printStackTrace;
					Ok(e.toString);
			}
		}
	}
	
	def doExecute(bookName: String, sheetName: String, sql: String): Unit = withConnection { con =>
		using(con.prepareStatement(sql)) { stmt =>
			using(stmt.executeQuery) { rs =>
				man.addResultSet(bookName, sheetName, Messages("executeTime"), rs);
			}
		}
	}
	
}
