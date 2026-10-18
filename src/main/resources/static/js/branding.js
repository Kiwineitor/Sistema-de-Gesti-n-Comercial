(function () {
    'use strict';
    function luminance(hex) {
        const channels = [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16) / 255)
            .map(v => v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4));
        return channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722;
    }
    window.brandContrast = function (a, b) {
        const x = luminance(a), y = luminance(b);
        return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
    };
    window.brandMix = function (a, b, ratio) {
        return '#' + [1, 3, 5].map(i => {
            const first = parseInt(a.slice(i, i + 2), 16);
            const second = parseInt(b.slice(i, i + 2), 16);
            return Math.round(first * (1 - ratio) + second * ratio).toString(16).padStart(2, '0');
        }).join('').toUpperCase();
    };
    window.brandTextOnLight = function (color, background, dark) {
        if (brandContrast(color, background) >= 4.5) return color;
        for (let step = 1; step <= 100; step++) {
            const candidate = brandMix(color, dark, step / 100);
            if (brandContrast(candidate, background) >= 4.5) return candidate;
        }
        return dark;
    };
    window.brandTextOnColor = function (color, light, dark) {
        return brandContrast(light, color) >= brandContrast(dark, color) ? light : dark;
    };
    window.brandMoney = function (amount) {
        return `${window.NEGOCIO?.simboloMoneda || ''} ${Number(amount || 0).toFixed(2)}`.trim();
    };
})();
