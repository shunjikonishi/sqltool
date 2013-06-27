if (typeof(flect) == "undefined") flect = {};
if (typeof(flect.util) == "undefined") flect.util = {};

/*
setting {
	"dataPath" : "Http request path for get query data.",
	"container" : "Id for graph container. Used by show and hide method.",
	"error" : "function of error handling. Its parameter is one string"
}
*/
flect.util.SqlGraph = function(setting) {
	setting = $.extend({
		"error" : function(str) {
			alert(error);
		}
	}, setting);
	var div = $(setting.div)
		container = div;
	if (setting.container) {
		container = $(setting.container);
	}
	
	function hide() {
		container.hide(setting.effect);
		return this;
	}
	function show() {
		container.show(setting.effect);
		return this;
	}
	function makePieGraph(data, graphSetting) {
		var pieSetting = {
			"HtmlText" : false,
			"grid" : {
				"verticalLines" : false,
				"horizontalLines" : false
			},
			"xaxis" : {
				"showLabels" : false 
			},
			"yaxis" : { 
				"showLabels" : false 
			},
			"pie" : {
				"show" : true,
				"explode" : 6
			},
			"mouse" : {
				"track" : true,
				"relative" : true,
				"trackFormatter": function(d) {
					return d.series.label + ": " + d.y;
				}
			},
			"legend" : {
				"position" : "se",
				"backgroundColor" : "#D2E8FF"
			},
			"others" : {
				"count" : 10,
				"label" : "Other"
			}
		}
		if (graphSetting) {
			pieSetting = $.extend(true, pieSetting, graphSetting);
		}
		var array = [];
		for (var i=0; i<data.data.length; i++) {
			var gd = data.data[i],
				n = [[0, gd.numbers[0]]];
			if (i <= pieSetting.others.count) {
				array.push({
					"data" : n,
					"label" : gd.label
				});
			} else {
				var others = array[array.length - 1];
				others.label = pieSetting.others.label;
				others.data[0][1] += gd.numbers[0];
			}
		}
		Flotr.draw(div[0], array, pieSetting);
	}
	function makeBarGraph(data, graphSetting) {
		var barSetting = {
			"legend" : {
				"backgroundColor" : "#D2E8FF", // Light blue 
				"position" : "ne"
			},
			"bars" : {
				"show" : true,
				"stacked" : true,
				"horizontal" : false,
				"barWidth" : 0.6,
				"lineWidth" : 1,
				"shadowSize" : 0
			},
			"grid" : {
				"verticalLines" : false,
				"horizontalLines" : true
			},
			"xaxis" : {
				"showLabels" : true,
			},
			"yaxis" : {
				"showLabels" : true,
			}
		};
		if (graphSetting) {
			barSetting = $.extend(true, barSetting, graphSetting);
		}
		var horizontal = barSetting.bars.horizontal,
			stacked = barSetting.bars.stacked;
		if (!stacked) {
			barSetting.bars.barWidth = barSetting.bars.barWidth / data.series.length;
		}
		var seriesData = [],
			barWidth = barSetting.bars.barWidth;
		for (var i=0; i<data.series.length; i++) {
			seriesData.push({
				"data" : [],
				"label" : data.series[i]
			});
		}
		var ticks = [];
		for (var i=0; i<data.data.length; i++) {
			ticks.push([i, data.data[i].label]);
			for (var j=0; j<data.series.length; j++) {
				var n = i;
				if (!stacked) {
					n = (n + (barWidth * j)) - (barWidth * data.series.length / 2)
				}
				if (horizontal) {
					seriesData[j].data.push([data.data[i].numbers[j], n]);
				} else {
					seriesData[j].data.push([n, data.data[i].numbers[j]]);
				}
			}
		}
		if (horizontal) {
			barSetting.yaxis.ticks = ticks;
		} else {
			barSetting.xaxis.ticks = ticks;
		}
		Flotr.draw(div[0], seriesData, barSetting);
	}
	function makeLineGraph(data, graphSetting) {
		var lineSetting = {
			"legend" : {
				"backgroundColor" : "#D2E8FF" // Light blue 
			},
			"xaxis" : {
				"showLabels" : true,
				"labelsAngle" : 45,
				"labelCount" : 10
			},
		};
		if (graphSetting) {
			lineSetting = $.extend(true, lineSetting, graphSetting);
		}
		var seriesData = [];
		for (var i=0; i<data.series.length; i++) {
			seriesData.push({
				"data" : [],
				"label" : data.series[i]
			});
		}
		var ticks = [],
			labelMod = Math.floor(data.data.length / (lineSetting.xaxis.labelCount - 1));
		for (var i=0; i<data.data.length; i++) {
			if (i % labelMod == 0) {
				ticks.push([i, data.data[i].label]);
			}
			for (var j=0; j<data.series.length; j++) {
				seriesData[j].data.push([i, data.data[i].numbers[j]]);
			}
		}
		lineSetting.xaxis.ticks = ticks;
		Flotr.draw(div[0], seriesData, lineSetting);
	}
	function execute(sql, graphType, params, graphSetting) {
		div.empty();
		var postData = {
			"sql" : sql
		}
		if (params && $.isArray(params) && params.length > 0) {
			postData["sql-param"] = JSON.stringify(params);
		}
		$.ajax({
			"url" : setting.dataPath,
			"type" : "POST",
			"data" : postData,
			"success" : function(data) {
				switch (graphType) {
					case "pie":
						makePieGraph(data, graphSetting);
						break;
					case "bar":
						makeBarGraph(data, graphSetting);
						break;
					case "line":
						makeLineGraph(data, graphSetting);
						break;
				}
			}
		});
		return this;
	}
	$.extend(this, {
		"execute" : execute,
		"show" : show,
		"hide" : hide
	});
}
