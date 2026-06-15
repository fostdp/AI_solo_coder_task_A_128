const SilkRoadMap = {
    map: null,
    layers: {
        routes: {},
        waypoints: {},
        caravans: {},
        weatherStations: {},
        waterSources: {},
        sandstormHeat: null,
        tempHeat: null,
        plannedPath: null
    },
    caravansData: [],
    _heatmapLayers: {
        sandstorm: null,
        temperature: null
    },

    init() {
        this.map = L.map('map', {
            center: CONFIG.MAP_CENTER,
            zoom: CONFIG.MAP_ZOOM,
            minZoom: CONFIG.MAP_MIN_ZOOM,
            maxZoom: CONFIG.MAP_MAX_ZOOM,
            zoomControl: true,
            attributionControl: false
        });

        L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
            maxZoom: 19,
            subdomains: 'abcd'
        }).addTo(this.map);

        L.control.scale({
            imperial: false,
            metric: true,
            position: 'bottomright'
        }).addTo(this.map);

        this.addHistoricalRouteLayer();
        this.setupCanvasRenderer();
        return this;
    },

    addHistoricalRouteLayer() {
    },

    setupCanvasRenderer() {
        this.canvasLayer = L.canvas({ padding: 0.5 });
    },

    loadRoutes(routes) {
        routes.forEach((route, index) => {
            const color = CONFIG.ROUTE_COLORS[index % CONFIG.ROUTE_COLORS.length];
            const latlngs = [];
            if (route.coordinates && route.coordinates.length > 0) {
                route.coordinates.forEach(coord => {
                    latlngs.push([coord[1], coord[0]]);
                });
            }
            if (latlngs.length >= 2) {
                const polyline = L.polyline(latlngs, {
                    color: color,
                    weight: 3,
                    opacity: 0.8,
                    lineCap: 'round',
                    lineJoin: 'round'
                }).bindPopup(this.createRoutePopup(route));
                this.layers.routes[route.id] = polyline;
                polyline.addTo(this.map);
            }
        });
    },

    createRoutePopup(route) {
        return `<h3>${route.name}</h3>
            <p><strong>总长度:</strong> ${route.totalDistanceKm || '-'} km</p>
            <p><strong>难度等级:</strong> ${route.difficultyLevel || '-'}</p>`;
    },

    loadWaypoints(waypoints) {
        waypoints.forEach(wp => {
            const iconCanvas = wp.isOasis
                ? CanvasIcons.createOasisIcon(28)
                : CanvasIcons.createWaypointIcon(22, wp.supplyStation);
            const icon = L.divIcon({
                className: 'custom-marker',
                html: `<div style="width:${iconCanvas.width}px;height:${iconCanvas.height}px;">
                        <canvas width="${iconCanvas.width}" height="${iconCanvas.height}"
                                style="width:100%;height:100%;"></canvas>
                       </div>`,
                iconSize: [iconCanvas.width, iconCanvas.height],
                iconAnchor: [iconCanvas.width / 2, iconCanvas.height / 2]
            });
            const marker = L.marker([wp.lat, wp.lng], { icon })
                .bindPopup(this.createWaypointPopup(wp));
            this.layers.waypoints[wp.id] = marker;
            marker.addTo(this.map);
            const canvas = marker.getElement().querySelector('canvas');
            if (canvas) {
                const ctx = canvas.getContext('2d');
                ctx.drawImage(iconCanvas, 0, 0);
            }
        });
    },

    createWaypointPopup(wp) {
        return `<h3>${wp.name}</h3>
            <p><strong>海拔:</strong> ${wp.elevationM || '-'} m</p>
            <p><strong>绿洲:</strong> ${wp.isOasis ? '是' : '否'}</p>
            <p><strong>水源:</strong> ${wp.waterAvailable ? '有' : '无'}</p>`;
    },

    loadCaravans(caravans) {
        this.caravansData = caravans;
        caravans.forEach(caravan => {
            if (!caravan.lat || !caravan.lng) return;
            const state = caravan.status === 'EN_ROUTE' ? 'moving' :
                         caravan.status === 'RESTING' ? 'resting' : 'normal';
            const iconCanvas = CanvasIcons.createCaravanIcon(36, 0, state);
            const icon = L.divIcon({
                className: 'caravan-marker',
                html: `<div style="width:${iconCanvas.width}px;height:${iconCanvas.height}px;">
                        <canvas width="${iconCanvas.width}" height="${iconCanvas.height}"
                                style="width:100%;height:100%;"></canvas>
                       </div>`,
                iconSize: [iconCanvas.width, iconCanvas.height],
                iconAnchor: [iconCanvas.width / 2, iconCanvas.height / 2]
            });
            if (this.layers.caravans[caravan.caravanId]) {
                this.layers.caravans[caravan.caravanId].setLatLng([caravan.lat, caravan.lng]);
            } else {
                const marker = L.marker([caravan.lat, caravan.lng], { icon })
                    .bindPopup(this.createCaravanPopup(caravan));
                this.layers.caravans[caravan.caravanId] = marker;
                marker.addTo(this.map);
                const canvas = marker.getElement().querySelector('canvas');
                if (canvas) {
                    const ctx = canvas.getContext('2d');
                    ctx.drawImage(iconCanvas, 0, 0);
                }
            }
        });
    },

    updateCaravan(caravan) {
        const marker = this.layers.caravans[caravan.caravanId];
        if (marker && caravan.lat && caravan.lng) {
            marker.setLatLng([caravan.lat, caravan.lng]);
            marker.setPopupContent(this.createCaravanPopup(caravan));
        }
    },

    createCaravanPopup(caravan) {
        return `<h3>${caravan.name}</h3>
            <p><strong>状态:</strong> ${caravan.status}</p>
            <p><strong>水量:</strong> ${Math.round(caravan.waterSupplyLiters || 0)} L</p>
            <p><strong>余水天数:</strong> ${caravan.waterRemainingDays?.toFixed(1) || '-'} 天</p>`;
    },

    loadWeatherStations(stations) {
        stations.forEach(station => {
            const iconCanvas = CanvasIcons.createWeatherStationIcon(24, station.isActive);
            const icon = L.divIcon({
                className: 'station-marker',
                html: `<div style="width:${iconCanvas.width}px;height:${iconCanvas.height}px;">
                        <canvas width="${iconCanvas.width}" height="${iconCanvas.height}"
                                style="width:100%;height:100%;"></canvas>
                       </div>`,
                iconSize: [iconCanvas.width, iconCanvas.height],
                iconAnchor: [iconCanvas.width / 2, iconCanvas.height / 2]
            });
            const lat = station.geom ? station.geom.coordinates[1] : station.lat;
            const lng = station.geom ? station.geom.coordinates[0] : station.lng;
            const marker = L.marker([lat, lng], { icon })
                .bindPopup(`<h3>${station.name}</h3><p><strong>海拔:</strong> ${station.elevationM || '-'} m</p>`);
            this.layers.weatherStations[station.id] = marker;
        });
    },

    showSandstormHeatmap(heatData) {
        this.hideSandstormHeatmap();
        this._heatmapLayers.sandstorm = this._createWebGLHeatmapLayer({
            radius: 40,
            blur: 25,
            maxOpacity: 0.75,
            gradient: { 0.0: '#22c55e', 0.4: '#eab308', 0.7: '#ef4444', 1.0: '#7c2d12' }
        }, heatData);
        this._heatmapLayers.sandstorm.addTo(this.map);
    },

    hideSandstormHeatmap() {
        if (this._heatmapLayers.sandstorm) {
            this.map.removeLayer(this._heatmapLayers.sandstorm);
            this._heatmapLayers.sandstorm = null;
        }
    },

    showTemperatureHeatmap(heatData) {
        this.hideTemperatureHeatmap();
        this._heatmapLayers.temperature = this._createWebGLHeatmapLayer({
            radius: 35,
            blur: 20,
            maxOpacity: 0.7,
            gradient: { 0.0: '#3b82f6', 0.3: '#22c55e', 0.5: '#eab308', 0.8: '#ef4444', 1.0: '#7c2d12' }
        }, heatData);
        this._heatmapLayers.temperature.addTo(this.map);
    },

    hideTemperatureHeatmap() {
        if (this._heatmapLayers.temperature) {
            this.map.removeLayer(this._heatmapLayers.temperature);
            this._heatmapLayers.temperature = null;
        }
    },

    _createWebGLHeatmapLayer(options, data) {
        const map = this.map;
        let heatmap = null;
        let canvas = null;
        let pane = null;
        let frameId = null;
        let needsRender = true;

        const layer = L.Layer.extend({
            onAdd: function(map) {
                pane = map.getPane('overlayPane');
                canvas = document.createElement('canvas');
                canvas.style.position = 'absolute';
                canvas.style.top = '0';
                canvas.style.left = '0';
                canvas.style.pointerEvents = 'none';
                canvas.style.zIndex = '500';
                pane.appendChild(canvas);
                heatmap = new WebGLHeatmap(Object.assign({ useDevicePixelRatio: true, maxPoints: 5000 }, options));
                this._updateSize();
                needsRender = true;
                this._scheduleRender();
                map.on('move', this._scheduleRender, this);
                map.on('zoom', this._scheduleRender, this);
                map.on('resize', this._updateSize, this);
                map.on('moveend', this._scheduleRender, this);
                map.on('zoomend', this._scheduleRender, this);
            },
            onRemove: function(map) {
                if (frameId) { cancelAnimationFrame(frameId); frameId = null; }
                if (canvas && pane) pane.removeChild(canvas);
                if (heatmap) { heatmap.destroy(); heatmap = null; }
                map.off('move', this._scheduleRender, this);
                map.off('zoom', this._scheduleRender, this);
                map.off('resize', this._updateSize, this);
                map.off('moveend', this._scheduleRender, this);
                map.off('zoomend', this._scheduleRender, this);
            },
            _updateSize: function() {
                const size = map.getSize();
                if (heatmap) heatmap.setSize(size.x, size.y);
                if (canvas) { canvas.style.width = size.x + 'px'; canvas.style.height = size.y + 'px'; }
                needsRender = true;
                this._scheduleRender();
            },
            _scheduleRender: function() {
                if (frameId) return;
                frameId = requestAnimationFrame(() => { frameId = null; this._render(); });
            },
            _render: function() {
                if (!heatmap || !canvas) return;
                const size = map.getSize();
                const bounds = map.getBounds();
                const ne = bounds.getNorthEast();
                const sw = bounds.getSouthWest();
                const topLeft = map.latLngToContainerPoint([ne.lat, sw.lng]);
                canvas.style.transform = `translate3d(${topLeft.x}px, ${topLeft.y}px, 0)`;

                if (needsRender) {
                    const screenPoints = [];
                    for (let i = 0; i < data.length; i++) {
                        const p = data[i];
                        if (!p || p.lng == null || p.lat == null) continue;
                        if (p.lng < sw.lng || p.lng > ne.lng || p.lat < sw.lat || p.lat > ne.lat) continue;
                        const point = map.latLngToContainerPoint([p.lat, p.lng]);
                        const localX = point.x - topLeft.x;
                        const localY = point.y - topLeft.y;
                        if (localX >= -100 && localX <= size.x + 100 && localY >= -100 && localY <= size.y + 100) {
                            screenPoints.push([localX, localY, p.value || 0]);
                        }
                    }
                    heatmap.setData(screenPoints);
                    needsRender = false;
                }
                heatmap.render();
                const heatmapCanvas = heatmap.getCanvas();
                if (heatmapCanvas) {
                    if (canvas.width !== heatmapCanvas.width || canvas.height !== heatmapCanvas.height) {
                        canvas.width = heatmapCanvas.width;
                        canvas.height = heatmapCanvas.height;
                    }
                    const canvasCtx = canvas.getContext('2d');
                    if (canvasCtx) {
                        canvasCtx.clearRect(0, 0, canvas.width, canvas.height);
                        canvasCtx.drawImage(heatmapCanvas, 0, 0);
                    }
                }
            },
            updateData: function(newData) {
                data = newData;
                needsRender = true;
                this._scheduleRender();
            }
        });
        return new layer();
    },

    showPlannedPath(pathPoints) {
        this.hidePlannedPath();
        const latlngs = pathPoints.map(p => [p[1], p[0]]);
        this.layers.plannedPath = L.polyline(latlngs, {
            color: '#4ade80', weight: 5, opacity: 0.9,
            dashArray: '15, 10', lineCap: 'round'
        }).addTo(this.map);
        this.map.fitBounds(this.layers.plannedPath.getBounds(), { padding: [50, 50] });
    },

    hidePlannedPath() {
        if (this.layers.plannedPath) {
            this.map.removeLayer(this.layers.plannedPath);
            this.layers.plannedPath = null;
        }
    },

    toggleLayer(layerName, visible) {
        const layers = this.layers[layerName];
        if (!layers) return;
        Object.values(layers).forEach(layer => {
            if (visible) layer.addTo(this.map);
            else this.map.removeLayer(layer);
        });
    },

    flyToCaravan(caravanId) {
        const caravan = this.caravansData.find(c => c.caravanId === caravanId);
        if (caravan && caravan.lat && caravan.lng) {
            this.map.flyTo([caravan.lat, caravan.lng], 8, { duration: 1.5 });
        }
    },

    animateCaravans() {
        Object.entries(this.layers.caravans).forEach(([id, marker]) => {
            const caravan = this.caravansData.find(c => c.caravanId == id);
            if (!caravan || caravan.status !== 'EN_ROUTE') return;
            const canvas = marker.getElement()?.querySelector('canvas');
            if (!canvas) return;
            const state = caravan.status === 'EN_ROUTE' ? 'moving' : 'normal';
            const iconCanvas = CanvasIcons.createCaravanIcon(36, 0, state);
            const ctx = canvas.getContext('2d');
            ctx.clearRect(0, 0, canvas.width, canvas.height);
            ctx.drawImage(iconCanvas, 0, 0);
        });
    }
};
