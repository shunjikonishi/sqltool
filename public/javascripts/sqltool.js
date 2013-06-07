if (typeof(flect) == "undefined") flect = {};
if (typeof(flect.app) == "undefined") flect.app = {};
if (typeof(flect.app.sqltool) == "undefined") flect.app.sqltool = {};

(function($) {
	function error(str) {
		$("#grid-pane").hide();
		$("#error-msg").html(str).show();
	};
	function MessagePane(el) {
		el = $(el);
		function message(text) {
			grid.hide();
			el.html(text).show();
		}
		$.extend(this, {
			"message" : message
		});
	}
   	function SqlTree(el) {
		function lazyRead(url, node) {
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
				}
			});
		}
		var tree = $(el).dynatree({
			"onLazyRead" : function(node) {
				var title = node.data.title;
				if (title == "Tables") {
					lazyRead("/sql/tables", node);
				} else if (title == "Views") {
					lazyRead("/sql/views", node);
				}
			},
			"children" : [
				{
					"title" : "Queries",
					"isFolder" : true
				},
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
				}
			]
		});
	};
	var grid, tree, msgPane;
	flect.app.sqltool.SqlTool = function(setting) {
		msgPane = new MessagePane("#error-msg");
		grid = new flect.util.SqlGrid({
			"modelPath" : "/sql/model",
			"dataPath" : "/sql/data",
			"div" : "#grid-pane",
			"error" : msgPane.message
		});
		tree = new SqlTree("#tree-pane");
		
		$("#sql-tab").tabs();
		$("#btnExec").click(function() {
			var sql = $("#txtSQL").val();
			var h = $("#lower-pane").height() - 120;
			grid.show().height(h).execute(sql);
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
