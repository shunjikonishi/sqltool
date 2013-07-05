import play.api.Application;
import play.api.db.DB;
import play.api.mvc.WithFilters;
import play.api.Play.current;
import play.api.Logger;
import jp.co.flect.play2.filters.SessionIdFilter;
import jp.co.flect.play2.filters.AccessControlFilter;
import jp.co.flect.util.ResourceGen;
import jp.co.flect.rdb.RunScript;
import java.io.File;
import java.util.Locale;

import models.Schedule;

object Global extends WithFilters(SessionIdFilter, AccessControlFilter) {
	
	override def onStart(app: Application) {
		sys.env.get("LANG").foreach { s =>
			val locale = s.split("_").toList match {
				case lang :: country :: variant :: Nil => new Locale(lang, country, variant);
				case lang :: country :: Nil => new Locale(lang, country);
				case _ => new Locale(s);
			}
			Locale.setDefault(locale);
		}
		//Generate messages and messages.ja
		val defaults = new File("conf/messages");
		val origin = new File("conf/messages.origin");
		if (origin.lastModified > defaults.lastModified) {
			val gen = new ResourceGen(defaults.getParentFile(), "messages");
			gen.process(origin);
		}
		val mode = sys.props.get("sqltool.mode").getOrElse("web");
		Logger.info("Application start mode=" + mode);
		
		mode match {
			case "schedule" =>
				Schedule.main();
				System.exit(0);
			case "setup" =>
				val filename = sys.props.get("sqltool.script").getOrElse("conf/create.sql");
				val file = new File(filename);
				DB.withTransaction { con =>
					val script = new RunScript(con);
					script.setIgnoreDdlError(true);
					script.run(file);
				}
				System.exit(0);
			case _ =>
		}
	}
}
