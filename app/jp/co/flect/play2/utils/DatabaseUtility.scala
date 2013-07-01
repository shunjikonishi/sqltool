package jp.co.flect.play2.utils;

import java.sql.Connection;
import play.api.db.DB;
import play.api.Play.current;

import play.api.libs.json.Json;
import play.api.libs.json.JsArray;
import java.text.SimpleDateFormat;

import play.api.mvc.Request;
import play.api.mvc.AnyContent;

trait DatabaseUtility {
	
	def databaseName: String;
	
	def withConnection[A](block: Connection => A): A = DB.withConnection(databaseName)(block);
	def withTransaction[A](block: Connection => A): A = DB.withTransaction(databaseName)(block);
	
	def using[A <: AutoCloseable, B](resource: A)(block: A => B): B = {
		try {
			block(resource);
		} finally {
			resource.close;
		}
	}
	
	def getSQLParams(implicit request: Request[AnyContent]) = {
		Params(request).get("sql-param") match {
			case Some(json) =>
				Json.parse(json) match {
					case arr: JsArray =>
						arr.value.map { v =>
							val datatype = (v \ "type").as[String];
							val value = (v \ "value").as[String];
							datatype match {
								case "boolean" => value.toBoolean;
								case "int" => Integer.parseInt(value);
								case "date" => new java.sql.Date(new SimpleDateFormat("yyyy-MM-dd").parse(value).getTime);
								case "datetime" => new java.sql.Timestamp(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(value).getTime);
								case "string" => value;
								case _ => throw new IllegalStateException(datatype + ", " + value);
							}
						}.toList;
					case _ => Nil;
				}
			case None => Nil;
		}
	}
}
