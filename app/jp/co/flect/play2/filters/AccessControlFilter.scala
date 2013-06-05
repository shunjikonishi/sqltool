package jp.co.flect.play2.filters;

import jp.co.flect.net.IPFilter;
import org.apache.commons.codec.binary.Base64;

import play.api.Logger;
import play.api.mvc.Filter;
import play.api.mvc.Result;
import play.api.mvc.Results;
import play.api.mvc.RequestHeader;
import play.api.libs.concurrent.Execution.Implicits.defaultContext;

object AccessControlFilter extends Filter with Results {
	
	//IP restriction setting, if required
	private val IP_FILTER = sys.env.get("ALLOWED_IP")
		.map(IPFilter.getInstance(_));
	
	//Basic authentication setting, if required
	private val BASIC_AUTH = sys.env.get("BASIC_AUTHENTICATION")
		.filter(_.split(":").length == 2)
		.map { str =>
			val strs = str.split(":");
			(strs(0), strs(1));
		};
	
	//Apply IP restriction and Basic authentication
	//and Logging
	def apply(f:RequestHeader => Result)(request: RequestHeader):Result = {
		def ipFilter = {
			IP_FILTER match {
				case Some(filter) =>
					val ip = request.headers.get("x-forwarded-for").getOrElse(request.remoteAddress);
					val ret = filter.allow(ip)
					Logger.debug("IPFilter: uri=" + request.uri + ", accsess=" + ret + ", ip=" + ip);
					ret;
				case None =>
					true;
			}
		}
		def basicAuth = {
			BASIC_AUTH match {
				case Some((username, password)) =>
					val (authuser, ret) = request.headers.get("Authorization").map { auth =>
						auth.split(" ").drop(1).headOption.map { encoded =>
							new String(Base64.decodeBase64(encoded.getBytes)).split(":").toList match {
								case u :: p :: Nil => (u, u == username && password == p);
								case _ => ("Invalid Authrization header: " + auth, false);
							}
						}.getOrElse(("Invalid Authrization header: " + auth, false));
					}.getOrElse {
						("No Authorization", false);
					}
					Logger.debug("Basic authentication: uri=" + request.uri + ", user=" + authuser);
					ret;
				case None =>
					true;
			}
		}
		if (!ipFilter) {
			Forbidden;
		} else if (!basicAuth) {
		    Unauthorized.withHeaders("WWW-Authenticate" -> "Basic realm=\"Secured\"");
		} else {
			f(request);
		}
	}
}
