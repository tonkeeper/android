(function () {
    // Sub-minimum bars keep the baseline edge fixed and grow toward the value, so
    // a zero/min bar sits on the shared baseline instead of straddling it.
    function barBox(base, value, pixelRatio, minLength) {
        const baseScaled = Math.round(base * pixelRatio);
        const valueScaled = Math.round(value * pixelRatio);
        const length = Math.max(Math.round(minLength * pixelRatio), Math.abs(valueScaled - baseScaled));
        const position = valueScaled <= baseScaled ? baseScaled - length : baseScaled;
        return {
            position: position,
            length: length,
        };
    }

    function barPosition(position, pixelRatio, width) {
        const scaledPosition = Math.round(position * pixelRatio);
        const length = Math.max(1, Math.round(width * pixelRatio));
        return {
            position: scaledPosition - Math.floor(length / 2),
            length: length,
        };
    }

    function columnSpacing(barSpacing, pixelRatio) {
        return Math.ceil(barSpacing * pixelRatio) <= 1 ? 0 : Math.max(1, Math.floor(pixelRatio));
    }

    function columnCommon(barSpacing, pixelRatio) {
        const spacing = columnSpacing(barSpacing, pixelRatio);
        const width = Math.round(barSpacing * pixelRatio) - spacing;
        const shiftLeft = width % 2 === 0;
        return {
            spacing,
            shiftLeft,
            columnHalfWidth: (width - (shiftLeft ? 0 : 1)) / 2,
            pixelRatio,
        };
    }

    function calculateColumnPosition(x, common, previous) {
        const scaledX = x * common.pixelRatio;
        const roundedX = Math.round(scaledX);
        const position = {
            left: roundedX - common.columnHalfWidth,
            right: roundedX + common.columnHalfWidth - (common.shiftLeft ? 1 : 0),
            shiftLeft: roundedX > scaledX,
        };
        const expectedShift = common.spacing + 1;
        if (previous && position.left - previous.right !== expectedShift) {
            if (previous.shiftLeft) {
                previous.right = position.left - expectedShift;
            } else {
                position.left = previous.right + expectedShift;
            }
        }
        return position;
    }

    function columnPositions(bars, barSpacing, pixelRatio, from, to) {
        const common = columnCommon(barSpacing, pixelRatio);
        const positions = new Map();
        let previous;
        let minWidth = Math.ceil(barSpacing * pixelRatio);

        for (let index = from; index < to; index += 1) {
            const position = calculateColumnPosition(bars[index].x, common, previous);
            if (position.right < position.left) {
                position.right = position.left;
            }
            minWidth = Math.min(minWidth, position.right - position.left + 1);
            positions.set(index, position);
            previous = position;
        }

        if (common.spacing > 0 && minWidth < 4) {
            for (const position of positions.values()) {
                const width = position.right - position.left + 1;
                if (width <= minWidth) {
                    continue;
                }
                if (position.shiftLeft) {
                    position.right -= 1;
                } else {
                    position.left += 1;
                }
            }
        }

        return positions;
    }

    class RoundedHistogramSeriesRenderer {
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
            const baseY = priceToCoordinate(options.base || 0);
            if (baseY === null) {
                return;
            }

            const dynamicPositions = options.barWidth == null
                ? columnPositions(bars, this._data.barSpacing, scope.horizontalPixelRatio, range.from, range.to)
                : null;
            const minHeight = options.barMinHeight || 1;
            const radius = Math.max(0, options.radius || 0) * Math.min(scope.horizontalPixelRatio, scope.verticalPixelRatio);
            const ctx = scope.context;

            for (let index = range.from; index < range.to; index += 1) {
                const bar = bars[index];
                const item = bar.originalData;
                if (!item || item.value === undefined) {
                    continue;
                }

                const valueY = priceToCoordinate(item.value);
                if (valueY === null) {
                    continue;
                }

                const column = dynamicPositions?.get(index);
                const x = column
                    ? { position: column.left, length: column.right - column.left + 1 }
                    : barPosition(bar.x, scope.horizontalPixelRatio, options.barWidth);
                const y = barBox(baseY, valueY, scope.verticalPixelRatio, minHeight);
                const barRadius = Math.min(radius, x.length / 2);
                ctx.fillStyle = item.color || options.color;
                if (ctx.roundRect) {
                    ctx.beginPath();
                    ctx.roundRect(x.position, y.position, x.length, y.length, barRadius);
                    ctx.fill();
                } else {
                    ctx.fillRect(x.position, y.position, x.length, y.length);
                }
            }
        }
    }

    class RoundedHistogramSeries {
        constructor() {
            this._renderer = new RoundedHistogramSeriesRenderer();
        }

        priceValueBuilder(plotRow) {
            // Include 0 so autoscale spans [0, value]; without it the scale fits
            // value..value and bars draw from below the pane. Last element is the value.
            return [0, plotRow.value];
        }

        renderer() {
            return this._renderer;
        }

        isWhitespace(data) {
            return data.value === undefined;
        }

        update(data, options) {
            this._renderer.update(data, options);
        }

        defaultOptions() {
            return {
                ...LightweightCharts.customSeriesDefaultOptions,
                color: '#2E3847',
                base: 0,
                barMinHeight: 1,
                radius: 3,
            };
        }
    }

    window.TKLightweightCharts = window.TKLightweightCharts || {};
    window.TKLightweightCharts.RoundedHistogramSeries = RoundedHistogramSeries;
})();
