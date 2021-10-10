var formatter = new byteFormatter();

function addZ(n) {
	return n < 10 ? '0' + n : '' + n;
}

function formatDate(input, source) {
	if (source === 'today' || source === 'day') {
		var date = new Date(input);
		return addZ(date.getHours()) + ':' + addZ(date.getMinutes());
	} else {
		return input.substr(0, input.indexOf(',')) + ", " + new Date(input).getFullYear();
	}
}

function qsParam(_name) {
	var qs = window.location.search.substring(1);
	var tokens = qs.split('&');
	for (var i = 0; i < tokens.length; i++) {
		var param = tokens[i].split('=');
		if (param[0] == _name) {
			return param[1];
		}
	}
	return null;
}

function formatBytes(input) {
	var value = parseFloat(input);
	input = input.toLowerCase();
	if (input.indexOf('gb') !== -1) {
		value *= Math.pow(1024,3);
	}
	if (input.indexOf('mb') !== -1) {
		value *= Math.pow(1024,2);
	}
	if (input.indexOf('kb') !== -1) {
		value *= Math.pow(1024,1);
	}
	return value;
}

function determineSource() {
	var source = qsParam('source');
	if (source === null) {
		source = 'all';
	}
	return source;
}

function pathForSource(source) {
	var now = new Date();
	var year = now.getFullYear();
	var month = now.getMonth() + 1;
	var day = now.getDate();
	switch (source) {
	case 'day':
		return qsParam('date');
		break;
	case 'today':
		return year + '-' + month + '-' + day;
		break;
	case 'month':
		return year + '-' + month;
		break;
	case 'all':
	default:
		break;
	}
	return 'all';
}

function loadData(source, path) {
	var source = source || determineSource();
	var path = path || pathForSource(source);
	$.getJSON('data/' + path + '.json', function (data) {
		if(data && data.length > 0) {
			processBandwidth(data, source);
			processSpeed(data, source);
		} else {
			$('.chart').remove();
			$('body').append('<p align=\'center\'>missing data for graphs</p>');
		}
	});
}

function processBandwidth(data, source) {
	var bwdata = [['Day', 'Upload', 'Download']];
	$.each(data, function (k, v) {
		bwdata.push([formatDate(v.date, source), formatBytes(v.upload), formatBytes(v.download)]);
	});
	//console.table(bwdata);
	drawBwChart(bwdata, source);
}

function processSpeed(data, source) {
	var spdata = [['Time', 'Upload', 'Download']];
	var pTime =  null, pUpload = 0, pDownload = 0;
	$.each(data, function (k, v) {
		var fUpload = formatBytes(v.upload);
		var fDownload = formatBytes(v.download);
		calculateSpeed(source, pTime, new Date(v.date), pUpload, fUpload, pDownload, fDownload, spdata);
		pUpload = fUpload;
		pDownload = fDownload;
		pTime = new Date(v.date);

	});
	drawSpChart(spdata, source);
}

function calculateSpeed(source, pTime, cTime, pUpload, cUpload, pDownload, cDownload, spdata) {
	if(pTime === null) {
		pTime = new Date(cTime.getFullYear(), cTime.getMonth(), cTime.getDate());
	}
	var timeSpend;
	if(source === 'day' || source === 'today') {
		timeSpend = (cTime.getTime() - pTime.getTime()) / 1000;
	} else {
		timeSpend = 86400;
		pUpload = 0;
		pDownload = 0;
	}

	var bytesUploaded 				= cUpload - pUpload;
	var bytesUploadedFormatted 		= formatter.formatValue(cUpload - pUpload);
	var speedUp 					= formatter.formatValue(bytesUploaded / timeSpend);

	var bytesDownloaded 			= cDownload - pDownload;
	var bytesDownloadedFormatted 	= formatter.formatValue(bytesDownloaded);
	var speedDown 					= formatter.formatValue(bytesDownloaded / timeSpend);

	//console.log(formatDate(pTime, 'today') + '-' + formatDate(cTime, 'today') + ': ' + bytesDownloadedFormatted + ' where downloaded (' + speedDown + '/s) and ' +
	//	bytesUploadedFormatted + ' were uploaded (' + speedUp + '/s)');
	spdata.push([formatDate(cTime, 'today'), bytesUploaded / timeSpend, bytesDownloaded / timeSpend]);
}



function byteFormatter(options) {
	var log1024 = Math.log(1024);
	this.scaleSuffix = ["B", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB"];
	this.formatValue = function (value, pf) {
		if(value === 0) {
			return "0 B";
		}
		if(!pf) {
			pf = '';
		}
		var scale = Math.floor(Math.log(value) / log1024);
		var scaleSuffix = this.scaleSuffix[scale];
		var scaledValue = value / Math.pow(1024, scale);
		return Math.round(scaledValue * 100) / 100 + " " + scaleSuffix + pf;
	};
	this.format = function (dt, c, pf) {
		var rows = dt.getNumberOfRows();
		for (var r = 0; r < rows; ++r) {
			var v = dt.getValue(r, c);
			var fv = this.formatValue(v, pf);
			dt.setFormattedValue(r, c, fv);
		}
	};
}

function drawBwChart(bwdata, source) {
	var data = google.visualization.arrayToDataTable(bwdata);
	var rangesDown = data.getColumnRange(2);
	var rangesUp = data.getColumnRange(1);

	formatter.format(data, 1);
	formatter.format(data, 2);
	var max = Math.max(rangesDown.max, rangesUp.max) * 1;

	var options = {
		title: 'Bandwidth',
		fontSize: '10',
		legend: {
			position:  'none'
		},
		vAxis: {
			ticks: [{
					v: 0
				}, {
					v: max * 0.2,
					f: formatter.formatValue(max * 0.2)
				}, {
					v: max * 0.4,
					f: formatter.formatValue(max * 0.4)
				}, {
					v: max * 0.6,
					f: formatter.formatValue(max * 0.6)
				}, {
					v: max * 0.8,
					f: formatter.formatValue(max * 0.8)
				}, {
					v: max,
					f: formatter.formatValue(max)
				}
			]
		}
	};

	var chart = new google.visualization.AreaChart(document.getElementById('chart_bw'));
	chart.draw(data, options);

	if(source === 'all' || source === 'month') {
		google.visualization.events.addListener(chart, 'select', function() {
			var selection = chart.getSelection();
			var r = selection[0].row, c = selection[0].column;
			var date = (source === 'all') ? new Date(data.getValue(r, 0)) : new Date(data.getValue(r, 0) + ', ' + new Date().getFullYear());
			var dateString = date.getFullYear() + '-' + (date.getMonth()+1) + '-' + date.getDate();
			var locationString = "/bw/?source=day&date="+dateString;
			history.replaceState({source: 'day', date: dateString}, 'selection: ' + dateString, locationString);
			loadData('day', dateString);
		});
	}
}

function drawSpChart(spdata, source) {
	var data = google.visualization.arrayToDataTable(spdata);
	var ranges = data.getColumnRange(2);

	formatter.format(data, 1, '/s');
	formatter.format(data, 2, '/s');
	var max = ranges.max * 1;

	var options = {
		title: 'Speed',
		fontSize: '10',
		legend: {
			position:  'none'
		},
		vAxis: {
			ticks: [{
					v: 0
				}, {
					v: max * 0.2,
					f: formatter.formatValue(max * 0.2)
				}, {
					v: max * 0.4,
					f: formatter.formatValue(max * 0.4)
				}, {
					v: max * 0.6,
					f: formatter.formatValue(max * 0.6)
				}, {
					v: max * 0.8,
					f: formatter.formatValue(max * 0.8)
				}, {
					v: max,
					f: formatter.formatValue(max)
				}
			]
		}
	};

	var chart = new google.visualization.AreaChart(document.getElementById('chart_sp'));
	chart.draw(data, options);
}
