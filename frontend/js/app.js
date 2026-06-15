const App = {
    routes: [],
    waypoints: [],
    caravans: [],
    weatherStations: [],
    alerts: [],
    stompClient: null,
    currentSeason: 'SPRING',

    async init() {
        this.setupEventListeners();
        SilkRoadMap.init();
        await this.loadInitialData();
        this.connectWebSocket();
        this.startDataRefresh();
        this.startAnimationLoop();
    },

    setupEventListeners() {
        document.getElementById('seasonSelect').addEventListener('change', (e) => {
            this.currentSeason = e.target.value;
            RiskPanel.setSeason(this.currentSeason);
            this.updateSeason();
        });

        document.getElementById('planBtn').addEventListener('click', () => this.planRoute());
        document.getElementById('analyzeBtn').addEventListener('click', () => this.analyzeRisk());

        document.getElementById('layerRoutes').addEventListener('change', (e) => {
            SilkRoadMap.toggleLayer('routes', e.target.checked);
        });
        document.getElementById('layerWaypoints').addEventListener('change', (e) => {
            SilkRoadMap.toggleLayer('waypoints', e.target.checked);
        });
        document.getElementById('layerCaravans').addEventListener('change', (e) => {
            SilkRoadMap.toggleLayer('caravans', e.target.checked);
        });
        document.getElementById('layerWeatherStations').addEventListener('change', (e) => {
            SilkRoadMap.toggleLayer('weatherStations', e.target.checked);
        });
        document.getElementById('layerHeatmap').addEventListener('change', (e) => {
            if (e.target.checked) this.loadSandstormHeatmap();
            else SilkRoadMap.hideSandstormHeatmap();
        });
        document.getElementById('layerTempHeatmap').addEventListener('change', (e) => {
            if (e.target.checked) this.loadTemperatureHeatmap();
            else SilkRoadMap.hideTemperatureHeatmap();
        });
        document.getElementById('stationSelect').addEventListener('change', (e) => {
            this.showStationWeather(e.target.value);
        });
        document.getElementById('riskRoute').addEventListener('change', (e) => {
            if (e.target.value) this.analyzeRisk();
        });
    },

    async loadInitialData() {
        try {
            this.routes = await API.getRoutes();
            this.weatherStations = await API.getStations();
            this.caravans = await API.getCaravans();
            this.alerts = await API.getAlerts();

            RiskPanel.init(this.routes, this.weatherStations);

            SilkRoadMap.loadCaravans(this.caravans);
            RiskPanel.renderCaravanList(this.caravans);
            RiskPanel.renderAlerts(this.alerts);
            RiskPanel.updateStats(this.caravans.length, this.alerts.filter(a => a.isActive).length,
                    this.weatherStations.length, this.routes.length);

            const allWaypoints = [];
            for (const route of this.routes) {
                const wps = await API.getRouteWaypoints(route.id);
                allWaypoints.push(...wps);
            }
            this.waypoints = allWaypoints;
            SilkRoadMap.loadWaypoints(allWaypoints);

            const routeDTOs = this.routes.map(r => ({
                ...r, coordinates: RiskPanel.generateRouteCoordinates(r.id)
            }));
            SilkRoadMap.loadRoutes(routeDTOs);

        } catch (error) {
            console.error('加载初始数据失败:', error);
            this.useMockData();
        }
    },

    useMockData() {
        this.routes = [
            { id: 1, name: '长安-敦煌主线', totalDistanceKm: 1800, difficultyLevel: 'MODERATE' },
            { id: 2, name: '敦煌-楼兰道', totalDistanceKm: 500, difficultyLevel: 'HARD' }
        ];
        this.caravans = [
            { caravanId: 1, name: '西行商队甲', status: 'EN_ROUTE', lng: 103.83, lat: 36.06,
              speedKmh: 5.0, waterSupplyLiters: 2500, waterRemainingDays: 10.4, foodSupplyDays: 30 },
            { caravanId: 2, name: '西域商队乙', status: 'RESTING', lng: 85.54, lat: 39.48,
              speedKmh: 4.5, waterSupplyLiters: 1200, waterRemainingDays: 5, foodSupplyDays: 25 }
        ];
        RiskPanel.init(this.routes, []);
        SilkRoadMap.loadCaravans(this.caravans);
        RiskPanel.renderCaravanList(this.caravans);
        RiskPanel.updateStats(2, 0, 0, 2);
    },

    async planRoute() {
        const result = await RiskPanel.planRoute();
        if (result) {
            SilkRoadMap.showPlannedPath(result.pathPoints);
            RiskPanel.renderPathResult(result);
        }
    },

    async analyzeRisk() {
        const analysis = await RiskPanel.analyzeRisk();
        if (analysis) RiskPanel.renderRiskAnalysis(analysis);
    },

    async showStationWeather(stationId) {
        const latest = await RiskPanel.showStationWeather(stationId);
        if (latest) RiskPanel.renderStationWeather(latest);
    },

    async loadSandstormHeatmap() {
        try {
            const data = await API.getSandstormHeatmap();
            SilkRoadMap.showSandstormHeatmap(data);
        } catch (error) {
            SilkRoadMap.showSandstormHeatmap(this.generateMockHeatmapData());
        }
    },

    async loadTemperatureHeatmap() {
        try {
            const data = await API.getTemperatureHeatmap();
            SilkRoadMap.showTemperatureHeatmap(data);
        } catch (error) {
            SilkRoadMap.showTemperatureHeatmap(this.generateMockHeatmapData(0.6));
        }
    },

    generateMockHeatmapData(baseValue = 0.4) {
        const data = [];
        const stations = [
            [108.94,34.26],[103.83,36.06],[100.45,38.93],[94.66,40.14],
            [89.55,40.51],[85.54,39.48],[80.05,36.95],[75.99,39.47],
            [66.96,39.65],[93.51,42.83],[88.23,44.01],[80.03,43.27]
        ];
        stations.forEach(s => {
            data.push({ lng: s[0], lat: s[1], value: baseValue + Math.random() * 0.4 });
            for (let i = 0; i < 3; i++) {
                data.push({ lng: s[0] + (Math.random()-0.5)*2, lat: s[1] + (Math.random()-0.5)*2,
                             value: baseValue * 0.6 + Math.random() * 0.3 });
            }
        });
        return data;
    },

    focusCaravan(id) {
        SilkRoadMap.flyToCaravan(id);
    },

    async startCaravan(id) {
        try {
            await API.startCaravan(id);
        } catch (e) { console.log('模拟启动驼队', id); }
        const caravan = this.caravans.find(c => c.caravanId === id);
        if (caravan) caravan.status = 'EN_ROUTE';
        RiskPanel.renderCaravanList(this.caravans);
    },

    async stopCaravan(id) {
        try {
            await API.stopCaravan(id);
        } catch (e) { console.log('模拟停靠驼队', id); }
        const caravan = this.caravans.find(c => c.caravanId === id);
        if (caravan) caravan.status = 'RESTING';
        RiskPanel.renderCaravanList(this.caravans);
    },

    updateSeason() {
        if (document.getElementById('layerHeatmap')?.checked) this.loadSandstormHeatmap();
        if (document.getElementById('riskRoute')?.value) this.analyzeRisk();
    },

    connectWebSocket() {
        try {
            const socket = new SockJS(CONFIG.WS_URL);
            this.stompClient = Stomp.over(socket);
            this.stompClient.connect({},
                () => {
                    this.updateWsStatus(true);
                    this.stompClient.subscribe('/topic/alerts', (msg) => {
                        this.handleAlertMessage(JSON.parse(msg.body));
                    });
                    this.stompClient.subscribe('/topic/caravans', (msg) => {
                        this.handleCaravanUpdate(JSON.parse(msg.body));
                    });
                },
                (error) => {
                    console.warn('WebSocket连接失败:', error);
                    this.updateWsStatus(false);
                }
            );
        } catch (error) {
            this.updateWsStatus(false);
        }
    },

    updateWsStatus(connected) {
        const status = document.getElementById('wsStatus');
        const dot = status.querySelector('.dot');
        const text = status.querySelector('span:last-child');
        dot.className = connected ? 'dot online' : 'dot offline';
        text.textContent = connected ? '已连接' : '未连接';
    },

    handleAlertMessage(data) {
        if (data.action === 'NEW' && data.alert) {
            this.alerts.unshift(data.alert);
            RiskPanel.renderAlerts(this.alerts);
            RiskPanel.updateStats(this.caravans.length, this.alerts.filter(a => a.isActive).length,
                    this.weatherStations.length, this.routes.length);
        }
    },

    handleCaravanUpdate(caravan) {
        const idx = this.caravans.findIndex(c => c.caravanId === caravan.caravanId);
        if (idx >= 0) this.caravans[idx] = caravan;
        else this.caravans.push(caravan);
        SilkRoadMap.updateCaravan(caravan);
        RiskPanel.renderCaravanList(this.caravans);
    },

    startDataRefresh() {
        setInterval(async () => {
            try {
                this.caravans = await API.getCaravans();
                SilkRoadMap.loadCaravans(this.caravans);
                RiskPanel.renderCaravanList(this.caravans);
            } catch (e) {
                this.simulateCaravanMovement();
            }
        }, CONFIG.UPDATE_INTERVAL);

        setInterval(async () => {
            try {
                this.alerts = await API.getAlerts();
                RiskPanel.renderAlerts(this.alerts);
            } catch (e) {}
        }, CONFIG.WEATHER_UPDATE_INTERVAL);
    },

    simulateCaravanMovement() {
        this.caravans.forEach(c => {
            if (c.status === 'EN_ROUTE' && c.lng && c.lat) {
                c.lng += (Math.random() - 0.5) * 0.02;
                c.lat += (Math.random() - 0.5) * 0.01;
                c.waterSupplyLiters = Math.max(0, (c.waterSupplyLiters || 2000) - 0.5);
            }
        });
        SilkRoadMap.loadCaravans(this.caravans);
        RiskPanel.renderCaravanList(this.caravans);
    },

    startAnimationLoop() {
        const animate = () => {
            SilkRoadMap.animateCaravans();
            requestAnimationFrame(animate);
        };
        animate();
    }
};

document.addEventListener('DOMContentLoaded', () => App.init());
