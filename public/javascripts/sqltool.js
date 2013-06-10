if (typeof(flect) == "undefined") flect = {};
if (typeof(flect.app) == "undefined") flect.app = {};
if (typeof(flect.app.sqltool) == "undefined") flect.app.sqltool = {};

(function($) {
	function normalizeGroup(g) {
		if (!g) {
			return "";
		}
		while (g.indexOf("//") != -1) {
			g = g.replace(/\/\//, "/");
		}
		if (g.length > 0 && g.charAt(0) == "/") {
			g = g.substring(1);
		}
		if (g.length > 0 && g.charAt(g.length-1) == "/") {
			g = g.substring(0, g.length - 1);
		}
		return g;
	}
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
		function doSchemaNodeAction(node) {
			var title = node.data.title;
			$.ajax({
				"url" : "/sql/columns/" + title,
				"type" : "POST",
				"success" : function(data, textStatus){
					app.setTableInfo(title, data);
				},
				"error" : function(xhr, status, e) {
					error(xhr.responseText);
				}
			});
		}
		function isQueryNode(node) {
			return node.data.kind == "query";
		}
		function doQueryNodeAction(node) {
			$.ajax({
				"url" : "/sql/queryInfo",
				"type" : "POST",
				"data" : {
					"id" : node.data.id
				},
				"success" : function(data, textStatus){
					app.setQueryInfo(data);
				},
				"error" : function(xhr, status, e) {
					error(xhr.responseText);
				}
			});
		}
		function activate(node) {
			var parent = node.getParent();
			var title = node.data.title;
			console.log("Node = title=" + title + ", group=" + node.data.group + ", kind=" + node.data.kind + ", id=" + node.data.id);
			if (isSchemaNode(node)) {
				doSchemaNodeAction(node);
			} else if (isQueryNode(node)) {
				doQueryNodeAction(node);
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
				group = "";
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
							"group" : data[i].group,
							"kind" : data[i].kind
						};
						if (data[i].kind == "group") {
							obj.isLazy = true;
							obj.isFolder = true;
						} else {
							obj.id = data[i].id;
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
			group = normalizeGroup(group);
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
location.reload();
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
		
		$("#workspace").css("height", document.documentElement.clientHeight - 40);
		$("#sql-tab").tabs();
		$("#workspace").splitter({
			"orientation" : "vertical",
			"limit" : 100
		});
		$("#upper-pane").splitter({
			"limit" : 30,
			"keepLeft" : false
		});
		
		var currentId = "",
			btnExec = $("#btnExec").click(function() {
				var sql = $("#txtSQL").val();
				executeSql(sql);
			}),
			btnSave = $("#btnSave").click(function() {
				saveDialog.show("test", currentId);
			}),
			btnSaveAs = $("#btnSaveAs").click(function() {
				console.log("Not implemented yet");
			}),
			btnRename = $("#btnRename").click(function() {
				alert("Not implemented yet");
			}),
			btnDelete = $("#btnDelete").click(function() {
				if (currentId) {
					removeQueryInfo(currentId);
				}
			});
		function removeQueryInfo(id) {
			$.ajax({
				"url" : "/sql/delete",
				"type" : "POST",
				"data" : {
					"id" : id
				},
				"success" : function(data, textStatus){
					if (data == "OK") {
						close();
location.reload();
					} else {
						alert(data);
					}
				},
				"error" : function(xhr, status, e) {
					error(xhr.responseText);
				}
			});
		}
		function executeSql(sql) {
			var h = $("#lower-pane").height() - 120;
			sqlGrid.show().height(h).execute(sql);
		}
		function setQueryInfo(query) {
			currentId = query.id;
			$("#txtSQL").val(query.sql);
			enableButtons(true);
			executeSql(query.sql);
		}
		function setTableInfo(table, columns) {
			currentId = "";
			var sql = "SELECT A." + columns[0].name;
			for (var i=1; i<columns.length; i++) {
				sql += ",\n       A." + columns[i].name;
			}
			sql += "\n  FROM " + table + " A";
			$("#txtSQL").val(sql);
			enableButtons(false);
			executeSql(sql);
		}
		function enableButtons(b) {
			if (b) {
				btnRename.parent("li").removeClass("disabled");
				btnDelete.parent("li").removeClass("disabled");
			} else {
				btnRename.parent("li").addClass("disabled");
				btnDelete.parent("li").addClass("disabled");
			}
		}
		$.extend(this, {
			"executeSql" : executeSql,
			"setQueryInfo" : setQueryInfo,
			"setTableInfo" : setTableInfo
		});
	}
})(jQuery);
