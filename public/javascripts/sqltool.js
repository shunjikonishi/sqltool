if (typeof(flect) == "undefined") flect = {};
if (typeof(flect.app) == "undefined") flect.app = {};

flect.app.sqltool = {};
/*
setting {
	"modelPath": "Http request path for get colModel.",
	"dataPath" : "Http request path for get query data.",
	"table" : "id of table element"
	"height" : "Height of grid",
	"gridCaption" : "Caption of grid",
	"error" : "function of error handling. Its parameter is one string",
	"gridId" : "id of grid"
}
*/
flect.app.sqltool.SqlTool = function(setting) {
	function error(str) {
		$("#error-msg").html(str);
	}
	var grid = new flect.util.SQLGrid({
			"modelPath" : "/sql/model",
			"dataPath" : "/sql/data",
			"div" : "#grid-pane",
			"error" : error
		});
	$("#btnExec").click(function() {
		var sql = $("#txtSQL").val();
		grid.execute(sql);
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
}
