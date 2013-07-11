if (typeof(flect) == "undefined") flect = {};
if (typeof(flect.app) == "undefined") flect.app = {};
if (typeof(flect.app.sqltool) == "undefined") flect.app.sqltool = {};

(function($) {
	var EXECUTE_NONE = 1,
		EXECUTE_ALWAYS = 2,
		EXECUTE_NO_PARAMS = 3,
		MSG = flect.app.sqltool.MSG;
	
	$.ajaxSetup({
		"type" : "POST",
		"error" : function(xhr, status, e) {
			error(xhr.responseText);
		}
	});
	function Enum(values, extension) {
		for (var i=0; i<values.length; i++) {
			var v = values[i];
			this[v.name] = v;
			if (typeof(extension) == "object") {
				$.extend(v, extension);
			}
		}
		$.extend(this, {
			"fromCode" : function(v) {
				for (var i=0; i<values.length; i++) {
					if (values[i].code == v) return values[i];
				}
				return null;
			},
			"fromName" : function(v) {
				for (var i=0; i<values.length; i++) {
					if (values[i].name == v) return values[i];
				}
				return null;
			},
			"fromText" : function(v) {
				for (var i=0; i<values.length; i++) {
					if (values[i].text == v) return values[i];
				}
				return null;
			},
			"bindSelect" : function(el) {
				el = $(el);
				for (var i=0; i<values.length; i++) {
					var v = values[i];
					var option = $("<option></option>");
					option.attr("value", v.code);
					option.text(v.text);
					el.append(option);
				}
				return el;
			}
		});
	}
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
		this.kind = QueryKind.fromCode(data.kind),
		this.name = data.name;
		this.group = data.group;
		this.sql = data.sql;
		this.desc = data.desc;
		if (data.setting) {
			this.setting = JSON.parse(data.setting);
		} else {
			this.setting = null;
		}
		
		this.parsedSql = null;
		
		function getHash() { 
			var s = this.setting;
			if (s) {
				s = JSON.stringify(s);
			}
			return {
				"id" : this.id,
				"kind" : this.kind.code,
				"name" : this.name,
				"group" : this.group,
				"sql" : this.sql,
				"desc" : this.desc,
				"setting" : s
			};
		}
		function fullname() {
			return this.group ? this.group + "/" + this.name : this.name;
		}
		function isParsed() { return this.parsedSql != null;}
		
		$.extend(this, {
			"fullname" : fullname,
			"getHash" : getHash,
			"isParsed" : isParsed
		});
	}
	function MessagePane(app, el) {
		el = $(el);
		function message(text) {
			sqlGraph.hide();
			sqlGrid.hide();
			sqlSheet.hide();
			el.html(text).show();
		}
		function hide() {
			el.hide();
		}
		$.extend(this, {
			"message" : message,
			"hide" : hide
		});
	}
   	function SqlTree(app, el) {
		var SCHEMAS = MSG.schemas,
			TABLES = MSG.tables,
			VIEWS = MSG.views,
			QUERIES = MSG.queries,
			expandTarget = null,
			dragTarget = null,
			groupTarget = null;
		
		function isSchemaNode(node) {
			if (node.getLevel() != 3) {
				return false;
			}
			var parent = node.getParent().getParent();
			return parent.data.title == SCHEMAS;
		}
		function isQueryNode(node) {
			return node.data.kind && node.data.kind != QueryKind.Group;
		}
		function isGroupNode(node) {
			return node.data.kind == QueryKind.Group;
		}
		function isQueryRoot(node) {
			return node.getLevel() == 1 && node.data.title == QUERIES;
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
		function doQueryNodeAction(node) {
			app.getQueryInfo(node.data.key, function(data) {
				app.setQueryInfo(new QueryInfo(data), true);
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
							"title" : data[i].name,
							"icon" : "table.png"
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
						var kind = QueryKind.fromCode(data[i].kind);
						var obj = {
							"title" : data[i].name,
							"group" : data[i].group,
							"kind" : kind
						};
						if (kind == QueryKind.Group) {
							obj.isLazy = true;
							obj.isFolder = true;
						} else {
							obj.key = data[i].id;
							obj.icon = kind.icon;
						}
						children.push(obj);
					}
					node.addChild(children);
					node.setLazyNodeStatus(DTNodeStatus_Ok);
					if (expandTarget) {
						addNode(expandTarget);
					} else if (groupTarget) {
						openQueryNode(groupTarget);
					} else if (dragTarget) {
						drop(node, dragTarget);
					}
				}
			});
		}
		function openQueryNode(targetGroup) {
			var parent = tree.getNodeByKey(QUERIES);
			while (true) {
				if (!isLoaded(parent)) {
					groupTarget = targetGroup;
					parent.expand(true);
					return;
				}
				groupTarget = null;
				var children = parent.getChildren(),
					newParent = null;
				if (!children) {
					return;
				}
				for (var i=0; i<children.length; i++) {
					var child = children[i],
						group = child.data.group;
					if (group == targetGroup) {
						return;
					} else if (targetGroup.indexOf(group + "/") == 0) {
						newParent = child;
						break;
					}
				}
				if (!newParent) {
					break;
				}
				parent = newParent;
			}
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
				if (child.data.kind && child.data.kind != QueryKind.Group) {
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
					var child = getChildNode(parent, QueryKind.Group, names[i]);
					if (child == null) {
						var group = names[i];
						if (parent.data.group) {
							group = parent.data.group + "/" + group;
						}
						child = parent.addChild({
							"title" : names[i],
							"group" : group,
							"kind" : QueryKind.Group,
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
			var newNode = getChildNode(parent, queryInfo.kind, queryInfo.name);
			if (newNode == null || newNode.data.key != queryInfo.id) {
				newNode = parent.addChild({
					"title" : queryInfo.name,
					"group" : parent.data.group,
					"key" : queryInfo.id,
					"kind" : queryInfo.kind,
					"icon" : queryInfo.kind.icon
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
		function dragStart(sourceNode) {
			if (isGroupNode(sourceNode)) {
				if (!isLoaded(sourceNode)) {
					sourceNode.reloadChildren();
				}
				return true;
			}
			return isQueryNode(sourceNode);
		}
		function dragEnter(targetNode, sourceNode) {
			if (targetNode == sourceNode.getParent()) {
				return false;
			}
			if (isGroupNode(targetNode)) {
				if (isGroupNode(sourceNode)) {
					var parent = targetNode.getParent();
					while (parent != null) {
						if (parent == sourceNode) {
							return false;
						}
						parent = parent.getParent();
					}
				}
				return true;
			}
			return isQueryRoot(targetNode);
		}
		function drop(targetNode, sourceNode) {
			if (!isLoaded(targetNode)) {
				dragTarget = sourceNode;
				targetNode.expand(true);
				return;
			}
			dragTarget = null;
			if (isQueryNode(sourceNode)) {
				app.moveQuery(sourceNode.data.key, targetNode.data.group || "");
			} else if (isGroupNode(sourceNode)) {
				app.moveGroup(sourceNode.data.group, targetNode.data.group || "");
			} else {
				alert("IllegalState: " + sourceNode.data.title);
			}
		}
		var tree = $(el).dynatree({
			"imagePath" : "/assets/images/",
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
			"dnd" : {
				"onDragStart" : dragStart,
				"onDragEnter" : dragEnter,
				"onDrop" : drop
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
			"openQueryNode" : openQueryNode,
			"removeNode" : removeNode
		});
	};
	function SaveDialog(app, el) {
		var dialog = $(el),
			initialized = false;
		function show(mode, queryInfo) {
			var id = "", 
				kind = QueryKind.Query.code,
				name = "", 
				group = "", 
				desc = "",
				spreadsheet = "",
				worksheet = "",
				scheduleTime = "00:00:00";
			if (queryInfo) {
				id = queryInfo.id;
				kind = queryInfo.kind.code,
				name = queryInfo.name;
				group = queryInfo.group;
				desc = queryInfo.desc;
				if (queryInfo.kind == QueryKind.Schedule) {
					spreadsheet = queryInfo.setting.spreadsheet;
					worksheet = queryInfo.setting.worksheet;
					scheduleTime = queryInfo.setting.time;
				}
			}
			$("#sql-name").val(name);
			$("#sql-group").val(group);
			$("#sql-kind").val(kind).change();
			$("#sql-desc").val(desc);
			$("#schedule-spreadsheet").val(spreadsheet);
			$("#schedule-worksheet").val(worksheet);
			$("#schedule-time").val(scheduleTime);
			
			dialog.dialog({
				"title" : MSG.saveSql,
				"position" : [ "center", 40],
				"buttons" : [
					{
						"text" : MSG.save,
						"click" : function() { 
							doSave(mode, id);
						}
					},
					{
						"text" : MSG.cancel,
						"click" : function() { 
							close();
						}
					}
				],
				"width" : "800px",
				"modal" : true
			});
			initialized = true;
		}
		function close() {
			if (initialized) {
				dialog.dialog("close");
			}
		}
		function doSave(mode, id) {
			var name = $("#sql-name").val(),
				kind = $("#sql-kind").val(),
				group = $("#sql-group").val(),
				desc = $("#sql-desc").val();
				sql = $("#txtSQL").val(),
				spreadsheet = $("#schedule-spreadsheet").val(),
				worksheet = $("#schedule-worksheet").val(),
				scheduleTime = $("#schedule-time").val();
			var hash = {
				"id" : id,
				"kind" : kind,
				"name" : name,
				"group" : group,
				"desc" : desc,
				"sql" : sql
			}
			if (!name) {
				alert(MSG.format(MSG.required, MSG.name));
				return;
			}
			if (kind == QueryKind.Schedule.code) {
				if (!spreadsheet) {
					alert(MSG.format(MSG.requiredIfSchedule, MSG.spreadsheet));
					return;
				}
				if (!worksheet) {
					alert(MSG.format(MSG.requiredIfSchedule, MSG.worksheet));
					return;
				}
				hash.setting = JSON.stringify({
					"spreadsheet" : spreadsheet,
					"worksheet" : worksheet,
					"time" : scheduleTime
				});
			}
			group = normalizeGroup(group);
			save(mode, new QueryInfo(hash));
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
	function GraphSettingDialog(app, el) {
		var dialog = $(el),
			initialized = false;
		function show(info) {
			dialog.load("/graphsetting/" + info.kind.graphType, {}, function(data) {
				var graphSetting = info.setting;
				if (!graphSetting) {
					graphSetting = $.extend(true, {}, info.kind.defaults);
				}
				setup(graphSetting);
				dialog.dialog({
					"title" : MSG.graphSetting,
					"buttons" : [
						{
							"text" : MSG.save,
							"click" : function() { 
								doSave(info, graphSetting);
							}
						},
						{
							"text" : MSG.cancel,
							"click" : function() { 
								close();
							}
						}
					],
					"width" : "400px",
					"modal" : true
				});
				initialized = true;
			});
		}
		function setup(graphSetting) {
			dialog.find(":input").each(function() {
				var input = $(this),
					names = input.attr("name").split("_"),
					obj = graphSetting;
				for (var i=0; i<names.length; i++) {
					if (obj[names[i]] !== undefined) {
						obj = obj[names[i]];
					} else {
						return;
					}
				}
				if (input.attr("type") == "radio") {
					obj = "" + obj;
					if (input.attr("value") == obj) {
						input.attr("checked", "checked");
					}
				} else {
					input.val(obj);
				}
			});
		}
		function merge(graphSetting) {
			dialog.find(":input").each(function() {
				var input = $(this),
					names = input.attr("name").split("_"),
					obj = graphSetting;
				if (input.attr("type") == "radio" && !input.is(":checked")) {
					return;
				}
				for (var i=0; i<names.length-1; i++) {
					if (!obj[names[i]]) {
						obj[names[i]] = {};
					}
					obj = obj[names[i]];
				}
				var value = input.val();
				if (input.attr("type") == "radio") {
					if (value === "true") {
						value = true;
					} else if (value === "false") {
						value = false;
					}
				}
				obj[names[names.length-1]] = value;
			});
		}
		function doSave(info, graphSetting) {
			merge(graphSetting);
			close();
			$.ajax({
				"url" : "/graph/update",
				"data" : {
					"id" : info.id,
					"setting" : JSON.stringify(graphSetting)
				},
				"success" : function() {
					info.setting = graphSetting;
					app.execute();
				}
			});
		}
		function close() {
			if (initialized) {
				dialog.dialog("close");
			}
		}
		$.extend(this, {
			"show" : show
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
					if (!name) {
						throw "Invalid param: [" + name + ":" + type + "]";
					}
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
							throw "Invalid datatype: " + name + ": " + type;
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
				var fieldset = $("<fieldset></fieldset>");
				fieldset.append(ul);
				form.append(fieldset);
			} else {
				form.append("<div class='noParameters'>" + MSG.noParameters + "</div>");
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
	function SqlSheet(app, el) {
		el = $(el);
		function hide() {
			el.hide();
			return this;
		}
		function show(info) {
			el.attr("src", "/google/show/" + info.setting.spreadsheet + "/" + info.setting.worksheet).show();
			return this;
		}
		function execute(info, sql) {
			$.ajax({
				"url" : "/google/execute",
				"data" : {
					"id" : info.id,
					"sql" : sql
				},
				"success" : function(data) {
					if (data == "OK") {
						el.show();
					} else {
						el.hide();
						error(data);
					}
				}
			});
		}
		$.extend(this, {
			"execute" : execute,
			"show" : show,
			"hide" : hide
		});
	}
	function error(str) {
		msgPane.message(str);
	};
	var sqlGrid, sqlTree, msgPane, sqlTabs, sqlForm, sqlGraph, sqlSheet,
		saveDialog, graphSettingDialog;
	var SaveMode = {
			"NEW" : "new",
			"UPDATE" : "update",
			"EDIT" : "edit"
		},
		QueryKind = new Enum([
			{ "code" : -1, "name" : "Group",     "text" : MSG.group },
			{ "code" : 1,  "name" : "Query",     "text" : MSG.query, "icon" : "query.png"},
			{ "code" : 11, "name" : "PieGraph",  "text" : MSG.pieGraph,  "icon" : "pie_chart.png", "graphType" : "pie", "defaults" : {
					"others" : {
						"count" : 12,
						"label" : MSG.other
					}
				}
			},
			{ "code" : 12, "name" : "BarGraph",  "text" : MSG.barGraph,  "icon" : "bar_chart.png", "graphType" : "bar", "defaults" : {
					"bars" : {
						"horizontal" : false,
						"stacked" : true
					}
				}
			},
			{ "code" : 13, "name" : "LineGraph", "text" : MSG.lineGraph, "icon" : "line_chart.png", "graphType" : "line", "defaults" : {
					"xaxis" : {
						"labelCount" : 10
					}
				}
			},
			{ "code" : 21, "name" : "Schedule", "text" : MSG.snapshot, "icon" : "schedule.png"}
		], {
			"isGraph" : function() { 
				return !!this.graphType;
			}
		});
	
	flect.app.sqltool.SqlTool = function(settings) {
		msgPane = new MessagePane(this, "#error-msg");
		sqlGrid = new flect.util.SqlGrid({
			"modelPath" : "/sql/model",
			"dataPath" : "/sql/data",
			"downloadPath" : "/sql/download",
			"div" : "#grid-pane",
			"error" : error
		}),
		sqlGraph = new flect.util.SqlGraph({
			"dataPath" : "/graph/data",
			"container" : "#graph-pane",
			"div" : "#graph-canvas",
			"error" : error
		});
		saveDialog = new SaveDialog(this, "#saveDialog"),
		graphSettingDialog = new GraphSettingDialog(this, "#graphSettingDialog");
		sqlTree = new SqlTree(this, "#tree-pane");
		sqlTabs = new SqlTabs(this, "#sql-tab");
		sqlForm = new SqlForm(this, "#formForm", "#formDesc");
		sqlSheet = new SqlSheet(this, "#sheet-frame");
		
		var sqlKind = QueryKind.bindSelect("#sql-kind");
		removeSqlKindFromSelect(QueryKind.Group);
		if (!settings.scheduleEnabled) {
			removeSqlKindFromSelect(QueryKind.Schedule);
		}
		sqlKind.change(function() {
			var value = $(this).val();
			if (value == QueryKind.Schedule.code) {
				$("#schedule-setting").show();
			} else {
				$("#schedule-setting").hide();
			}
		});
		
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
			btnExec = $("#btnExec").click(execute),
			btnNew = $("#btnNew").click(function() {
				setQueryInfo(null);
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
				if (currentQuery && confirm(MSG.confirmDelete)) {
					removeQueryInfo(currentQuery);
				}
			}),
			btnGraphSetting = $("#btnGraphSetting").click(function() {
				graphSetting();
			}),
			txtSql = $("#txtSQL").change(function() {
				sqlForm.setBuilded(false);
				if (currentQuery) {
					currentQuery.parsedSql = null;
				}
			});
		function removeSqlKindFromSelect(kind) {
			sqlKind.find("option[value=" + kind.code + "]").remove();
		}
		function checkSql() {
			var sql = $("#txtSQL").val();
			if (!sql) {
				alert(MSG.format(MSG.required, "SQL"));
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
						setQueryInfo(null);
					} else {
						alert(data);
					}
				}
			});
		}
		function execute() {
			if (sqlForm.isBuilded() && (currentQuery == null || (currentQuery && currentQuery.isParsed()))) {
				if (currentQuery) {
					executeSql(currentQuery.parsedSql);
				} else {
					executeSql(checkSql());
				}
			} else {
				var sql = checkSql();
				if (sql) {
					checkSqlParams(sql, EXECUTE_ALWAYS);
				}
			}
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
					msg += MSG.format(MSG.required, params.empty[i]);
				}
				alert(msg);
				return;
			}
			msgPane.hide();
			sqlGraph.hide();
			sqlGrid.hide();
			if (currentQuery == null || currentQuery.kind == QueryKind.Query) {
				sqlSheet.hide();
				var h = $("#lower-pane").height() - 120;
				sqlGrid.show().height(h).execute(sql, params.params);
			} else if (currentQuery.kind == QueryKind.Schedule) {
				sqlSheet.execute(currentQuery, sql);
			} else {
				sqlSheet.hide();
				var graphSetting = currentQuery.setting;
				if (!graphSetting) {
					graphSetting = currentQuery.kind.defaults;
				}
				graphSetting = $.extend(true, {}, graphSetting);
				graphSetting.title = currentQuery.name;
				sqlGraph.show().execute(sql, currentQuery.kind.graphType, params.params, graphSetting);
			}
		}
		function checkSqlParams(sql, execMode) {
			$.ajax({
				"url" : "/sql/queryParams",
				"data" : {
					"sql" : sql
				},
				"success" : function(data) {
					try {
						sqlForm.makeForm(data.params);
					} catch (e) {
						error(e);
						return;
					}
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
									sqlGraph.hide();
									sqlSheet.hide();
								} else {
									executeSql(data.sql);
								}
							} else {
								sqlGrid.hide();
								sqlGraph.hide();
								sqlSheet.hide();
							}
							break;
						default:
							alert("IllegalExecuteMode: " + execMode);
							break;
					}
				}
			});
		}
		function setQueryInfo(query, bExec) {
			currentQuery = query;
			if (query) {
				$("#txtSQL").val(query.sql);
				$("#lblQuery").text(query.fullname());
				sqlForm.setDescription(query.desc);
				enableButtons(true);
				if (bExec) {
					if (query.kind == QueryKind.Schedule) {
						sqlGrid.hide();
						sqlGraph.hide();
						sqlSheet.show(query);
					} else {
						checkSqlParams(query.sql, EXECUTE_NO_PARAMS);
					}
				}
			} else {
				$("#txtSQL").val("");
				enableButtons(false);
				sqlForm.setDescription(null);
				sqlForm.makeForm(null);
				$("#lblQuery").empty();
				sqlTabs.activateSql();
			}
		}
		function setTableInfo(table, columns) {
			setQueryInfo(null);
			var sql = "SELECT A." + columns[0].name;
			for (var i=1; i<columns.length; i++) {
				sql += ",\n       A." + columns[i].name;
			}
			sql += "\n  FROM " + table + " A";
			$("#txtSQL").val(sql);
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
			if (currentQuery && currentQuery.kind.graphType) {
				btnGraphSetting.removeAttr("disabled");
			} else {
				btnGraphSetting.attr("disabled", "disabled");
			}
		}
		function updateTree(queryInfo) {
			if (currentQuery && currentQuery.id == queryInfo.id) {
				if (currentQuery.name == queryInfo.name && currentQuery.group == queryInfo.group) {
					currentQuery = queryInfo;
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
		function moveQuery(sourceId, newGroup) {
			function doMoveQuery(info) {
				var newInfo = new QueryInfo(info.getHash());
				newInfo.group = newGroup;
				saveDialog.save(SaveMode.EDIT, newInfo);
			}
			if (currentQuery && currentQuery.id == sourceId) {
				doMoveQuery(currentQuery);
			} else {
				getQueryInfo(sourceId, function(data) {
					var info = new QueryInfo(data);
					setQueryInfo(info, false);
					doMoveQuery(info);
				});
			}
		}
		function moveGroup(oldGroup, newGroup) {
			$.ajax({
				"url" : "/sql/moveGroup",
				"data" : {
					"oldGroup" : oldGroup,
					"newGroup" : newGroup
				},
				"success" : function() {
					location.reload();
				}
			});
		}
		function getQueryInfo(id, callback) {
			$.ajax({
				"url" : "/sql/queryInfo",
				"data" : {
					"id" : id
				},
				"success" : callback
			});
		}
		function graphSetting() {
			if (currentQuery && currentQuery.kind.graphType) {
				graphSettingDialog.show(currentQuery);
			}
		}
		$("#importFile").change(function() {
			var value = $(this).val();
			if (value) {
				$("#importForm")[0].submit();
			}
		});
		$.extend(this, {
			"execute" : execute,
			"setQueryInfo" : setQueryInfo,
			"setTableInfo" : setTableInfo,
			"updateTree" : updateTree,
			"moveQuery" : moveQuery,
			"moveGroup" : moveGroup,
			"getQueryInfo" : getQueryInfo,
			"checkSqlParams" : checkSqlParams
		});
		if (settings.importInsert > 0 || settings.importUpdate > 0) {
			alert(MSG.format(MSG.importResult, settings.importInsert, settings.importUpdate));
		}
		if (settings.targetGroup) {
			sqlTree.openQueryNode(settings.targetGroup);
		}
	}
})(jQuery);
