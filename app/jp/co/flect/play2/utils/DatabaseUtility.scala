package jp.co.flect.play2.utils;

import java.sql.Connection;
import play.api.db.DB;
import play.api.Play.current;

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
}
