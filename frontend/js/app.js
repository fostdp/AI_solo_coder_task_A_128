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
            this.updateSeason();
        });

        document.getElementById('planBtn').addEventListener('click', () => this.planRoute());

        document.getElementById('analyzeBtn').addEventListener('click', () => this.analyzeRisk());

        document.getElementById('startRoute').addEventListener?.('change', () => {
            this.updateEndRouteOptions();
        });

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
            if (e.target.checked) {
                this.loadSandstormHeatmap();
            } else {
                SilkRoadMap.hideSandstormHeatmap();
            }
        });
        document.getElementById('layerTempHeatmap').addEventListener('change', (e) => {
            if (e.target.checked) {
                this.loadTemperatureHeatmap();
            } else {
                SilkRoadMap.hideTemperatureHeatmap();
            }
        });

        document.getElementById('stationSelect').addEventListener('change', (e) => {
            this.showStationWeather(e.target.value);
        });

        document.getElementById('riskRoute').addEventListener('change', (e) => {
            if (e.target.value) {
                this.analyzeRisk();
            }
        });
    },

    async loadInitialData() {
        try {
            this.routes = await API.getRoutes();
            this.populateRouteSelects();

            this.weatherStations = await API.getStations();
            this.populateStationSelect();

            this.caravans = await API.getCaravans();
            SilkRoadMap.loadCaravans(this.caravans);
            this.renderCaravanList();

            this.alerts = await API.getAlerts();
            this.renderAlerts();
            this.updateStats();

            const allWaypoints = [];
            for (const route of this.routes) {
                const wps = await API.getRouteWaypoints(route.id);
                allWaypoints.push(...wps);
            }
            this.waypoints = allWaypoints;
            SilkRoadMap.loadWaypoints(allWaypoints);

            const routeDTOs = this.routes.map(r => ({
                ...r,
                coordinates: this.generateRouteCoordinates(r.id)
            }));
            SilkRoadMap.loadRoutes(routeDTOs);

        } catch (error) {
            console.error('加载初始数据失败:', error);
            this.useMockData();
        }
    },

    generateRouteCoordinates(routeId) {
        const routeCoords = {
            1: [[108.94, 34.26], [106.16, 34.73], [103.83, 36.06], [102.64, 37.43], [100.45, 38.93], [97.14, 39.73], [94.66, 40.14]],
            2: [[94.66, 40.14], [92.24, 40.51], [90.18, 40.52], [89.55, 40.51]],
            3: [[89.55, 40.51], [87.31, 40.53], [85.54, 39.48], [83.13, 38.32], [81.47, 37.21], [80.05, 36.95]],
            4: [[80.05, 36.95], [78.38, 37.12], [77.24, 38.17], [76.87, 39.42]],
            5: [[76.87, 39.42], [75.99, 39.47], [75.23, 39.72], [74.87, 40.12]],
            6: [[75.99, 39.47], [73.75, 39.63], [71.67, 39.83], [69.28, 40.11], [66.96, 39.65]],
            7: [[94.66, 40.14], [95.01, 41.73], [93.51, 42.83]],
            8: [[93.51, 42.83], [91.62, 42.82], [89.18, 43.78], [88.23, 44.01]],
            9: [[88.23, 44.01], [86.18, 44.28], [82.61, 43.82], [80.03, 43.27], [77.03, 42.84], [74.58, 42.78]],
            10: [[66.96, 39.65], [64.43, 39.65], [61.83, 36.30], [58.35, 36.28], [54.38, 36.68], [51.42, 35.69]]
        };
        return routeCoords[routeId] || [];
    },

    useMockData() {
        this.routes = [
            { id: 1, name: '长安-敦煌主线', nameEn: 'Changan-Dunhuang', totalDistanceKm: 1800, difficultyLevel: 'MODERATE' },
            { id: 2, name: '敦煌-楼兰道', nameEn: 'Dunhuang-Loulan', totalDistanceKm: 500, difficultyLevel: 'HARD' }
        ];
        this.populateRouteSelects();

        this.caravans = [
            { caravanId: 1, name: '西行商队甲', status: 'EN_ROUTE', lng: 103.83, lat: 36.06, speedKmh: 5.0, waterSupplyLiters: 2500, waterRemainingDays: 10.4, foodSupplyDays: 30, cargoType: '丝绸' },
            { caravanId: 2, name: '西域商队乙', status: 'RESTING', lng: 85.54, lat: 39.48, speedKmh: 4.5, waterSupplyLiters: 1200, waterRemainingDays: 5, foodSupplyDays: 25, cargoType: '玉石' }
        ];
        SilkRoadMap.loadCaravans(this.caravans);
        this.renderCaravanList();
        this.updateStats();
    },

    populateRouteSelects() {
        const startSelect = document.getElementById('startRoute');
        const endSelect = document.getElementById('endRoute');
        const riskSelect = document.getElementById('riskRoute');

        const options = this.routes.map(r => `<option value="${r.id}">${r.name}</option>`).join('');

        if (startSelect) startSelect.innerHTML = '<option value="">选择起点</option>' + options;
        if (endSelect) endSelect.innerHTML = '<option value="">选择终点</option>' + options;
        if (riskSelect) riskSelect.innerHTML = '<option value="">选择线路</option>' + options;
    },

    populateStationSelect() {
        const select = document.getElementById('stationSelect');
        if (!select) return;

        select.innerHTML = '<option value="">选择气象站</option>' +
            this.weatherStations.map(s => `<option value="${s.id}">${s.name}</option>`).join('');
    },

    updateEndRouteOptions() {
    },

    async planRoute() {
        const startId = document.getElementById('startRoute').value;
        const endId = document.getElementById('endRoute').value;
        const speed = parseFloat(document.getElementById('caravanSpeed').value);
        const preferOasis = document.getElementById('preferOasis').checked;

        if (!startId || !endId) {
            alert('请选择起点和终点');
            return;
        }

        const startRoute = this.routes.find(r => r.id == startId);
        const endRoute = this.routes.find(r => r.id == endId);

        const startCoords = this.getRouteFirstPoint(startId);
        const endCoords = this.getRouteLastPoint(endId);

        if (!startCoords || !endCoords) return;

        const btn = document.getElementById('planBtn');
        btn.textContent = '规划中...';
        btn.disabled = true;

        try {
            const result = await API.planPath({
                startLng: startCoords[0],
                startLat: startCoords[1],
                endLng: endCoords[0],
                endLat: endCoords[1],
                season: this.currentSeason,
                caravanSpeed: speed,
                preferOasis: preferOasis,
                weightPenalty: 1.0
            });

            SilkRoadMap.showPlannedPath(result.pathPoints);
            this.renderPathResult(result);
        } catch (error) {
            console.error('路径规划失败:', error);
            this.renderMockPathResult(startCoords, endCoords);
        } finally {
            btn.textContent = '规划最优路径';
            btn.disabled = false;
        }
    },

    getRouteFirstPoint(routeId) {
        const coords = this.generateRouteCoordinates(routeId);
        return coords.length > 0 ? coords[0] : null;
    },

    getRouteLastPoint(routeId) {
        const coords = this.generateRouteCoordinates(routeId);
        return coords.length > 0 ? coords[coords.length - 1] : null;
    },

    renderPathResult(result) {
        const div = document.getElementById('pathResult');
        div.classList.remove('hidden');

        div.innerHTML = `
            <h3>路径规划结果</h3>
            <div class="stat-row">
                <span class="label">总距离:</span>
                <span class="value">${result.totalDistanceKm.toFixed(1)} km</span>
            </div>
            <div class="stat-row">
                <span class="label">预计时间:</span>
                <span class="value">${result.estimatedHours.toFixed(1)} 小时</span>
            </div>
            <div class="stat-row">
                <span class="label">风险等级:</span>
                <span class="value"><span class="risk-level ${result.riskLevel}">${this.getRiskLabel(result.riskLevel)}</span></span>
            </div>
            <div class="stat-row">
                <span class="label">风险评分:</span>
                <span class="value">${(result.totalRiskScore * 100).toFixed(1)}%</span>
            </div>
            <div class="stat-row">
                <span class="label">海拔提升:</span>
                <span class="value">${result.elevationGainM.toFixed(0)} m</span>
            </div>
            <div class="stat-row">
                <span class="label">需水量:</span>
                <span class="value">${result.waterRequiredLiters.toFixed(0)} L</span>
            </div>
            <div class="stat-row">
                <span class="label">计算耗时:</span>
                <span class="value">${result.computationTimeMs} ms</span>
            </div>
        `;
    },

    renderMockPathResult(start, end) {
        const div = document.getElementById('pathResult');
        div.classList.remove('hidden');

        const distance = Math.round(Math.sqrt(Math.pow(end[0]-start[0], 2) + Math.pow(end[1]-start[1], 2)) * 111);
        const hours = (distance / 5).toFixed(1);

        div.innerHTML = `
            <h3>路径规划结果（模拟）</h3>
            <div class="stat-row">
                <span class="label">总距离:</span>
                <span class="value">${distance} km</span>
            </div>
            <div class="stat-row">
                <span class="label">预计时间:</span>
                <span class="value">${hours} 小时</span>
            </div>
            <div class="stat-row">
                <span class="label">风险等级:</span>
                <span class="value"><span class="risk-level MODERATE">中等</span></span>
            </div>
            <p style="color:#888;font-size:0.8rem;margin-top:8px;">* 连接后端后可获取真实规划结果</p>
        `;
    },

    getRiskLabel(level) {
        const labels = { 'LOW': '低', 'MODERATE': '中等', 'HIGH': '高', 'EXTREME': '极高' };
        return labels[level] || level;
    },

    async analyzeRisk() {
        const routeId = document.getElementById('riskRoute').value;
        if (!routeId) return;

        const btn = document.getElementById('analyzeBtn');
        btn.textContent = '分析中...';
        btn.disabled = true;

        try {
            const analysis = await API.getRouteRisk(routeId, this.currentSeason);
            this.renderRiskAnalysis(analysis);
        } catch (error) {
            console.error('风险分析失败:', error);
            this.renderMockRiskAnalysis();
        } finally {
            btn.textContent = '分析风险';
            btn.disabled = false;
        }
    },

    renderRiskAnalysis(analysis) {
        const div = document.getElementById('riskAnalysis');
        div.classList.remove('hidden');

        div.innerHTML = `
            <div class="risk-header">
                <span class="risk-score">${(analysis.overallRiskScore * 100).toFixed(0)}%</span>
                <span class="risk-level ${analysis.riskLevel}">${this.getRiskLabel(analysis.riskLevel)}</span>
            </div>
            <div class="risk-section">
                <h4>气温情况</h4>
                <div class="stat-row"><span class="label">平均:</span><span class="value">${analysis.avgTemperature?.toFixed(1) || '-'}°C</span></div>
                <div class="stat-row"><span class="label">最高:</span><span class="value">${analysis.maxTemperature?.toFixed(1) || '-'}°C</span></div>
                <div class="stat-row"><span class="label">最低:</span><span class="value">${analysis.minTemperature?.toFixed(1) || '-'}°C</span></div>
            </div>
            <div class="risk-section">
                <h4>气象风险</h4>
                <div class="stat-row"><span class="label">沙尘暴概率:</span><span class="value">${(analysis.sandstormProbability * 100).toFixed(0)}%</span></div>
                <div class="stat-row"><span class="label">水源可用度:</span><span class="value">${(analysis.waterAvailabilityScore * 100).toFixed(0)}%</span></div>
                <div class="stat-row"><span class="label">推荐行程:</span><span class="value">${analysis.recommendedTravelDays || '-'} 天</span></div>
            </div>
            ${analysis.riskFactors?.length ? `
            <div class="risk-section">
                <h4>⚠️ 风险因素</h4>
                <ul>${analysis.riskFactors.map(f => `<li>${f}</li>`).join('')}</ul>
            </div>` : ''}
            ${analysis.recommendations?.length ? `
            <div class="risk-section">
                <h4>💡 建议</h4>
                <ul>${analysis.recommendations.map(r => `<li>${r}</li>`).join('')}</ul>
            </div>` : ''}
        `;
    },

    renderMockRiskAnalysis() {
        const div = document.getElementById('riskAnalysis');
        div.classList.remove('hidden');

        div.innerHTML = `
            <div class="risk-header">
                <span class="risk-score">45%</span>
                <span class="risk-level MODERATE">中等</span>
            </div>
            <div class="risk-section">
                <h4>气温情况</h4>
                <div class="stat-row"><span class="label">平均:</span><span class="value">15°C</span></div>
                <div class="stat-row"><span class="label">最高:</span><span class="value">30°C</span></div>
                <div class="stat-row"><span class="label">最低:</span><span class="value">0°C</span></div>
            </div>
            <div class="risk-section">
                <h4>气象风险</h4>
                <div class="stat-row"><span class="label">沙尘暴概率:</span><span class="value">25%</span></div>
                <div class="stat-row"><span class="label">水源可用度:</span><span class="value">60%</span></div>
            </div>
            <div class="risk-section">
                <h4>💡 建议</h4>
                <ul>
                    <li>携带防沙装备</li>
                    <li>储备充足饮用水</li>
                </ul>
            </div>
            <p style="color:#888;font-size:0.8rem;margin-top:8px;">* 连接后端后可获取真实分析结果</p>
        `;
    },

    async loadSandstormHeatmap() {
        try {
            const data = await API.getSandstormHeatmap();
            SilkRoadMap.showSandstormHeatmap(data);
        } catch (error) {
            console.error('加载沙尘暴热力图失败:', error);
            const mockData = this.generateMockHeatmapData();
            SilkRoadMap.showSandstormHeatmap(mockData);
        }
    },

    async loadTemperatureHeatmap() {
        try {
            const data = await API.getTemperatureHeatmap();
            SilkRoadMap.showTemperatureHeatmap(data);
        } catch (error) {
            console.error('加载温度热力图失败:', error);
            const mockData = this.generateMockHeatmapData(0.6);
            SilkRoadMap.showTemperatureHeatmap(mockData);
        }
    },

    generateMockHeatmapData(baseValue = 0.4) {
        const data = [];
        const stations = [
            [108.94, 34.26], [103.83, 36.06], [100.45, 38.93], [94.66, 40.14],
            [89.55, 40.51], [85.54, 39.48], [80.05, 36.95], [75.99, 39.47],
            [66.96, 39.65], [93.51, 42.83], [88.23, 44.01], [80.03, 43.27]
        ];
        stations.forEach(s => {
            data.push({ lng: s[0], lat: s[1], value: baseValue + Math.random() * 0.4 });
            for (let i = 0; i < 3; i++) {
                data.push({
                    lng: s[0] + (Math.random() - 0.5) * 2,
                    lat: s[1] + (Math.random() - 0.5) * 2,
                    value: baseValue * 0.6 + Math.random() * 0.3
                });
            }
        });
        return data;
    },

    async showStationWeather(stationId) {
        if (!stationId) return;

        const div = document.getElementById('stationWeather');
        try {
            const reports = await API.getStationReports(stationId, 5);
            if (reports.length > 0) {
                const latest = reports[0];
                div.innerHTML = `
                    <div class="weather-grid">
                        <div class="weather-item">
                            <span class="w-value">${latest.temperatureC?.toFixed(1) || '-'}°C</span>
                            <span class="w-label">温度</span>
                        </div>
                        <div class="weather-item">
                            <span class="w-value">${latest.windSpeedKmh?.toFixed(1) || '-'} km/h</span>
                            <span class="w-label">风速</span>
                        </div>
                        <div class="weather-item">
                            <span class="w-value">${latest.humidityPct?.toFixed(0) || '-'}%</span>
                            <span class="w-label">湿度</span>
                        </div>
                        <div class="weather-item">
                            <span class="w-value">${(latest.sandstormProbability * 100).toFixed(0)}%</span>
                            <span class="w-label">沙尘暴概率</span>
                        </div>
                        <div class="weather-item">
                            <span class="w-value">${latest.precipitationMm || 0} mm</span>
                            <span class="w-label">降水</span>
                        </div>
                        <div class="weather-item">
                            <span class="w-value">${latest.visibilityKm?.toFixed(1) || '-'} km</span>
                            <span class="w-label">能见度</span>
                        </div>
                    </div>
                    <p style="color:#888;font-size:0.75rem;margin-top:8px;">
                        数据时间: ${new Date(latest.reportTime).toLocaleString('zh-CN')}
                    </p>
                `;
            }
        } catch (error) {
            const station = this.weatherStations.find(s => s.id == stationId);
            div.innerHTML = `
                <div class="weather-grid">
                    <div class="weather-item">
                        <span class="w-value">${15 + Math.random() * 10}°C</span>
                        <span class="w-label">温度</span>
                    </div>
                    <div class="weather-item">
                        <span class="w-value">${20 + Math.random() * 15} km/h</span>
                        <span class="w-label">风速</span>
                    </div>
                    <div class="weather-item">
                        <span class="w-value">${30 + Math.random() * 20}%</span>
                        <span class="w-label">湿度</span>
                    </div>
                    <div class="weather-item">
                        <span class="w-value">${Math.round(Math.random() * 50)}%</span>
                        <span class="w-label">沙尘暴概率</span>
                    </div>
                </div>
                <p style="color:#888;font-size:0.75rem;margin-top:8px;">* 模拟数据</p>
            `;
        }
    },

    renderAlerts() {
        const list = document.getElementById('alertsList');
        if (!this.alerts || this.alerts.length === 0) {
            list.innerHTML = '<div class="empty-state">暂无告警</div>';
            return;
        }

        list.innerHTML = this.alerts.slice(0, 10).map(alert => `
            <div class="alert-item ${alert.severity?.toLowerCase() || 'moderate'}">
                <div class="alert-type">${this.getAlertTypeLabel(alert.alertType)}</div>
                <div class="alert-message">${alert.message}</div>
                <div class="alert-time">${new Date(alert.triggeredAt).toLocaleString('zh-CN')}</div>
            </div>
        `).join('');
    },

    getAlertTypeLabel(type) {
        const types = {
            'SANDSTORM_WARNING': '🌪️ 沙尘暴预警',
            'HIGH_WIND_WARNING': '💨 大风预警',
            'EXTREME_HEAT_WARNING': '🔥 高温预警',
            'EXTREME_COLD_WARNING': '❄️ 低温预警',
            'WATER_SHORTAGE': '💧 水源不足'
        };
        return types[type] || type;
    },

    renderCaravanList() {
        const list = document.getElementById('caravanList');
        if (!this.caravans || this.caravans.length === 0) {
            list.innerHTML = '<div class="empty-state">暂无驼队</div>';
            return;
        }

        list.innerHTML = this.caravans.map(c => `
            <div class="caravan-item" onclick="App.focusCaravan(${c.caravanId})">
                <div class="c-name">${c.name}</div>
                <span class="c-status ${c.status?.toLowerCase().replace('_', '-') || ''}">${this.getStatusLabel(c.status)}</span>
                <div class="c-info">
                    <span>💧 ${Math.round(c.waterSupplyLiters || 0)}L</span>
                    <span>🐪 ${c.camelCount || 50}</span>
                </div>
                <div class="c-actions">
                    <button onclick="event.stopPropagation(); App.startCaravan(${c.caravanId})">出发</button>
                    <button onclick="event.stopPropagation(); App.stopCaravan(${c.caravanId})">停靠</button>
                </div>
            </div>
        `).join('');
    },

    getStatusLabel(status) {
        const labels = { 'EN_ROUTE': '行进中', 'RESTING': '休整中', 'IDLE': '待命' };
        return labels[status] || status;
    },

    focusCaravan(id) {
        SilkRoadMap.flyToCaravan(id);
    },

    async startCaravan(id) {
        try {
            await API.startCaravan(id);
            const caravan = this.caravans.find(c => c.caravanId === id);
            if (caravan) caravan.status = 'EN_ROUTE';
            this.renderCaravanList();
        } catch (e) {
            console.log('模拟启动驼队', id);
            const caravan = this.caravans.find(c => c.caravanId === id);
            if (caravan) caravan.status = 'EN_ROUTE';
            this.renderCaravanList();
        }
    },

    async stopCaravan(id) {
        try {
            await API.stopCaravan(id);
            const caravan = this.caravans.find(c => c.caravanId === id);
            if (caravan) caravan.status = 'RESTING';
            this.renderCaravanList();
        } catch (e) {
            console.log('模拟停靠驼队', id);
            const caravan = this.caravans.find(c => c.caravanId === id);
            if (caravan) caravan.status = 'RESTING';
            this.renderCaravanList();
        }
    },

    updateStats() {
        document.getElementById('statCaravans').textContent = this.caravans.length;
        document.getElementById('statAlerts').textContent = this.alerts.filter(a => a.isActive).length;
        document.getElementById('statStations').textContent = this.weatherStations.length;
        document.getElementById('statRoutes').textContent = this.routes.length;
    },

    updateSeason() {
        const heatmapChecked = document.getElementById('layerHeatmap')?.checked;
        if (heatmapChecked) {
            this.loadSandstormHeatmap();
        }
        const riskRoute = document.getElementById('riskRoute')?.value;
        if (riskRoute) {
            this.analyzeRisk();
        }
    },

    connectWebSocket() {
        try {
            const socket = new SockJS(CONFIG.WS_URL);
            this.stompClient = Stomp.over(socket);

            this.stompClient.connect({},
                () => {
                    this.updateWsStatus(true);
                    this.stompClient.subscribe('/topic/alerts', (message) => {
                        const data = JSON.parse(message.body);
                        this.handleAlertMessage(data);
                    });
                    this.stompClient.subscribe('/topic/caravans', (message) => {
                        const caravan = JSON.parse(message.body);
                        this.handleCaravanUpdate(caravan);
                    });
                },
                (error) => {
                    console.warn('WebSocket连接失败:', error);
                    this.updateWsStatus(false);
                }
            );
        } catch (error) {
            console.warn('WebSocket初始化失败:', error);
            this.updateWsStatus(false);
        }
    },

    updateWsStatus(connected) {
        const status = document.getElementById('wsStatus');
        const dot = status.querySelector('.dot');
        const text = status.querySelector('span:last-child');
        if (connected) {
            dot.className = 'dot online';
            text.textContent = '已连接';
        } else {
            dot.className = 'dot offline';
            text.textContent = '未连接';
        }
    },

    handleAlertMessage(data) {
        if (data.action === 'NEW' && data.alert) {
            this.alerts.unshift(data.alert);
            this.renderAlerts();
            this.updateStats();
        }
    },

    handleCaravanUpdate(caravan) {
        const index = this.caravans.findIndex(c => c.caravanId === caravan.caravanId);
        if (index >= 0) {
            this.caravans[index] = caravan;
        } else {
            this.caravans.push(caravan);
        }
        SilkRoadMap.updateCaravan(caravan);
        this.renderCaravanList();
    },

    startDataRefresh() {
        setInterval(async () => {
            try {
                const caravans = await API.getCaravans();
                this.caravans = caravans;
                SilkRoadMap.loadCaravans(caravans);
            } catch (e) {
                this.simulateCaravanMovement();
            }
        }, CONFIG.UPDATE_INTERVAL);

        setInterval(async () => {
            try {
                const alerts = await API.getAlerts();
                this.alerts = alerts;
                this.renderAlerts();
                this.updateStats();
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
        this.renderCaravanList();
    },

    startAnimationLoop() {
        const animate = () => {
            SilkRoadMap.animateCaravans();
            requestAnimationFrame(animate);
        };
        animate();
    }
};

document.addEventListener('DOMContentLoaded', () => {
    App.init();
});
