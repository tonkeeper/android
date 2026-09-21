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

    return {
        init: guarded('init', function (options) {
            document.body.style.margin = 0;
            document.body.style.touchAction = 'pan-y';
            var size = viewportSize();
            options.width = size.width;
            options.height = size.height;
            chart = LightweightCharts.createChart(document.body, options);
            chart.subscribeCrosshairMove(onCrosshairMove);
            // A vertical drag ends in a touchcancel, which tracking mode ignores — without this
            // the crosshair would stay armed until the next tap.
            document.addEventListener('touchcancel', guarded('touchcancel', function () {
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
            chart.timeScale().applyOptions(options);
        }),

        resetTimeScale: guarded('resetTimeScale', function () {
            chart.timeScale().resetTimeScale();
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
