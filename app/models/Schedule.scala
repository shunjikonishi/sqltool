package models;

import play.api.Logger;
import play.api.i18n.Messages;

import jp.co.flect.play2.utils.DatabaseUtility;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

object Schedule extends DatabaseUtility {
	
	val logger = Logger("Schedule");
	
	def databaseName = "target";
	
	case class Time(str: String, num: Long)
	
	val gm = GoogleSpreadsheetManager();
	val qm = new RdbQueryManager("default");
	
	def main(args: Array[String] = Array()): Unit = {
		
		val decimal = new DecimalFormat("00");
		val sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		
		val now = new Date();
		val nowStr = sdf.format(now).takeWhile(_ != ' ');
		
		val times = for (hour <- 0 to 23) yield {
			val str = decimal.format(hour) + ":00:00";
			val num = sdf.parse(nowStr + " " + str).getTime;
			Time(str, num)
		}
		times.filter { time =>
			math.abs(now.getTime - time.num) < 10 * 60 * 1000;
		}.foreach { time =>
			val queries = qm.getScheduledQueryList(time.str);
			logger.info("Run schedule: " + time.str + ", count=" + queries.size);
			queries.foreach(execute(_));
		}
	}
	
	private def execute(info: QueryInfo): Unit = withConnection { con =>
		val timeLabel = Messages("executeTime");
		using(con.prepareStatement(info.sql)) { stmt =>
			using (stmt.executeQuery) { rs =>
				gm.addResultSet(info.spreadsheet, info.worksheet, timeLabel, rs);
			}
		}
	}
	
}

