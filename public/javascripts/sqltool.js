if (typeof(flect) == "undefined") flect = {};
if (typeof(flect.app) == "undefined") flect.app = {};
if (typeof(flect.app.sqltool) == "undefined") flect.app.sqltool = {};

(function($) {
	function error(str) {
		$("#grid-pane").hide();
		$("#error-msg").html(str).show();
	};
	function SqlTree(el) {
		var tree = $(el).dynatree({
			"onLazyRead" : function(node) {
				var title = node.data.title;
				if (title == "Tables") {
					node.appendAjax({
						"url": "/sql/tables",
						"type" : "POST"
					});
				} else if (title == "Views") {
					node.appendAjax({
						"url": "/sql/views",
						"type" : "POST"
					});
				}
			},
			"children" : [
				{
					"title" : "Schemas",
					"isFolder" : true,
					"children" : [
						{
							"title" : "Tables",
							"isFolder" : true,
							"isLazy" : true
						},
						{
							"title" : "Views",
							"isFolder" : true,
							"isLazy" : true
						}
					]
				},
				{
					"title" : "Queries",
					"isFolder" : true
				}
			]
		});
	};
	flect.app.sqltool.SqlTool = function(setting) {
		var grid = new flect.util.SqlGrid({
				"modelPath" : "/sql/model",
				"dataPath" : "/sql/data",
				"div" : "#grid-pane",
				"error" : error
			}),
			tree = new SqlTree("#tree-pane");
		$("#btnExec").click(function() {
			var sql = $("#txtSQL").val();
			$("#grid-pane").show();
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
})(jQuery);
