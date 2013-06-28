package models;

import java.net.URL;
import com.google.gdata.client.spreadsheet.SpreadsheetService;
import com.google.gdata.data.spreadsheet.SpreadsheetFeed;
import scala.collection.JavaConversions._;

object GoogleSpreadsheetManager {
	
	val APPLICATION_NAME = "flect.co.jp-SQLTool-1.0";
	private val USERNAME = sys.env.get("GOOGLE_USERNAME");
	private val PASSWORD = sys.env.get("GOOGLE_PASSWORD");
	
	def apply() = new GoogleSpreadsheetManager(USERNAME.get, PASSWORD.get);
	
	def enabled = USERNAME.nonEmpty && PASSWORD.nonEmpty;
}


class GoogleSpreadsheetManager(username: String, password: String) {
	
	import GoogleSpreadsheetManager._;
	
	private val service = {
		val ret = new SpreadsheetService(APPLICATION_NAME);
		ret.setUserCredentials(username, password);
		ret;
	}
	
	def test = {
		
		val SPREADSHEET_FEED_URL = new URL(
			"https://spreadsheets.google.com/feeds/spreadsheets/private/full");
		
		// Make a request to the API and get all spreadsheets.
		val feed = service.getFeed(SPREADSHEET_FEED_URL, classOf[SpreadsheetFeed]);
		val spreadsheets = feed.getEntries();
		
		spreadsheets.map(_.getTitle.getPlainText).mkString("\n");
	}
}
