(function () {
    function bitmapLinePosition(position, pixelRatio, width) {
        const scaledPosition = Math.round(position * pixelRatio);
        const length = Math.max(1, Math.round(width));
        return {
            position: scaledPosition - Math.floor(length / 2),
            length: length,
        };
    }

    function boxPosition(first, second, pixelRatio, minLength) {
        const firstScaled = Math.round(first * pixelRatio);
        const secondScaled = Math.round(second * pixelRatio);
        const position = Math.min(firstScaled, secondScaled);
        const length = Math.max(Math.round(minLength * pixelRatio), Math.abs(secondScaled - firstScaled));
        return {
            position: position,
            length: length,
        };
    }

    function optimalCandlestickWidth(barSpacing, pixelRatio) {
        const specialCaseFrom = 2.5;
        const specialCaseTo = 4;
        if (barSpacing >= specialCaseFrom && barSpacing <= specialCaseTo) {
            return Math.floor(3 * pixelRatio);
        }

        const coeff = 1 - (0.2 * Math.atan(Math.max(specialCaseTo, barSpacing) - specialCaseTo)) / (Math.PI * 0.5);
        const width = Math.floor(barSpacing * coeff * pixelRatio);
        const scaledBarSpacing = Math.floor(barSpacing * pixelRatio);
        return Math.max(Math.floor(pixelRatio), Math.min(width, scaledBarSpacing));
    }

    function candlestickWidth(barSpacing, pixelRatio) {
        let width = optimalCandlestickWidth(barSpacing, pixelRatio);
        if (width >= 2) {
            const wickWidth = Math.floor(pixelRatio);
            if (wickWidth % 2 !== width % 2) {
                width -= 1;
            }
        }
        return width;
    }

    class RoundedCandlestickSeriesRenderer {
        constructor() {
            this._data = null;
            this._options = null;
        }

        update(data, options) {
            this._data = data;
            this._options = options;
        }

        draw(target, priceToCoordinate) {
            target.useBitmapCoordinateSpace(scope => {
                this._draw(scope, priceToCoordinate);
            });
        }

        _draw(scope, priceToCoordinate) {
            if (this._data === null || this._options === null || this._data.visibleRange === null) {
                return;
            }

            const bars = this._data.bars;
            const range = this._data.visibleRange;
            const options = this._options;

            const bodyWidth = options.bodyWidth == null
                ? candlestickWidth(this._data.barSpacing, scope.horizontalPixelRatio)
                : Math.max(1, Math.round(options.bodyWidth * scope.horizontalPixelRatio));
            const wickWidth = options.wickWidth == null
                ? Math.max(1, Math.floor(scope.horizontalPixelRatio))
                : Math.max(1, Math.round(options.wickWidth * scope.horizontalPixelRatio));
            const bodyMinHeight = options.bodyMinHeight || 2;
            const radius = Math.min(
                Math.max(0, options.radius || 0) * Math.min(scope.horizontalPixelRatio, scope.verticalPixelRatio),
                bodyWidth / 2
            );
            const ctx = scope.context;

            for (let index = range.from; index < range.to; index += 1) {
                const bar = bars[index];
                const item = bar.originalData;
                if (!item || item.open === undefined || item.high === undefined || item.low === undefined || item.close === undefined) {
                    continue;
                }

                const isUp = item.close >= item.open;
                const wickColor = item.wickColor || item.color || (isUp ? options.wickUpColor : options.wickDownColor);
                const bodyColor = item.color || (isUp ? options.upColor : options.downColor);

                const highY = priceToCoordinate(item.high);
                const lowY = priceToCoordinate(item.low);
                const openY = priceToCoordinate(item.open);
                const closeY = priceToCoordinate(item.close);
                if (highY === null || lowY === null || openY === null || closeY === null) {
                    continue;
                }

                if (options.wickVisible !== false) {
                    const wickX = bitmapLinePosition(bar.x, scope.horizontalPixelRatio, wickWidth);
                    const wickY = boxPosition(highY, lowY, scope.verticalPixelRatio, 1);
                    ctx.fillStyle = wickColor;
                    ctx.fillRect(wickX.position, wickY.position, wickX.length, wickY.length);
                }

                const bodyX = bitmapLinePosition(bar.x, scope.horizontalPixelRatio, bodyWidth);
                const bodyY = boxPosition(openY, closeY, scope.verticalPixelRatio, bodyMinHeight);
                ctx.fillStyle = bodyColor;
                if (ctx.roundRect) {
                    ctx.beginPath();
                    ctx.roundRect(bodyX.position, bodyY.position, bodyX.length, bodyY.length, radius);
                    ctx.fill();
                } else {
                    ctx.fillRect(bodyX.position, bodyY.position, bodyX.length, bodyY.length);
                }
            }
        }
    }

    class RoundedCandlestickSeries {
        constructor() {
            this._renderer = new RoundedCandlestickSeriesRenderer();
        }

        priceValueBuilder(plotRow) {
            return [plotRow.high, plotRow.low, plotRow.close];
        }

        renderer() {
            return this._renderer;
        }

        isWhitespace(data) {
            return data.close === undefined;
        }

        update(data, options) {
            this._renderer.update(data, options);
        }

        defaultOptions() {
            return {
                ...LightweightCharts.customSeriesDefaultOptions,
                upColor: '#39CC83',
                downColor: '#FF4766',
                wickVisible: true,
                wickUpColor: '#39CC83',
                wickDownColor: '#FF4766',
                bodyMinHeight: 2,
                radius: 3,
            };
        }
    }

    window.TKLightweightCharts = window.TKLightweightCharts || {};
    window.TKLightweightCharts.RoundedCandlestickSeries = RoundedCandlestickSeries;
})();
