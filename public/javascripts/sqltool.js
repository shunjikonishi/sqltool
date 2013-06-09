if (typeof(flect) == "undefined") flect = {};
if (typeof(flect.app) == "undefined") flect.app = {};
if (typeof(flect.app.sqltool) == "undefined") flect.app.sqltool = {};

(function($) {
	function MessagePane(app, el) {
		el = $(el);
		function message(text) {
			sqlGrid.hide();
			el.html(text).show();
		}
		$.extend(this, {
			"message" : message
		});
	}
   	function SqlTree(app, el) {
		var SCHEMAS = "Schemas",
			TABLES = "Tables",
			VIEWS = "Views",
			QUERIES = "Queries";
		
		function isSchemaNode(node) {
			if (node.getLevel() != 3) {
				return false;
			}
			var parent = node.getParent().getParent();
			return parent.data.title == SCHEMAS;
		}
		function activate(node) {
			var parent = node.getParent();
			var title = node.data.title;
			console.log("Node = " + title + ", " + node.group + ", " + node.data.group);
			if (isSchemaNode(node)) {
				app.executeSql("SELECT * FROM " + title);
			}
		}
		function readSchema(url, node) {
			$.ajax({
				"url" : url,
				"type" : "POST",
				"success" : function(data, textStatus){
					var children = [];
					for (var i=0; i<data.length; i++) {
						var obj = {
							"title" : data[i].name
						};
						children.push(obj);
					}
					node.addChild(children);
					node.setLazyNodeStatus(DTNodeStatus_Ok);
				},
				"error" : function(xhr, status, e) {
					error(xhr.responseText);
				}
			});
		}
		function readQueries(group, node) {
			if (!group) {
				group = "/";
			}
			$.ajax({
				"url" : "/sql/queryNode",
				"type" : "POST",
				"data" : {
					"group" : group
				},
				"success" : function(data, textStatus){
					var children = [];
					for (var i=0; i<data.length; i++) {
						var obj = {
							"title" : data[i].name,
							"group" : data[i].group
						};
						if (data[i].kind == "group") {
							obj.isLazy = true;
							obj.isFolder = true;
						}
						children.push(obj);
					}
					node.addChild(children);
					node.setLazyNodeStatus(DTNodeStatus_Ok);
				},
				"error" : function(xhr, status, e) {
					error(xhr.responseText);
				}
			});
		}
		var tree = $(el).dynatree({
			"onActivate" : activate,
			"onClick" : activate,
			"onLazyRead" : function(node) {
				var title = node.data.title;
				if (title == TABLES) {
					readSchema("/sql/tables", node);
				} else if (title == VIEWS) {
					readSchema("/sql/views", node);
				} else if (title == QUERIES || node.data.group) {
					readQueries(node.data.group, node);
				}
			},
			"children" : [
				{
					"title" : QUERIES,
					"isFolder" : true,
					"isLazy" : true
				},
				{
					"title" : SCHEMAS,
					"isFolder" : true,
					"children" : [
						{
							"title" : TABLES,
							"isFolder" : true,
							"isLazy" : true
						},
						{
							"title" : VIEWS,
							"isFolder" : true,
							"isLazy" : true
						}
					]
				}
			]
		});
	};
	function SaveDialog(app, el) {
		var dialog = $(el);
		function show(mode, id) {
			dialog.dialog({
				"title" : "Save SQL",
				"buttons" : [
					{
						"text" : "Save",
						"click" : function() { 
							doSave(id);
						}
					},
					{
						"text" : "Cancel",
						"click" : function() { 
							close();
						}
					}
				],
				"width" : "800px",
				"modal" : true
			});
		}
		function close() {
			dialog.dialog("close");
		}
		function doSave(id) {
			var name = $("#sql-name").val(),
				group = $("#sql-group").val(),
				desc = $("#sql-desc").val();
				sql = $("#txtSQL").val();
			if (!name) {
				alert("Name is required");
				return;
			}
			if (!group) {
				group = "/";
			} else if (!group.startsWith("/")) {
				group = "/" + group;
			}
			$.ajax({
				"url" : "/sql/save",
				"type" : "POST",
				"data" : {
					"id" : id,
					"name" : name,
					"group" : group,
					"desc" : desc,
					"sql" : sql
				},
				"success" : function(data, textStatus){
					if (data == "OK") {
						close();
					} else {
						alert(data);
					}
				},
				"error" : function(xhr, status, e) {
					error(xhr.responseText);
				}
			});
		}
		$.extend(this, {
			"show" : show
		});
	}
	function error(str) {
		msgPane.message(str);
	};
	var sqlGrid, sqlTree, msgPane;
	flect.app.sqltool.SqlTool = function(setting) {
		msgPane = new MessagePane(this, "#error-msg");
		sqlGrid = new flect.util.SqlGrid({
			"modelPath" : "/sql/model",
			"dataPath" : "/sql/data",
			"div" : "#grid-pane",
			"error" : error
		}),
		saveDialog = new SaveDialog(this, "#saveDialog"),
		sqlTree = new SqlTree(this, "#tree-pane");
		
		var currentId = "";
		
		$("#sql-tab").tabs();
		$("#btnExec").click(function() {
			var sql = $("#txtSQL").val();
			executeSql(sql);
		});
		$("#btnSave").click(function() {
			saveDialog.show("test", currentId);
		});
		$("#workspace").css("height", document.documentElement.clientHeight - 40);
		$("#workspace").splitter({
			"orientation" : "vertical",
			"limit" : 100
		});
		$("#upper-pane").splitter({
			"limit" : 30,
			"keepLeft" : false
		});
		function executeSql(sql) {
			var h = $("#lower-pane").height() - 120;
			sqlGrid.show().height(h).execute(sql);
		}
		$.extend(this, {
			"executeSql" : executeSql
		});
	}
})(jQuery);
