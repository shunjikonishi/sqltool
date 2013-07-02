package models;

import java.sql.ResultSet;
import java.net.URL;
import java.net.URI;
import com.google.gdata.client.spreadsheet.SpreadsheetService;
import com.google.gdata.client.spreadsheet.FeedURLFactory;
import com.google.gdata.client.spreadsheet.SpreadsheetQuery;
import com.google.gdata.client.spreadsheet.CellQuery;
import com.google.gdata.data.spreadsheet.SpreadsheetFeed;
import com.google.gdata.data.spreadsheet.SpreadsheetEntry;
import com.google.gdata.data.spreadsheet.WorksheetEntry;
import com.google.gdata.data.spreadsheet.CellFeed;
import com.google.gdata.data.spreadsheet.ListFeed;
import com.google.gdata.data.spreadsheet.ListEntry;
import com.google.gdata.data.TextConstruct;
import com.google.gdata.data.docs.DocumentEntry;
import com.google.gdata.data.Category;
import com.google.gdata.client.docs.DocsService;

import scala.collection.JavaConversions._;

object GoogleSpreadsheetManager {
	
	val APPLICATION_NAME = "flect.co.jp-SQLTool-1.0";
	val SPREADSHEET_CATEGORY = "http://schemas.google.com/docs/2007#spreadsheet";
	
	val databaseName = "target";
	
	private val USERNAME = sys.env.get("GOOGLE_USERNAME");
	private val PASSWORD = sys.env.get("GOOGLE_PASSWORD");
	
	def apply() = new GoogleSpreadsheetManager(USERNAME.get, PASSWORD.get);
	
	def enabled = USERNAME.nonEmpty && PASSWORD.nonEmpty;
	
}


class GoogleSpreadsheetManager(username: String, password: String) {
	
	import GoogleSpreadsheetManager._;
	
	private val service = {
		val ss = new SpreadsheetService(APPLICATION_NAME);
		ss.setUserCredentials(username, password);
		ss;
	}
	
	def getSpreadsheet(title: String): Option[SpreadsheetEntry] = {
		val urlFactory = FeedURLFactory.getDefault;
		val spreadsheetQuery = new SpreadsheetQuery(urlFactory.getSpreadsheetsFeedUrl());
		spreadsheetQuery.setTitleQuery(title);
		val spreadsheetFeed = service.query(spreadsheetQuery, classOf[SpreadsheetFeed]);
		spreadsheetFeed.getEntries.filter { entry =>
			val name = entry.getTitle.getPlainText;
			name == title;
		}.headOption;
	}
	
	def getOrCreateSpreadsheet(title: String): SpreadsheetEntry = {
		getSpreadsheet(title) match {
			case Some(x) => x;
			case None =>
				val DOCS_FEED_URL = new URL(
					"https://docs.google.com/feeds/default/private/full");
				
				val ds = new DocsService(APPLICATION_NAME);
				ds.setUserCredentials(username, password);
				
				val urlFactory = FeedURLFactory.getDefault;
				val entry = new SpreadsheetEntry();
				entry.setTitle(TextConstruct.plainText(title));
				entry.getCategories.add(new Category(SPREADSHEET_CATEGORY));
				
				ds.insert(DOCS_FEED_URL, entry);
		}
	}
	
	def getWorksheet(sheet: SpreadsheetEntry, title: String): Option[WorksheetEntry] = {
		sheet.getWorksheets.filter(_.getTitle.getPlainText == title).headOption;
	}
	
	def getOrCreateWorksheet(sheet: SpreadsheetEntry, title: String, labels: Seq[String]): WorksheetEntry = {
		getWorksheet(sheet, title) match {
			case Some(x) => x;
			case None =>
				val worksheet = new WorksheetEntry();
				worksheet.setTitle(TextConstruct.plainText(title));
				worksheet.setColCount(labels.size);
				worksheet.setRowCount(100);
				
				val worksheetFeedUrl = sheet.getWorksheetFeedUrl();
				val result = service.insert(worksheetFeedUrl, worksheet);
				val cellFeedUrl = result.getCellFeedUrl();
				
				val cellQuery = new CellQuery(result.getCellFeedUrl());
				cellQuery.setRange("R1C1:R1C" + labels.size);
				cellQuery.setReturnEmpty(true);
				val cellFeed = service.query(cellQuery, classOf[CellFeed]);
				
				labels.zip(cellFeed.getEntries).foreach { case (value, cell) =>
					cell.changeInputValueLocal(value);
					cell.update;
				}
				result;
		}
	}
	
	def addRow(worksheet: WorksheetEntry, values: Seq[(String, String)]): Unit = {
		val listFeedUrl = worksheet.getListFeedUrl();
		val listFeed = service.getFeed(listFeedUrl, classOf[ListFeed]);
		
		val row = new ListEntry();
		values.foreach { case(label, value) =>
			row.getCustomElements().setValueLocal(label, value);
		}
		service.insert(listFeedUrl, row);
	}
	
	def addResultSet(bookName: String, sheetName: String, rs: ResultSet): Unit = {
		val meta = rs.getMetaData;
		val labels = for (idx <- 1 to meta.getColumnCount) yield {
			meta.getColumnLabel(idx);
		}
		val book = getOrCreateSpreadsheet(bookName);
		val sheet = getOrCreateWorksheet(book, sheetName, labels);
		val normalizedLabels = labels.map(_.toLowerCase.replaceAll("[ 　]", ""));
		while (rs.next) {
			val values = for (idx <- 1 to meta.getColumnCount) yield {
				rs.getString(idx);
			}
			addRow(sheet, normalizedLabels.zip(values));
		}
	}
	
}
