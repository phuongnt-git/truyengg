/**
 * BlurHash Decoder
 * Decodes blurhash strings to image data for placeholder previews
 * Based on https://github.com/woltapp/blurhash
 */

const BlurHashDecoder = (() => {
    'use strict';

    // Base83 character set
    const BASE83_CHARS = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~';

    // Lookup table for Base83
    const BASE83_LOOKUP = {};
    BASE83_CHARS.split('').forEach((char, i) => {
        BASE83_LOOKUP[char] = i;
    });

    /**
     * Decode Base83 string to integer
     */
    function decodeBase83(str) {
        let value = 0;
        for (const char of str) {
            const digit = BASE83_LOOKUP[char];
            if (digit === undefined) {
                throw new Error(`Invalid Base83 character: ${char}`);
            }
            value = value * 83 + digit;
        }
        return value;
    }

    /**
     * Decode DC value (first value, representing average color)
     */
    function decodeDC(value) {
        const r = value >> 16;
        const g = (value >> 8) & 255;
        const b = value & 255;
        return [sRGBToLinear(r), sRGBToLinear(g), sRGBToLinear(b)];
    }

    /**
     * Decode AC value (subsequent values)
     */
    function decodeAC(value, maxAC) {
        const quantR = Math.floor(value / (19 * 19));
        const quantG = Math.floor(value / 19) % 19;
        const quantB = value % 19;

        return [
            signPow((quantR - 9) / 9, 2.0) * maxAC,
            signPow((quantG - 9) / 9, 2.0) * maxAC,
            signPow((quantB - 9) / 9, 2.0) * maxAC
        ];
    }

    /**
     * Convert sRGB to linear
     */
    function sRGBToLinear(value) {
        const v = value / 255;
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    /**
     * Convert linear to sRGB
     */
    function linearToSRGB(value) {
        const v = Math.max(0, Math.min(1, value));
        return v <= 0.0031308
            ? Math.round(v * 12.92 * 255 + 0.5)
            : Math.round((1.055 * Math.pow(v, 1 / 2.4) - 0.055) * 255 + 0.5);
    }

    /**
     * Signed power function
     */
    function signPow(value, exp) {
        return Math.sign(value) * Math.pow(Math.abs(value), exp);
    }

    /**
     * Decode blurhash to pixel array
     */
    function decode(blurhash, width, height, punch = 1) {
        if (!blurhash || blurhash.length < 6) {
            throw new Error('Invalid blurhash');
        }

        // Decode size flag
        const sizeFlag = decodeBase83(blurhash[0]);
        const numY = Math.floor(sizeFlag / 9) + 1;
        const numX = (sizeFlag % 9) + 1;

        // Decode quantised max AC
        const quantisedMaxAC = decodeBase83(blurhash[1]);
        const maxAC = (quantisedMaxAC + 1) / 166;

        // Decode colors
        const colors = [];

        // DC value
        const dcValue = decodeBase83(blurhash.substring(2, 6));
        colors.push(decodeDC(dcValue));

        // AC values
        for (let i = 1; i < numX * numY; i++) {
            const acValue = decodeBase83(blurhash.substring(4 + i * 2, 6 + i * 2));
            colors.push(decodeAC(acValue, maxAC * punch));
        }

        // Generate pixel data
        const pixels = new Uint8ClampedArray(width * height * 4);

        for (let y = 0; y < height; y++) {
            for (let x = 0; x < width; x++) {
                let r = 0, g = 0, b = 0;

                for (let j = 0; j < numY; j++) {
                    for (let i = 0; i < numX; i++) {
                        const basis = Math.cos((Math.PI * x * i) / width) *
                            Math.cos((Math.PI * y * j) / height);
                        const color = colors[i + j * numX];
                        r += color[0] * basis;
                        g += color[1] * basis;
                        b += color[2] * basis;
                    }
                }

                const idx = 4 * (x + y * width);
                pixels[idx] = linearToSRGB(r);
                pixels[idx + 1] = linearToSRGB(g);
                pixels[idx + 2] = linearToSRGB(b);
                pixels[idx + 3] = 255;
            }
        }

        return pixels;
    }

    /**
     * Decode blurhash to canvas
     */
    function decodeToCanvas(blurhash, width, height, punch = 1) {
        const pixels = decode(blurhash, width, height, punch);

        const canvas = document.createElement('canvas');
        canvas.width = width;
        canvas.height = height;

        const ctx = canvas.getContext('2d');
        const imageData = ctx.createImageData(width, height);
        imageData.data.set(pixels);
        ctx.putImageData(imageData, 0, 0);

        return canvas;
    }

    /**
     * Decode blurhash to data URL
     */
    function decodeToDataURL(blurhash, width = 32, height = 32, punch = 1) {
        try {
            const canvas = decodeToCanvas(blurhash, width, height, punch);
            return canvas.toDataURL();
        } catch (error) {
            console.warn('[BlurHash] Failed to decode:', error);
            return null;
        }
    }

    /**
     * Create placeholder image element
     */
    function createPlaceholder(blurhash, width = 32, height = 32) {
        const dataUrl = decodeToDataURL(blurhash, width, height);
        if (!dataUrl) return null;

        const img = document.createElement('img');
        img.src = dataUrl;
        img.className = 'blurhash-placeholder';
        img.style.cssText = 'width: 100%; height: 100%; object-fit: cover; filter: blur(20px); transform: scale(1.1);';

        return img;
    }

    /**
     * Apply blurhash as background to element
     */
    function applyAsBackground(element, blurhash, width = 32, height = 32) {
        const dataUrl = decodeToDataURL(blurhash, width, height);
        if (dataUrl && element) {
            element.style.backgroundImage = `url(${dataUrl})`;
            element.style.backgroundSize = 'cover';
            element.style.backgroundPosition = 'center';
        }
    }

    /**
     * Validate blurhash format
     */
    function isValidBlurhash(blurhash) {
        if (!blurhash || typeof blurhash !== 'string' || blurhash.length < 6) {
            return false;
        }

        try {
            const sizeFlag = decodeBase83(blurhash[0]);
            const numY = Math.floor(sizeFlag / 9) + 1;
            const numX = (sizeFlag % 9) + 1;
            const expectedLength = 4 + 2 * numX * numY;

            return blurhash.length === expectedLength;
        } catch {
            return false;
        }
    }

    // Public API
    return {
        decode,
        decodeToCanvas,
        decodeToDataURL,
        createPlaceholder,
        applyAsBackground,
        isValidBlurhash
    };
})();

// Export for module systems
if (typeof module !== 'undefined' && module.exports) {
    module.exports = BlurHashDecoder;
}

