package jp.co.flect.play2.utils;

import play.api.mvc.Request;
import play.api.mvc.AnyContent;

import jp.co.flect.play2.filters.SessionIdFilter;

object Params {
	
	def apply(request: Request[AnyContent]) = new Params(request);
	
}

class Params(request: Request[AnyContent]) {
	
	def sessionId = request.tags.get(SessionIdFilter.SESSIONID_NAME) match {
		case Some(x) => x;
		case _ => throw new IllegalStateException("SessionId not found");
	}
	
	def get(name: String) = {
		request.body.asFormUrlEncoded.flatMap {
			_.get(name).map(_.head)
		}
	}
	
	def getAll(name: String) = {
		request.body.asFormUrlEncoded.flatMap {
			_.get(name).map(_.head)
		}
	}
}

