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
        const historicalStyle = {
            color: '#e94560',
            weight: 3,
            opacity: 0.8,
            dashArray: '10, 8',
            lineCap: 'round'
        };
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
        return `
            <h3>${route.name}</h3>
            <p><strong>英文名:</strong> ${route.nameEn || '-'}</p>
            <p><strong>总长度:</strong> ${route.totalDistanceKm || '-'} km</p>
            <p><strong>难度等级:</strong> ${route.difficultyLevel || '-'}</p>
            <p><strong>驿站数量:</strong> ${route.waypointCount || 0}</p>
            ${route.description ? `<p>${route.description}</p>` : ''}
        `;
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
        return `
            <h3>${wp.name}</h3>
            <p><strong>英文名:</strong> ${wp.nameEn || '-'}</p>
            <p><strong>海拔:</strong> ${wp.elevationM || '-'} m</p>
            <p><strong>顺序:</strong> 第 ${wp.waypointOrder} 站</p>
            <p><strong>绿洲:</strong> ${wp.isOasis ? '是 ✓' : '否'}</p>
            <p><strong>水源:</strong> ${wp.waterAvailable ? '有 ✓' : '无'}</p>
            <p><strong>补给站:</strong> ${wp.supplyStation ? '是 ✓' : '否'}</p>
            ${wp.description ? `<p>${wp.description}</p>` : ''}
        `;
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
        return `
            <h3>${caravan.name}</h3>
            <p><strong>状态:</strong> ${caravan.status}</p>
            <p><strong>速度:</strong> ${caravan.speedKmh || '-'} km/h</p>
            <p><strong>货物:</strong> ${caravan.cargoType || '-'}</p>
            <p><strong>水量:</strong> ${Math.round(caravan.waterSupplyLiters || 0)} L</p>
            <p><strong>余水天数:</strong> ${caravan.waterRemainingDays?.toFixed(1) || '-'} 天</p>
            <p><strong>食物:</strong> ${caravan.foodSupplyDays?.toFixed(1) || '-'} 天</p>
        `;
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
                .bindPopup(this.createStationPopup(station));

            this.layers.weatherStations[station.id] = marker;
        });
    },

    createStationPopup(station) {
        return `
            <h3>${station.name}</h3>
            <p><strong>编号:</strong> ${station.stationCode}</p>
            <p><strong>海拔:</strong> ${station.elevationM || '-'} m</p>
            <p><strong>覆盖半径:</strong> ${station.coverageRadiusKm || '-'} km</p>
            <p><strong>状态:</strong> ${station.isActive ? '运行中' : '已停用'}</p>
        `;
    },

    showSandstormHeatmap(heatData) {
        this.hideSandstormHeatmap();

        const points = heatData.map(p => [p.lat, p.lng, p.value]);
        this.layers.sandstormHeat = L.heatLayer(points, {
            radius: 40,
            blur: 25,
            maxZoom: 10,
            max: 1.0,
            gradient: {
                0.0: '#22c55e',
                0.4: '#eab308',
                0.7: '#ef4444',
                1.0: '#7c2d12'
            }
        }).addTo(this.map);
    },

    hideSandstormHeatmap() {
        if (this.layers.sandstormHeat) {
            this.map.removeLayer(this.layers.sandstormHeat);
            this.layers.sandstormHeat = null;
        }
    },

    showTemperatureHeatmap(heatData) {
        this.hideTemperatureHeatmap();

        const points = heatData.map(p => [p.lat, p.lng, p.value]);
        this.layers.tempHeat = L.heatLayer(points, {
            radius: 35,
            blur: 20,
            maxZoom: 10,
            max: 1.0,
            gradient: {
                0.0: '#3b82f6',
                0.3: '#22c55e',
                0.5: '#eab308',
                0.8: '#ef4444',
                1.0: '#7c2d12'
            }
        }).addTo(this.map);
    },

    hideTemperatureHeatmap() {
        if (this.layers.tempHeat) {
            this.map.removeLayer(this.layers.tempHeat);
            this.layers.tempHeat = null;
        }
    },

    showPlannedPath(pathPoints) {
        this.hidePlannedPath();

        const latlngs = pathPoints.map(p => [p[1], p[0]]);
        this.layers.plannedPath = L.polyline(latlngs, {
            color: '#4ade80',
            weight: 5,
            opacity: 0.9,
            dashArray: '15, 10',
            lineCap: 'round'
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
        const layerMap = {
            routes: this.layers.routes,
            waypoints: this.layers.waypoints,
            caravans: this.layers.caravans,
            weatherStations: this.layers.weatherStations,
            waterSources: this.layers.waterSources
        };

        const layers = layerMap[layerName];
        if (!layers) return;

        Object.values(layers).forEach(layer => {
            if (visible) {
                layer.addTo(this.map);
            } else {
                this.map.removeLayer(layer);
            }
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
