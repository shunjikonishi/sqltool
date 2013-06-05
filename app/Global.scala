import play.api.mvc.WithFilters;
import jp.co.flect.play2.filters.SessionIdFilter;
import jp.co.flect.play2.filters.AccessControlFilter;

object Global extends WithFilters(SessionIdFilter, AccessControlFilter)