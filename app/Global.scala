import play.api.Application;
import play.api.mvc.WithFilters;
import jp.co.flect.play2.filters.SessionIdFilter;
import jp.co.flect.play2.filters.AccessControlFilter;
import jp.co.flect.util.ResourceGen;
import java.io.File;

import models.Schedule;

object Global extends WithFilters(SessionIdFilter, AccessControlFilter) {
	
	override def onStart(app: Application) {
		//Generate messages and messages.ja
		val defaults = new File("conf/messages");
		val origin = new File("conf/messages.origin");
		if (origin.lastModified > defaults.lastModified) {
			val gen = new ResourceGen(defaults.getParentFile(), "messages");
			gen.process(origin);
		}
		sys.props.get("sqltool.mode").getOrElse("web") match {
			case "schedule" =>
				println("Run schedule");
				Schedule.main(Array());
				System.exit(0);
			case _ =>
		}
	}
}
