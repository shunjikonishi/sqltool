if (typeof(flect) == "undefined") flect = {};
if (typeof(flect.util) == "undefined") flect.util = {};

/*
setting {
	"modelPath": "Http request path for get colModel.",
	"dataPath" : "Http request path for get query data.",
	"table" : "id of table element"
	"height" : "Height of grid",
	"gridCaption" : "Caption of grid",
	"effect" : "effect for showing and hiding",
	"error" : "function of error handling. Its parameter is one string",
	"gridId" : "id of grid"
}
*/
flect.util.SqlGrid = function(setting) {
	setting = $.extend({
		"height" : "200px",
		"gridCaption" : "Results",
		"gridId" : "sqlgrid",
		"resize" : true,
		"error" : function(str) {
			alert(error);
		}
	}, setting);
	var div = $(setting.div),
		grid = null,
		pager = null;
	
	function defaultError(str) {
		alert(str);
	}
	function hide() {
		div.hide(setting.effect);
		return this;
	}
	function show() {
		div.show(setting.effect);
		return this;
	}
	function height(v) {
		if (v) {
			setting.height = v;
			return this;
		} else {
			return setting.height;
		}
	}
	function execute(sql, params) {
		var data = {
			"sql" : sql
		};
		$.ajax({
			"url" : setting.modelPath, 
			"type" : "POST",
			"data" : {
				"sql" : sql
			},
			"success" : function(data) {
				makeGrid(sql, data, params);
			},
			"error" : function(xhr) {
				setting.error(xhr.responseText);
			}
		});
		return this;
	}
	function download(sql, params, type) {
		var data = {
			"sql" : sql,
			"type" : type
		}
		if (params && $.isArray(params) && params.length > 0) {
			data["sql-param"] = JSON.stringify(params);
		}
		var form = div.find("#" + setting.gridId + "-downloadForm");
		if (form.length ==0) {
			form = $("<form method='POST' target='_blank'></form>");
			form.attr({
				"action" : setting.downloadPath,
				"id" : setting.gridId + "-downloadForm"
			});
			div.append(form);
		}
		form.empty();
		for (var key in data) {
			var input = $("<input type='hidden'></input>");
			input.attr({
				"name" : key,
				"value" : data[key]
			});
			form.append(input);
		}
		form[0].submit();
	}
	function makeGrid(sql, colModel, params) {
		if (grid) {
			grid.jqGrid("GridDestroy");
		}
		div.empty();
		
		grid = $("<table/>").attr("id", setting.gridId);
		pager = $("<div/>").attr("id", setting.gridId + "-pager");
		div.append(grid).append(pager);
		
		var data = {
			"sql" : sql
		}
		if (params && $.isArray(params) && params.length > 0) {
			data["sql-param"] = JSON.stringify(params);
		}
		
		var t,
			pagerId = "#" + setting.gridId + "-pager";
		grid.jqGrid({
			"url" : setting.dataPath,
			"datatype" : "json",
			"mtype" : "post",
			"colModel" : colModel,
			"pager" : pager,
			"rowNum" : 50,
			"gridview" : true,
			"sortable" : true,
			"postData": data,
			"rowList" : [10, 50, 100],
			"rownumbers" : true,
			"viewrecords" : true,
			"autowidth" : true,
			"height" : setting.height,
			"shrinkToFit" : false,
			"caption" : setting.gridCaption,
			"beforeRequest" : function() {
				t = new Date().getTime();
			},
			"loadError" : function(xhr, status, ex) {
				var t2 = new Date().getTime();
				var caption = setting.gridCaption + " - error(" + (t2 - t) + "ms)";
				grid.jqGrid("setCaption", caption);
				setting.error(xhr.responseText);
			},
			"loadComplete" : function(data) {
				var t2 = new Date().getTime();
				var caption = setting.gridCaption + " - (" + (t2 - t) + "ms)";
				grid.jqGrid("setCaption", caption);
			}
		}).jqGrid("navGrid", pagerId, {
			"add" : false,
			"del" : false,
			"edit" : false,
			"refresh" : true,
			"search" : false,
			"view" : false
		}, {
		}).jqGrid("navButtonAdd", pagerId, {
			"caption" : "",
			"buttonicon" : "ui-icon-arrowthickstop-1-s",
			"onClickButton" : function() {
				download(sql, params, "CSV");
			},
			"title" : "Download as CSV"
		}).jqGrid("navButtonAdd", pagerId, {
			"caption" : "",
			"buttonicon" : "ui-icon-calculator",
			"onClickButton" : function() {
				download(sql, params, "Excel");
			},
			"title" : "Download as Excel"
		});
		if (setting.resize) {
			grid.jqGrid("gridResize");
		}
	}
	$.extend(this, {
		"execute" : execute,
		"height" : height,
		"show" : show,
		"hide" : hide
	});
}
