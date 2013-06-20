if (typeof(flect) == "undefined") flect = {};
if (typeof(flect.app) == "undefined") flect.app = {};
if (typeof(flect.app.sqltool) == "undefined") flect.app.sqltool = {};

(function($) {
	var EXECUTE_NONE = 1,
		EXECUTE_ALWAYS = 2,
		EXECUTE_NO_PARAMS = 3;
	
	$.ajaxSetup({
		"type" : "POST",
		"error" : function(xhr, status, e) {
			error(xhr.responseText);
		}
	});
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
	function QueryInfo(data) {
		var self = this;
		
		this.id = data.id;
		this.name = data.name;
		this.group = data.group;
		this.sql = data.sql;
		this.desc = data.desc;
		
		this.parsedSql = null;
		
		function getHash() { 
			return {
				"id" : this.id,
				"name" : this.name,
				"group" : this.group,
				"sql" : this.sql,
				"desc" : this.desc
			};
		}
		function isParsed() { return this.parsedSql != null;}
		
		$.extend(this, {
			"getHash" : getHash,
			"isParsed" : isParsed
		});
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
			QUERIES = "Queries",
			expandTarget = null;
		
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
				"success" : function(data, textStatus){
					app.setTableInfo(title, data);
				}
			});
		}
		function isQueryNode(node) {
			return node.data.kind == "query";
		}
		function doQueryNodeAction(node) {
			$.ajax({
				"url" : "/sql/queryInfo",
				"data" : {
					"id" : node.data.key
				},
				"success" : function(data, textStatus){
					app.setQueryInfo(new QueryInfo(data));
				}
			});
		}
		function activate(node) {
			var parent = node.getParent();
			var title = node.data.title;
			if (isSchemaNode(node)) {
				doSchemaNodeAction(node);
			} else if (isQueryNode(node)) {
				doQueryNodeAction(node);
			}
		}
		function readSchema(url, node) {
			$.ajax({
				"url" : url,
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
				}
			});
		}
		function readQueries(group, node) {
			if (!group) {
				group = "";
			}
			$.ajax({
				"url" : "/sql/queryNode",
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
							obj.key = data[i].id;
						}
						children.push(obj);
					}
					node.addChild(children);
					node.setLazyNodeStatus(DTNodeStatus_Ok);
					if (expandTarget) {
						addNode(expandTarget);
					}
				}
			});
		}
		function getChildNode(node, kind, title) {
			var children = node.getChildren();
			if (!children) {
				return null;
			}
			for (var i=0; i<children.length; i++) {
				var child = children[i];
				if (child.data.kind == kind && child.data.title == title) {
					return child;
				}
			}
			return null;
		}
		function getFirstQueryNode(node) {
			var children = node.getChildren();
			if (!children) {
				return null;
			}
			for (var i=0; i<children.length; i++) {
				var child = children[i];
				if (child.data.kind == "query") {
					return child;
				}
			}
			return null;
		}
		function isLoaded(node) {
			return node.getChildren() !== undefined;
		}
		function addNode(queryInfo) {
			var parent = tree.getNodeByKey(QUERIES);
			if (!isLoaded(parent)) {
				expandTarget = queryInfo;
				parent.expand(true);
				return;
			}
			if (queryInfo.group) {
				var names = queryInfo.group.split("/");
				for (var i=0; i<names.length; i++) {
					parent.expand();
					var child = getChildNode(parent, "group", names[i]);
					if (child == null) {
						child = parent.addChild({
							"title" : names[i],
							"group" : parent.data.group,
							"kind" : "group",
							"isFolder" : true
						}, getFirstQueryNode(parent));
					} else if (!isLoaded(child)) {
						expandTarget = queryInfo;
						child.expand(true);
						return;
					}
					parent = child;
				}
			}
			parent.expand(true);
			var newNode = getChildNode(parent, "query", queryInfo.name);
			if (newNode == null) {
				newNode = parent.addChild({
					"title" : queryInfo.name,
					"group" : parent.data.group,
					"key" : queryInfo.id,
					"kind" : "query"
				});
			}
			newNode.activate();
			expandTarget = null;
		}
		function removeNode(queryInfo) {
			var node = tree.getNodeByKey(queryInfo.id);
			if (node) {
				node.remove();
				return true;
			}
			return false;
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
					"key" : QUERIES,
					"isFolder" : true,
					"isLazy" : true
				},
				{
					"title" : SCHEMAS,
					"key" : SCHEMAS,
					"isFolder" : true,
					"children" : [
						{
							"title" : TABLES,
							"key" : TABLES,
							"isFolder" : true,
							"isLazy" : true
						},
						{
							"title" : VIEWS,
							"key" : VIEWS,
							"isFolder" : true,
							"isLazy" : true
						}
					]
				}
			]
		}).dynatree("getTree");
		$.extend(this, {
			"tree" : tree,
			"addNode" : addNode,
			"removeNode" : removeNode
		});
	};
	function SaveDialog(app, el) {
		var dialog = $(el);
		function show(mode, queryInfo) {
			var id = "", 
				name = "", 
				group = "", 
				desc = "";
			if (queryInfo) {
				id = queryInfo.id;
				name = queryInfo.name;
				group = queryInfo.group;
				desc = queryInfo.desc;
			}
			$("#sql-name").val(name);
			$("#sql-group").val(group);
			$("#sql-desc").val(desc);
			
			dialog.dialog({
				"title" : "Save SQL",
				"buttons" : [
					{
						"text" : "Save",
						"click" : function() { 
							doSave(mode, id);
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
		function doSave(mode, id) {
			var name = $("#sql-name").val(),
				group = $("#sql-group").val(),
				desc = $("#sql-desc").val();
				sql = $("#txtSQL").val();
			if (!name) {
				alert("Name is required");
				return;
			}
			group = normalizeGroup(group);
			save(mode, new QueryInfo({
				"id" : id,
				"name" : name,
				"group" : group,
				"desc" : desc,
				"sql" : sql
			}));
		}
		function save(mode, queryInfo) {
			$.ajax({
				"url" : "/sql/save",
				"data" : queryInfo.getHash(),
				"success" : function(data, textStatus){
					if (data.status == "OK") {
						close();
						queryInfo.id = data.id;
						switch (mode) {
							case SaveMode.NEW:
							case SaveMode.EDIT:
								app.updateTree(queryInfo);
								break;
							case SaveMode.UPDATE:
								break;
							default:
								alert("UnknownMode: " + mode);
								break;
						}
					} else {
						alert(data);
					}
				}
			});
		}
		$.extend(this, {
			"show" : show,
			"save" : save
		});
	}
	function SqlTabs(app, el) {
		var self = this;
		el = $(el).tabs({
			"beforeActivate" : function(event, ui) {
				if (!sqlForm.isBuilded() && ui.newPanel.attr("id") == "form-pane") {
					var sql = $("#txtSQL").val();
					app.checkSqlParams(sql, EXECUTE_NONE);
				}
			}
		});
		
		function activateSql() {
			el.tabs("option", "active", 0);
		}
		function activateForm() {
			el.tabs("option", "active", 1);
		}
		$.extend(this, {
			"activateSql" : activateSql,
			"activateForm" : activateForm
		});
	}
	function SqlForm(app, form, desc) {
		form = $(form);
		desc = $(desc);
		
		var self = this,
			builded = false;
		function setDescription(str) {
			desc.empty();
			if (str) {
				var array = str.split("\n");
				for (var i=0; i<array.length; i++) {
					if (i != 0) {
						desc.append("<br>");
					}
					desc.append(array[i]);
				}
			}
		}
		function makeForm(params) {
			function getOldValue(name) {
				for (var i=0; i<oldParams.params.length; i++) {
					var p = oldParams.params[i];
					if (p.name == name) {
						return p.value;
					}
				}
			}
			function getDefaultDate(bTime) {
				function pad(n) {
					return n < 10 ? "0" + n : "" + n;
				}
				var d = new Date(),
					ret = d.getFullYear() + "-" +
						pad(d.getMonth() + 1) + "-" +
						pad(d.getDate());
				if (bTime) {
					ret += "T00:00:00";
				}
				return ret;
			}
			var oldParams = getParams();
			form.empty();
			if (params && params.length) {
				var ul = $("<ul></ul>");
				for (var i=0; i<params.length; i++) {
					var name = params[i].name,
						type = params[i].type;
					var li = $("<li></li>"),
						input = null;
					li.append("<label>" + name + "</label>");
					
					switch (type) {
						case "boolean":
							input = $("<input type='checkbox'></input>");
							break;
						case "int":
							input = $("<input type='number'></input>");
							break;
						case "date":
							input = $("<input type='date'></input>");
							input.attr("value", getDefaultDate(false));
							break;
						case "datetime":
							input = $("<input type='datetime-local' step='1'></input>");
							input.attr("value", getDefaultDate(true));
							break;
						case "string":
							input = $("<input type='text'></input>");
							break;
						default:
							throw new Exception("Invalid datatype: " + name + ": " + type);
					}
					input.attr({
						"name" : name,
						"data-type" : type
					});
					var value = getOldValue(name);
					if (value) {
						input.val(value);
					}
					
					li.append(input);
					ul.append(li);
				}
				form.append(ul);
			}
			builded = true;
		}
		function getParams() {
			var ret = [],
				empties = [],
				inputs = form.find(":input");
			inputs.each(function() {
				var el = $(this),
					obj = {
						"name" : el.attr("name"),
						"type" : el.attr("data-type"),
						"value" : el.val()
					}
				if (obj.value) {
					if (obj.type == "datetime" && obj.value.length == 16) {
						obj.value = obj.value += ":00";
					}
					ret.push(obj);
				} else {
					empties.push(obj.name);
				}
			});
			if (empties.length > 0) {
				return {
					"error" : true,
					"empty" : empties,
					"params" : ret
				};
			} else {
				return {
					"params" : ret
				}
			}
		}
		$.extend(this, {
			"setDescription" : setDescription,
			"makeForm" : makeForm,
			"getParams" : getParams,
			"isBuilded" : function() {
				return builded;
			},
			"setBuilded" : function(b) {
				builded = b;
				return self;
			}
		});
	}
	function error(str) {
		msgPane.message(str);
	};
	var sqlGrid, sqlTree, msgPane, sqlTabs, sqlForm;
	var SaveMode = {
		"NEW" : "new",
		"UPDATE" : "update",
		"EDIT" : "edit"
	};
	
	flect.app.sqltool.SqlTool = function(settings) {
		msgPane = new MessagePane(this, "#error-msg");
		sqlGrid = new flect.util.SqlGrid({
			"modelPath" : "/sql/model",
			"dataPath" : "/sql/data",
			"div" : "#grid-pane",
			"error" : error,
			"effect" : "highlight"
		}),
		saveDialog = new SaveDialog(this, "#saveDialog"),
		sqlTree = new SqlTree(this, "#tree-pane");
		sqlTabs = new SqlTabs(this, "#sql-tab");
		sqlForm = new SqlForm(this, "#formForm", "#formDesc");
		
		$("#workspace").css("height", document.documentElement.clientHeight - 40);
		$("#workspace").splitter({
			"orientation" : "vertical",
			"limit" : 100
		});
		$("#upper-pane").splitter({
			"limit" : 30,
			"keepLeft" : true
		});
		
		var currentQuery = null,
			btnExec = $("#btnExec").click(function() {
				if (sqlForm.isBuilded() && (currentQuery == null || (currentQuery && currentQuery.isParsed()))) {
					executeSql(currentQuery.parsedSql);
				} else {
					var sql = checkSql();
					if (sql) {
						checkSqlParams(sql, EXECUTE_ALWAYS);
					}
				}
			}),
			btnSave = $("#btnSave").click(function() {
				var sql = checkSql();
				if (sql) {
					if (currentQuery) {
						currentQuery.sql = $("#txtSQL").val();
						saveDialog.save(SaveMode.UPDATE, currentQuery);
					} else {
						saveDialog.show(SaveMode.NEW, null);
					}
				}
			}),
			btnSaveAs = $("#btnSaveAs").click(function() {
				var sql = checkSql();
				if (sql) {
					saveDialog.show(SaveMode.NEW, null);
				}
			}),
			btnEdit = $("#btnEdit").click(function() {
				var sql = checkSql();
				if (sql && currentQuery) {
					currentQuery.sql = sql;
					saveDialog.show(SaveMode.EDIT, currentQuery);
				}
			}),
			btnTest = $("#btnTest").click(function() {
				//alert("builded: " + sqlForm.isBuilded());
				alert(JSON.stringify(sqlForm.getParams()));
			}),
			btnImport = $("#btnImport").click(function() {
				importSql();
			}),
			btnExport = $("#btnExport").click(function() {
				exportSql();
			}),
			btnDelete = $("#btnDelete").click(function() {
				if (currentQuery && confirm("Delete query.\nAre you sure?")) {
					removeQueryInfo(currentQuery);
				}
			}),
			txtSql = $("#txtSQL").change(function() {
				sqlForm.setBuilded(false);
				if (currentQuery) {
					currentQuery.parsedSql = null;
				}
			});
		function checkSql() {
			var sql = $("#txtSQL").val();
			if (!sql) {
				alert("SQL is required");
				return null;
			}
			return sql;
		}
		function removeQueryInfo(queryInfo) {
			$.ajax({
				"url" : "/sql/delete",
				"data" : {
					"id" : queryInfo.id
				},
				"success" : function(data, textStatus){
					if (data == "OK") {
						sqlTree.removeNode(queryInfo);
						currentQuery = null;
						enableButtons(false);
					} else {
						alert(data);
					}
				}
			});
		}
		function executeSql(sql) {
			var params = sqlForm.getParams();
			if (params.error) {
				sqlTabs.activateForm();
				var msg = "";
				for (var i=0; i<params.empty.length; i++) {
					if (msg != "") {
						msg += "\n";
					}
					msg += params.empty[i] + " is required.";
				}
				alert(msg);
				return;
			}
			var h = $("#lower-pane").height() - 120;
			sqlGrid.show().height(h).execute(sql, params.params);
		}
		function checkSqlParams(sql, execMode) {
			$.ajax({
				"url" : "/sql/queryParams",
				"data" : {
					"sql" : sql
				},
				"success" : function(data) {
					sqlForm.makeForm(data.params);
					if (data.params.length == 0) {
						sqlTabs.activateSql();
					} else {
						sqlTabs.activateForm();
					}
					if (currentQuery) {
						currentQuery.parsedSql = data.sql;
					}
					switch (execMode) {
						case EXECUTE_NONE:
							break;
						case EXECUTE_ALWAYS:
						case EXECUTE_NO_PARAMS:
							var params = sqlForm.getParams();
							if (execMode == EXECUTE_ALWAYS || params.params.length == 0) {
								if (sqlForm.getParams().error) {
									sqlGrid.hide();
								} else {
									executeSql(data.sql);
								}
							} else {
								sqlGrid.hide();
							}
							break;
						default:
							alert("IllegalExecuteMode: " + execMode);
							break;
					}
				}
			});
		}
		function setQueryInfo(query) {
			currentQuery = query;
			$("#txtSQL").val(query.sql);
			sqlForm.setDescription(query.desc);
			enableButtons(true);
			checkSqlParams(query.sql, EXECUTE_NO_PARAMS);
		}
		function setTableInfo(table, columns) {
			currentQuery = null;
			var sql = "SELECT A." + columns[0].name;
			for (var i=1; i<columns.length; i++) {
				sql += ",\n       A." + columns[i].name;
			}
			sql += "\n  FROM " + table + " A";
			$("#txtSQL").val(sql);
			enableButtons(false);
			sqlForm.makeForm(null);
			executeSql(sql);
		}
		function enableButtons(b) {
			if (b) {
				btnEdit.parent("li").removeClass("disabled");
				btnDelete.parent("li").removeClass("disabled");
			} else {
				btnEdit.parent("li").addClass("disabled");
				btnDelete.parent("li").addClass("disabled");
			}
		}
		function updateTree(queryInfo) {
			if (currentQuery && currentQuery.id == queryInfo.id) {
				if (currentQuery.name == queryInfo.name && currentQuery.group == queryInfo.group) {
					return;
				}
				sqlTree.removeNode(currentQuery);
			}
			sqlTree.addNode(queryInfo);
			currentQuery = queryInfo;
		}
		function exportSql() {
			window.open("/sql/export.sql");
		}
		function importSql() {
			$("#importFile").val(null).click();
		}
		$("#importFile").change(function() {
			var value = $(this).val();
			if (value) {
				$("#importForm")[0].submit();
			}
		});
		$.extend(this, {
			"executeSql" : executeSql,
			"setQueryInfo" : setQueryInfo,
			"setTableInfo" : setTableInfo,
			"updateTree" : updateTree,
			"checkSqlParams" : checkSqlParams
		});
		if (settings.importInsert > 0 || settings.importUpdate > 0) {
			alert("Do import: insert=" + settings.importInsert + ", update=" + settings.importUpdate);
		}
	}
})(jQuery);
