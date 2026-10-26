// Glue between LightweightChartView and the lightweight-charts runtime.
window.AndroidChart = (function () {
    'use strict';

    var chart = null;
    var seriesById = {};
    var pluginsById = {};

    function post(message) {
        if (window.AndroidChartBridge) {
            window.AndroidChartBridge.postMessage(JSON.stringify(message));
        }
    }

    function reportError(where, err) {
        post({
            type: 'error',
            where: where,
            message: err && err.message ? err.message : String(err)
        });
    }

    function guarded(name, fn) {
        return function () {
            try {
                return fn.apply(null, arguments);
            } catch (err) {
                reportError(name, err);
                return undefined;
            }
        };
    }

    function onCrosshairMove(param) {
        var seriesData = {};
        Object.keys(seriesById).forEach(function (id) {
            var value = param.seriesData.get(seriesById[id]);
            if (value != null) {
                seriesData[id] = value;
            }
        });
        post({
            type: 'crosshairMove',
            time: typeof param.time === 'number' ? param.time : null,
            point: param.point ? { x: param.point.x, y: param.point.y } : null,
            leftmostIndex: param.point ? chart.timeScale().coordinateToLogical(0) : null,
            seriesData: seriesData
        });
    }

    function viewportSize() {
        return {
            // The page can load before the WebView is laid out; never create a 0-sized chart.
            width: Math.max(1, document.documentElement.clientWidth),
            height: Math.max(1, document.documentElement.clientHeight)
        };
    }

    function layoutChart() {
        if (chart) {
            var size = viewportSize();
            chart.resize(size.width, size.height);
            refreshZoomSensitiveLabels();
        }
    }

    function postLayoutInfo() {
        var size = viewportSize();
        post({
            type: 'layout',
            viewportWidth: size.width,
            viewportHeight: size.height,
            paneWidth: chart.paneSize().width,
            paneHeight: chart.paneSize().height,
            timeScaleHeight: chart.timeScale().height(),
            rightScaleWidth: chart.priceScale('right').width(),
            devicePixelRatio: window.devicePixelRatio
        });
    }

    var timeLabelFormatters = {};

    function timeLabel(timeSeconds, locale) {
        var key = locale || '';
        if (!timeLabelFormatters[key]) {
            timeLabelFormatters[key] = new Intl.DateTimeFormat(locale || undefined, {
                hour: '2-digit',
                minute: '2-digit',
                hourCycle: 'h23',
                timeZone: 'UTC'
            });
        }
        return timeLabelFormatters[key].format(new Date(timeSeconds * 1000));
    }

    function hourMarksFit() {
        var timeScale = chart.timeScale();
        var range = timeScale.getVisibleRange();
        if (!range || typeof range.from !== 'number' || typeof range.to !== 'number') {
            return false;
        }
        var bars = timeScale.timeToIndex(range.to, false) - timeScale.timeToIndex(range.from, false);
        if (!(bars > 0) || !(range.to > range.from)) {
            return false;
        }
        var barSeconds = (range.to - range.from) / bars;
        var options = timeScale.options();
        var maxLabelWidth = (chart.options().layout.fontSize + 4) * 5 / 8 * (options.tickMarkMaxCharacterLength || 8);
        var maxIndexesPerMark = Math.ceil(maxLabelWidth / options.barSpacing);
        return 86400 / barSeconds / 2 >= maxIndexesPerMark;
    }

    var tickMarkFormatters = {
        dayMarksAsTime: function (time, tickMarkType, locale) {
            if (tickMarkType > 2 || typeof time !== 'number' || !hourMarksFit()) {
                return null;
            }
            return timeLabel(time, locale);
        }
    };

    var zoomSensitiveLabels = false;
    var lastHourMarksFit = null;

    function resolveTimeScaleOptions(options) {
        if (options && typeof options.tickMarkFormatter === 'string') {
            var formatter = tickMarkFormatters[options.tickMarkFormatter];
            if (!formatter) {
                throw new Error('Unknown tickMarkFormatter: ' + options.tickMarkFormatter);
            }
            options.tickMarkFormatter = formatter;
            zoomSensitiveLabels = true;
        }
        return options;
    }

    function refreshZoomSensitiveLabels() {
        if (!zoomSensitiveLabels) {
            return;
        }
        var fit = hourMarksFit();
        if (fit !== lastHourMarksFit) {
            lastHourMarksFit = fit;
            chart.timeScale().applyOptions({});
        }
    }

    return {
        init: guarded('init', function (options) {
            document.body.style.margin = 0;
            document.body.style.touchAction = 'pan-y';
            var size = viewportSize();
            options.width = size.width;
            options.height = size.height;
            resolveTimeScaleOptions(options.timeScale);
            chart = LightweightCharts.createChart(document.body, options);
            chart.subscribeCrosshairMove(onCrosshairMove);
            chart.timeScale().subscribeVisibleLogicalRangeChange(guarded('visibleLogicalRange', function (range) {
                if (range) {
                    var timeScale = chart.timeScale();
                    post({
                        type: 'visibleLogicalRange',
                        from: range.from,
                        to: range.to,
                        leftmostIndex: timeScale.coordinateToLogical(0),
                        rightmostIndex: timeScale.coordinateToLogical(timeScale.width() - 1)
                    });
                    refreshZoomSensitiveLabels();
                }
            }));
            // lightweight-charts ignores touchcancel and would drop the next touchstart and keep
            // the crosshair armed; replay it as a touchend and clear the crosshair ourselves.
            document.addEventListener('touchcancel', guarded('touchcancel', function (event) {
                event.target.dispatchEvent(new TouchEvent('touchend', {
                    bubbles: true,
                    cancelable: true,
                    touches: [],
                    targetTouches: [],
                    changedTouches: Array.prototype.slice.call(event.changedTouches)
                }));
                chart.clearCrosshairPosition();
                // clearCrosshairPosition fires no crosshair-move event; clear the plugins and
                // the native side ourselves, or both keep their last crosshair state.
                Object.keys(pluginsById).forEach(function (id) {
                    var plugin = pluginsById[id];
                    if (plugin && plugin.clearCrosshair) {
                        plugin.clearCrosshair();
                    }
                });
                post({ type: 'crosshairMove', time: null, point: null, seriesData: {} });
            }), true);
            new ResizeObserver(guarded('layout', layoutChart)).observe(document.documentElement);
            window.addEventListener('resize', guarded('layout', layoutChart));
            post({ type: 'ready' });
            setTimeout(guarded('layoutInfo', postLayoutInfo), 500);
        }),

        applyChartOptions: guarded('applyChartOptions', function (options) {
            resolveTimeScaleOptions(options.timeScale);
            chart.applyOptions(options);
        }),

        addSeries: guarded('addSeries', function (id, type, options) {
            var series;
            if (type === 'area') {
                series = chart.addSeries(LightweightCharts.AreaSeries, options);
            } else if (type === 'roundedCandlestick') {
                series = chart.addCustomSeries(new window.TKLightweightCharts.RoundedCandlestickSeries(), options);
            } else if (type === 'roundedHistogram') {
                series = chart.addCustomSeries(new window.TKLightweightCharts.RoundedHistogramSeries(), options);
            } else {
                throw new Error('Unknown series type: ' + type);
            }
            seriesById[id] = series;
        }),

        applySeriesOptions: guarded('applySeriesOptions', function (id, options) {
            seriesById[id].applyOptions(options);
        }),

        setSeriesData: guarded('setSeriesData', function (id, data) {
            seriesById[id].setData(data);
        }),

        updateSeriesBar: guarded('updateSeriesBar', function (id, bar) {
            seriesById[id].update(bar);
        }),

        applySeriesPriceScaleOptions: guarded('applySeriesPriceScaleOptions', function (id, options) {
            seriesById[id].priceScale().applyOptions(options);
        }),

        applyPriceScaleOptions: guarded('applyPriceScaleOptions', function (scaleId, options) {
            chart.priceScale(scaleId).applyOptions(options);
        }),

        applyTimeScaleOptions: guarded('applyTimeScaleOptions', function (options) {
            chart.timeScale().applyOptions(resolveTimeScaleOptions(options));
        }),

        resetTimeScale: guarded('resetTimeScale', function () {
            chart.timeScale().resetTimeScale();
        }),

        setVisibleLogicalRange: guarded('setVisibleLogicalRange', function (from, to) {
            chart.timeScale().setVisibleLogicalRange({ from: from, to: to });
        }),

        attachPriceLevelMarkers: guarded('attachPriceLevelMarkers', function (pluginId, seriesId, options) {
            pluginsById[pluginId] = window.TKLightweightCharts.createPriceLevelMarkers(seriesById[seriesId], options);
        }),

        setPriceLevelMarkers: guarded('setPriceLevelMarkers', function (pluginId, markers) {
            pluginsById[pluginId].setMarkers(markers);
        }),

        applyPriceLevelMarkersOptions: guarded('applyPriceLevelMarkersOptions', function (pluginId, options) {
            pluginsById[pluginId].applyOptions(options);
        }),

        detachPriceLevelMarkers: guarded('detachPriceLevelMarkers', function (pluginId) {
            var plugin = pluginsById[pluginId];
            if (plugin) {
                plugin.detach();
                delete pluginsById[pluginId];
            }
        })
    };
})();
