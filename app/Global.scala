import play.api.Application;
import play.api.mvc.WithFilters;
import jp.co.flect.play2.filters.SessionIdFilter;
import jp.co.flect.play2.filters.AccessControlFilter;
import jp.co.flect.util.ResourceGen;
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
		sys.props.get("sqltool.mode").getOrElse("web") match {
			case "schedule" =>
				Schedule.main();
				System.exit(0);
			case _ =>
		}
	}
}
