package models;

case class QueryInfo(id: Int, name: String, group: String, sql: String)

trait QueryManager {
	
	def getGroupList(parent: String): List[String];
	def getQueryList(parent: String): List[QueryInfo];
	
	def save(info:QueryInfo): QueryInfo;
	def getQueryInfo(id: Int): Option[QueryInfo];
}

class RdbQueryManager extends QueryManager {
	
	def getGroupList(parent: String): List[String] = Nil;
	def getQueryList(parent: String): List[QueryInfo] = Nil;
	
	def save(info:QueryInfo): QueryInfo = {
		info;
	}
	
	def getQueryInfo(id: Int): Option[QueryInfo] = None;
}

