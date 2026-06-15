(function() {
    'use strict';

    class WebGLHeatmap {
        constructor(options = {}) {
            this.options = Object.assign({
                radius: 30,
                blur: 20,
                maxOpacity: 0.8,
                minOpacity: 0.1,
                gradient: {
                    0.0: '#0000ff',
                    0.25: '#00ffff',
                    0.5: '#00ff00',
                    0.75: '#ffff00',
                    1.0: '#ff0000'
                },
                useDevicePixelRatio: true,
                maxPoints: 5000,
                autoRender: true
            }, options);

            this._canvas = document.createElement('canvas');
            this._gl = null;
            this._program = null;
            this._pointsBuffer = null;
            this._colorTexture = null;
            this._points = [];
            this._width = 0;
            this._height = 0;
            this._projectionMatrix = new Float32Array(16);
            this._dirty = true;
            this._webglSupported = false;
            this._fallbackCanvas = null;
            this._fallbackCtx = null;

            this._initWebGL();
        }

        _initWebGL() {
            const canvas = this._canvas;
            const gl = canvas.getContext('webgl', {
                antialias: false,
                alpha: true,
                premultipliedAlpha: false,
                preserveDrawingBuffer: false
            }) || canvas.getContext('experimental-webgl');

            if (!gl) {
                console.warn('WebGL not supported, falling back to Canvas 2D');
                this._initFallback();
                return;
            }

            this._gl = gl;
            this._webglSupported = true;
            this._createShaders();
            this._createBuffers();
            this._createColorTexture();
            this._setDefaultGLState();
        }

        _initFallback() {
            this._fallbackCanvas = this._canvas;
            this._fallbackCtx = this._fallbackCanvas.getContext('2d');
        }

        _createShaders() {
            const gl = this._gl;

            const vertexShaderSource = `
                attribute vec2 a_position;
                attribute float a_value;
                attribute float a_radius;
                
                uniform mat4 u_projectionMatrix;
                uniform vec2 u_resolution;
                uniform float u_pixelRatio;
                
                varying float v_value;
                varying vec2 v_center;
                varying float v_radius;
                
                void main() {
                    vec2 pixelPos = a_position * u_pixelRatio;
                    vec2 clipSpace = (pixelPos / u_resolution) * 2.0 - 1.0;
                    clipSpace.y = -clipSpace.y;
                    
                    float radiusPx = a_radius * u_pixelRatio;
                    v_radius = radiusPx;
                    v_center = clipSpace.xy;
                    v_value = a_value;
                    
                    gl_Position = u_projectionMatrix * vec4(clipSpace, 0.0, 1.0);
                    gl_PointSize = radiusPx * 2.0 + 4.0;
                }
            `;

            const fragmentShaderSource = `
                precision mediump float;
                
                varying float v_value;
                varying vec2 v_center;
                varying float v_radius;
                
                uniform sampler2D u_colorRamp;
                uniform float u_maxOpacity;
                uniform float u_minOpacity;
                
                void main() {
                    vec2 coord = gl_PointCoord - vec2(0.5);
                    float dist = length(coord) * 2.0;
                    
                    if (dist > 1.0) {
                        discard;
                    }
                    
                    float gaussian = exp(-dist * dist * 3.0);
                    float alpha = gaussian * v_value * u_maxOpacity;
                    alpha = max(alpha, u_minOpacity * v_value);
                    
                    vec2 texCoord = vec2(v_value, 0.5);
                    vec4 color = texture2D(u_colorRamp, texCoord);
                    
                    gl_FragColor = vec4(color.rgb, alpha * color.a);
                }
            `;

            const vertexShader = this._compileShader(gl.VERTEX_SHADER, vertexShaderSource);
            const fragmentShader = this._compileShader(gl.FRAGMENT_SHADER, fragmentShaderSource);

            const program = gl.createProgram();
            gl.attachShader(program, vertexShader);
            gl.attachShader(program, fragmentShader);
            gl.linkProgram(program);

            if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
                console.error('WebGL program link error:', gl.getProgramInfoLog(program));
                this._initFallback();
                return;
            }

            this._program = program;
            gl.deleteShader(vertexShader);
            gl.deleteShader(fragmentShader);
        }

        _compileShader(type, source) {
            const gl = this._gl;
            const shader = gl.createShader(type);
            gl.shaderSource(shader, source);
            gl.compileShader(shader);

            if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
                console.error('WebGL shader compile error:', gl.getShaderInfoLog(shader));
                return null;
            }

            return shader;
        }

        _createBuffers() {
            const gl = this._gl;
            this._pointsBuffer = gl.createBuffer();
            this._indexBuffer = gl.createBuffer();

            const maxPoints = this.options.maxPoints;
            const floatCount = 4;

            gl.bindBuffer(gl.ARRAY_BUFFER, this._pointsBuffer);
            gl.bufferData(gl.ARRAY_BUFFER, maxPoints * floatCount * 4, gl.DYNAMIC_DRAW);

            const indices = new Uint16Array(maxPoints * 6);
            for (let i = 0; i < maxPoints; i++) {
                const offset = i * 4;
                indices[i * 6] = offset;
                indices[i * 6 + 1] = offset + 1;
                indices[i * 6 + 2] = offset + 2;
                indices[i * 6] = offset;
                indices[i * 6 + 3] = offset + 2;
                indices[i * 6 + 4] = offset + 3;
            }

            gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, this._indexBuffer);
            gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, indices, gl.STATIC_DRAW);
        }

        _createColorTexture() {
            const gl = this._gl;
            const gradient = this.options.gradient;

            const canvas = document.createElement('canvas');
            canvas.width = 256;
            canvas.height = 1;
            const ctx = canvas.getContext('2d');

            const gradientObj = ctx.createLinearGradient(0, 0, 256, 0);
            const stops = Object.keys(gradient).map(k => parseFloat(k)).sort((a, b) => a - b);

            stops.forEach(stop => {
                gradientObj.addColorStop(stop, gradient[stop]);
            });

            ctx.fillStyle = gradientObj;
            ctx.fillRect(0, 0, 256, 1);

            const imageData = ctx.getImageData(0, 0, 256, 1);

            this._colorTexture = gl.createTexture();
            gl.bindTexture(gl.TEXTURE_2D, this._colorTexture);
            gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
            gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
            gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
            gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
            gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, imageData);
        }

        _setDefaultGLState() {
            const gl = this._gl;
            gl.enable(gl.BLEND);
            gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
            gl.disable(gl.DEPTH_TEST);
        }

        _updateProjectionMatrix() {
            const m = this._projectionMatrix;
            const w = this._width;
            const h = this._height;

            m[0] = 2 / w;  m[4] = 0;      m[8] = 0;   m[12] = -1;
            m[1] = 0;      m[5] = -2 / h; m[9] = 0;   m[13] = 1;
            m[2] = 0;      m[6] = 0;      m[10] = 1;  m[14] = 0;
            m[3] = 0;      m[7] = 0;      m[11] = 0;  m[15] = 1;
        }

        setData(points) {
            this._points = points || [];
            this._dirty = true;
            if (this.options.autoRender) {
                this.render();
            }
        }

        addData(points) {
            if (Array.isArray(points)) {
                this._points = this._points.concat(points);
            } else {
                this._points.push(points);
            }
            this._dirty = true;
            if (this.options.autoRender) {
                this.render();
            }
        }

        clear() {
            this._points = [];
            this._dirty = true;
            if (this.options.autoRender) {
                this.render();
            }
        }

        setSize(width, height) {
            const pixelRatio = this.options.useDevicePixelRatio ? (window.devicePixelRatio || 1) : 1;
            this._width = width;
            this._height = height;

            this._canvas.width = width * pixelRatio;
            this._canvas.height = height * pixelRatio;
            this._canvas.style.width = width + 'px';
            this._canvas.style.height = height + 'px';

            if (this._gl) {
                this._gl.viewport(0, 0, this._canvas.width, this._canvas.height);
            }

            this._updateProjectionMatrix();
            this._dirty = true;
            if (this.options.autoRender) {
                this.render();
            }
        }

        getCanvas() {
            return this._canvas;
        }

        render() {
            if (this._webglSupported) {
                this._renderWebGL();
            } else {
                this._renderFallback();
            }
            this._dirty = false;
        }

        _renderWebGL() {
            const gl = this._gl;
            if (!gl || !this._program) return;

            gl.clearColor(0, 0, 0, 0);
            gl.clear(gl.COLOR_BUFFER_BIT);

            if (this._points.length === 0) return;

            gl.useProgram(this._program);

            const pixelRatio = this.options.useDevicePixelRatio ? (window.devicePixelRatio || 1) : 1;

            const uResolution = gl.getUniformLocation(this._program, 'u_resolution');
            gl.uniform2f(uResolution, this._canvas.width, this._canvas.height);

            const uPixelRatio = gl.getUniformLocation(this._program, 'u_pixelRatio');
            gl.uniform1f(uPixelRatio, pixelRatio);

            const uMaxOpacity = gl.getUniformLocation(this._program, 'u_maxOpacity');
            gl.uniform1f(uMaxOpacity, this.options.maxOpacity);

            const uMinOpacity = gl.getUniformLocation(this._program, 'u_minOpacity');
            gl.uniform1f(uMinOpacity, this.options.minOpacity);

            const uColorRamp = gl.getUniformLocation(this._program, 'u_colorRamp');
            gl.activeTexture(gl.TEXTURE0);
            gl.bindTexture(gl.TEXTURE_2D, this._colorTexture);
            gl.uniform1i(uColorRamp, 0);

            const uProjectionMatrix = gl.getUniformLocation(this._program, 'u_projectionMatrix');
            gl.uniformMatrix4fv(uProjectionMatrix, false, this._projectionMatrix);

            const aPosition = gl.getAttribLocation(this._program, 'a_position');
            const aValue = gl.getAttribLocation(this._program, 'a_value');
            const aRadius = gl.getAttribLocation(this._program, 'a_radius');

            const pointCount = Math.min(this._points.length, this.options.maxPoints);
            const floatCount = 4;
            const vertexData = new Float32Array(pointCount * floatCount * 4);

            for (let i = 0; i < pointCount; i++) {
                const point = this._points[i];
                const x = point[0];
                const y = point[1];
                const value = Math.max(0, Math.min(1, point[2] || 0));
                const radius = this.options.radius * pixelRatio;

                const offset = i * floatCount * 4;

                vertexData[offset] = x - radius;
                vertexData[offset + 1] = y - radius;
                vertexData[offset + 2] = value;
                vertexData[offset + 3] = radius;

                vertexData[offset + 4] = x + radius;
                vertexData[offset + 5] = y - radius;
                vertexData[offset + 6] = value;
                vertexData[offset + 7] = radius;

                vertexData[offset + 8] = x + radius;
                vertexData[offset + 9] = y + radius;
                vertexData[offset + 10] = value;
                vertexData[offset + 11] = radius;

                vertexData[offset + 12] = x - radius;
                vertexData[offset + 13] = y + radius;
                vertexData[offset + 14] = value;
                vertexData[offset + 15] = radius;
            }

            gl.bindBuffer(gl.ARRAY_BUFFER, this._pointsBuffer);
            gl.bufferSubData(gl.ARRAY_BUFFER, 0, vertexData);

            gl.enableVertexAttribArray(aPosition);
            gl.vertexAttribPointer(aPosition, 2, gl.FLOAT, false, floatCount * 4, 0);

            gl.enableVertexAttribArray(aValue);
            gl.vertexAttribPointer(aValue, 1, gl.FLOAT, false, floatCount * 4, 2 * 4);

            gl.enableVertexAttribArray(aRadius);
            gl.vertexAttribPointer(aRadius, 1, gl.FLOAT, false, floatCount * 4, 3 * 4);

            gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, this._indexBuffer);
            gl.drawElements(gl.TRIANGLES, pointCount * 6, gl.UNSIGNED_SHORT, 0);

            gl.disableVertexAttribArray(aPosition);
            gl.disableVertexAttribArray(aValue);
            gl.disableVertexAttribArray(aRadius);
        }

        _renderFallback() {
            const ctx = this._fallbackCtx;
            const canvas = this._fallbackCanvas;

            ctx.clearRect(0, 0, canvas.width, canvas.height);

            if (this._points.length === 0) return;

            const pixelRatio = this.options.useDevicePixelRatio ? (window.devicePixelRatio || 1) : 1;
            const radius = this.options.radius * pixelRatio;
            const blur = this.options.blur * pixelRatio;

            const shadowCanvas = document.createElement('canvas');
            shadowCanvas.width = canvas.width;
            shadowCanvas.height = canvas.height;
            const shadowCtx = shadowCanvas.getContext('2d');

            shadowCtx.globalCompositeOperation = 'lighter';

            for (let i = 0; i < this._points.length; i++) {
                const point = this._points[i];
                const x = point[0] * pixelRatio;
                const y = point[1] * pixelRatio;
                const value = Math.max(0, Math.min(1, point[2] || 0));

                const grd = shadowCtx.createRadialGradient(x, y, 0, x, y, radius + blur);
                const alpha = value * this.options.maxOpacity;
                grd.addColorStop(0, `rgba(255, 255, 255, ${alpha})`);
                grd.addColorStop(1, 'rgba(255, 255, 255, 0)');

                shadowCtx.fillStyle = grd;
                shadowCtx.beginPath();
                shadowCtx.arc(x, y, radius + blur, 0, Math.PI * 2);
                shadowCtx.fill();
            }

            const imageData = shadowCtx.getImageData(0, 0, canvas.width, canvas.height);
            const pixels = imageData.data;

            const gradient = this.options.gradient;
            const stops = Object.keys(gradient).map(k => parseFloat(k)).sort((a, b) => a - b);
            const colors = stops.map(s => this._parseColor(gradient[s]));

            for (let i = 0; i < pixels.length; i += 4) {
                const alpha = pixels[i + 3] / 255;
                if (alpha < 0.01) continue;

                const color = this._interpolateColor(stops, colors, alpha);
                pixels[i] = color[0];
                pixels[i + 1] = color[1];
                pixels[i + 2] = color[2];
                pixels[i + 3] = Math.floor(alpha * 255);
            }

            ctx.putImageData(imageData, 0, 0);
        }

        _parseColor(colorStr) {
            const canvas = document.createElement('canvas');
            canvas.width = 1;
            canvas.height = 1;
            const ctx = canvas.getContext('2d');
            ctx.fillStyle = colorStr;
            ctx.fillRect(0, 0, 1, 1);
            const data = ctx.getImageData(0, 0, 1, 1).data;
            return [data[0], data[1], data[2]];
        }

        _interpolateColor(stops, colors, value) {
            if (value <= stops[0]) return colors[0];
            if (value >= stops[stops.length - 1]) return colors[stops.length - 1];

            for (let i = 1; i < stops.length; i++) {
                if (value <= stops[i]) {
                    const t = (value - stops[i - 1]) / (stops[i] - stops[i - 1]);
                    const c1 = colors[i - 1];
                    const c2 = colors[i];
                    return [
                        Math.floor(c1[0] + (c2[0] - c1[0]) * t),
                        Math.floor(c1[1] + (c2[1] - c1[1]) * t),
                        Math.floor(c1[2] + (c2[2] - c1[2]) * t)
                    ];
                }
            }
            return colors[colors.length - 1];
        }

        destroy() {
            if (this._gl) {
                if (this._pointsBuffer) this._gl.deleteBuffer(this._pointsBuffer);
                if (this._indexBuffer) this._gl.deleteBuffer(this._indexBuffer);
                if (this._colorTexture) this._gl.deleteTexture(this._colorTexture);
                if (this._program) this._gl.deleteProgram(this._program);
            }
            this._points = [];
            this._gl = null;
            this._program = null;
        }
    }

    window.WebGLHeatmap = WebGLHeatmap;
})();
