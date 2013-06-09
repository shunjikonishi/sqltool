package jp.co.flect.play2.filters;

import play.api.Logger;
import play.api.mvc.Filter;
import play.api.mvc.Request;
import play.api.mvc.Result;
import play.api.mvc.PlainResult;
import play.api.mvc.AsyncResult;
import play.api.mvc.Session;
import play.api.mvc.Cookies;
import play.api.mvc.RequestHeader;
import play.api.http.HeaderNames;
import play.api.libs.concurrent.Execution.Implicits.defaultContext;

import java.util.UUID;

object SessionIdFilter extends Filter {
	
	val SESSIONID_NAME = "X-SESSIONID";
	
	def apply(f: RequestHeader => Result)(rh: RequestHeader):Result = {
		def generateSessionId = UUID.randomUUID.toString;
		
		val id = rh.session.get(SESSIONID_NAME).getOrElse(generateSessionId);
		val request = rh.copy(tags=rh.tags + (SESSIONID_NAME -> id));
		Logger.debug("SessionIdFilter: uri=" + request.uri + ", id=" + id + ", " + rh.session.get(SESSIONID_NAME));
		
		def addSessionId(result: PlainResult): Result = {
			val session = Session.decodeFromCookie(
				Cookies(result.header.headers.get(HeaderNames.COOKIE))
					.get(Session.COOKIE_NAME)
			);
			session.get(SESSIONID_NAME) match {
				case Some(x) => result;
				case None =>
					result.withSession(session + (SESSIONID_NAME -> id));
			}
		}
		f(request) match { 
			case plain: PlainResult => addSessionId(plain);
			case async: AsyncResult => async.transform(addSessionId);
		}
	}
	
}
