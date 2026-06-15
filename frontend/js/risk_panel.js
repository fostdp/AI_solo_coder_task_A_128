const RiskPanel = {
    routes: [],
    weatherStations: [],
    currentSeason: 'SPRING',

    init(routes, weatherStations) {
        this.routes = routes || [];
        this.weatherStations = weatherStations || [];
        this.populateRouteSelects();
        this.populateStationSelect();
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

    setSeason(season) {
        this.currentSeason = season;
    },

    async planRoute() {
        const startId = document.getElementById('startRoute').value;
        const endId = document.getElementById('endRoute').value;
        const speed = parseFloat(document.getElementById('caravanSpeed').value);
        const preferOasis = document.getElementById('preferOasis').checked;

        if (!startId || !endId) { alert('请选择起点和终点'); return null; }

        const startCoords = this.getRouteFirstPoint(startId);
        const endCoords = this.getRouteLastPoint(endId);
        if (!startCoords || !endCoords) return null;

        const btn = document.getElementById('planBtn');
        btn.textContent = '规划中...';
        btn.disabled = true;

        try {
            const result = await API.planPath({
                startLng: startCoords[0], startLat: startCoords[1],
                endLng: endCoords[0], endLat: endCoords[1],
                season: this.currentSeason, caravanSpeed: speed,
                preferOasis: preferOasis, weightPenalty: 1.0
            });
            return result;
        } catch (error) {
            console.error('路径规划失败:', error);
            return null;
        } finally {
            btn.textContent = '规划最优路径';
            btn.disabled = false;
        }
    },

    async analyzeRisk() {
        const routeId = document.getElementById('riskRoute').value;
        if (!routeId) return null;

        const btn = document.getElementById('analyzeBtn');
        btn.textContent = '分析中...';
        btn.disabled = true;

        try {
            const analysis = await API.getRouteRisk(routeId, this.currentSeason);
            return analysis;
        } catch (error) {
            console.error('风险分析失败:', error);
            return null;
        } finally {
            btn.textContent = '分析风险';
            btn.disabled = false;
        }
    },

    async showStationWeather(stationId) {
        if (!stationId) return null;
        try {
            const reports = await API.getStationReports(stationId, 5);
            return reports.length > 0 ? reports[0] : null;
        } catch (error) {
            console.error('获取气象数据失败:', error);
            return null;
        }
    },

    renderPathResult(result) {
        const div = document.getElementById('pathResult');
        if (!result) { div.classList.add('hidden'); return; }
        div.classList.remove('hidden');
        div.innerHTML = `
            <h3>路径规划结果</h3>
            <div class="stat-row"><span class="label">总距离:</span><span class="value">${result.totalDistanceKm?.toFixed(1) || '-'} km</span></div>
            <div class="stat-row"><span class="label">预计时间:</span><span class="value">${result.estimatedHours?.toFixed(1) || '-'} 小时</span></div>
            <div class="stat-row"><span class="label">风险等级:</span><span class="value"><span class="risk-level ${result.riskLevel || 'MODERATE'}">${this.getRiskLabel(result.riskLevel)}</span></span></div>
            <div class="stat-row"><span class="label">风险评分:</span><span class="value">${((result.totalRiskScore || 0) * 100).toFixed(1)}%</span></div>
            <div class="stat-row"><span class="label">海拔提升:</span><span class="value">${result.elevationGainM?.toFixed(0) || '-'} m</span></div>
            <div class="stat-row"><span class="label">需水量:</span><span class="value">${result.waterRequiredLiters?.toFixed(0) || '-'} L</span></div>
            <div class="stat-row"><span class="label">算法:</span><span class="value">${result.algorithmUsed || 'A*'}</span></div>
            <div class="stat-row"><span class="label">计算耗时:</span><span class="value">${result.computationTimeMs || '-'} ms</span></div>
        `;
    },

    renderRiskAnalysis(analysis) {
        const div = document.getElementById('riskAnalysis');
        if (!analysis) { div.classList.add('hidden'); return; }
        div.classList.remove('hidden');
        div.innerHTML = `
            <div class="risk-header">
                <span class="risk-score">${((analysis.overallRiskScore || 0) * 100).toFixed(0)}%</span>
                <span class="risk-level ${analysis.riskLevel || 'MODERATE'}">${this.getRiskLabel(analysis.riskLevel)}</span>
            </div>
            <div class="risk-section">
                <h4>气温情况</h4>
                <div class="stat-row"><span class="label">平均:</span><span class="value">${analysis.avgTemperature?.toFixed(1) || '-'}°C</span></div>
                <div class="stat-row"><span class="label">最高:</span><span class="value">${analysis.maxTemperature?.toFixed(1) || '-'}°C</span></div>
                <div class="stat-row"><span class="label">最低:</span><span class="value">${analysis.minTemperature?.toFixed(1) || '-'}°C</span></div>
            </div>
            <div class="risk-section">
                <h4>气象风险</h4>
                <div class="stat-row"><span class="label">沙尘暴概率:</span><span class="value">${((analysis.sandstormProbability || 0) * 100).toFixed(0)}%</span></div>
                <div class="stat-row"><span class="label">水源可用度:</span><span class="value">${((analysis.waterAvailabilityScore || 0) * 100).toFixed(0)}%</span></div>
                <div class="stat-row"><span class="label">推荐行程:</span><span class="value">${analysis.recommendedTravelDays || '-'} 天</span></div>
            </div>
            ${analysis.riskFactors?.length ? `<div class="risk-section"><h4>⚠️ 风险因素</h4><ul>${analysis.riskFactors.map(f => `<li>${f}</li>`).join('')}</ul></div>` : ''}
            ${analysis.recommendations?.length ? `<div class="risk-section"><h4>💡 建议</h4><ul>${analysis.recommendations.map(r => `<li>${r}</li>`).join('')}</ul></div>` : ''}
        `;
    },

    renderStationWeather(latest) {
        const div = document.getElementById('stationWeather');
        if (!latest) {
            div.innerHTML = '<div class="empty-state">选择气象站查看详情</div>';
            return;
        }
        div.innerHTML = `
            <div class="weather-grid">
                <div class="weather-item"><span class="w-value">${latest.temperatureC?.toFixed(1) || '-'}°C</span><span class="w-label">温度</span></div>
                <div class="weather-item"><span class="w-value">${latest.windSpeedKmh?.toFixed(1) || '-'} km/h</span><span class="w-label">风速</span></div>
                <div class="weather-item"><span class="w-value">${latest.humidityPct?.toFixed(0) || '-'}%</span><span class="w-label">湿度</span></div>
                <div class="weather-item"><span class="w-value">${((latest.sandstormProbability || 0) * 100).toFixed(0)}%</span><span class="w-label">沙尘暴概率</span></div>
                <div class="weather-item"><span class="w-value">${latest.precipitationMm || 0} mm</span><span class="w-label">降水</span></div>
                <div class="weather-item"><span class="w-value">${latest.visibilityKm?.toFixed(1) || '-'} km</span><span class="w-label">能见度</span></div>
            </div>
            <p style="color:#888;font-size:0.75rem;margin-top:8px;">
                数据时间: ${latest.reportTime ? new Date(latest.reportTime).toLocaleString('zh-CN') : '-'}
            </p>
        `;
    },

    renderAlerts(alerts) {
        const list = document.getElementById('alertsList');
        if (!alerts || alerts.length === 0) {
            list.innerHTML = '<div class="empty-state">暂无告警</div>';
            return;
        }
        list.innerHTML = alerts.slice(0, 10).map(alert => `
            <div class="alert-item ${alert.severity?.toLowerCase() || 'moderate'}">
                <div class="alert-type">${this.getAlertTypeLabel(alert.alertType)}</div>
                <div class="alert-message">${alert.message}</div>
                <div class="alert-time">${alert.triggeredAt ? new Date(alert.triggeredAt).toLocaleString('zh-CN') : ''}</div>
            </div>
        `).join('');
    },

    renderCaravanList(caravans) {
        const list = document.getElementById('caravanList');
        if (!caravans || caravans.length === 0) {
            list.innerHTML = '<div class="empty-state">暂无驼队</div>';
            return;
        }
        list.innerHTML = caravans.map(c => `
            <div class="caravan-item" onclick="App.focusCaravan(${c.caravanId})">
                <div class="c-name">${c.name}</div>
                <span class="c-status ${c.status?.toLowerCase().replace('_', '-') || ''}">${this.getStatusLabel(c.status)}</span>
                <div class="c-info"><span>💧 ${Math.round(c.waterSupplyLiters || 0)}L</span></div>
                <div class="c-actions">
                    <button onclick="event.stopPropagation(); App.startCaravan(${c.caravanId})">出发</button>
                    <button onclick="event.stopPropagation(); App.stopCaravan(${c.caravanId})">停靠</button>
                </div>
            </div>
        `).join('');
    },

    updateStats(caravans, alerts, stations, routes) {
        document.getElementById('statCaravans').textContent = caravans || 0;
        document.getElementById('statAlerts').textContent = alerts || 0;
        document.getElementById('statStations').textContent = stations || 20;
        document.getElementById('statRoutes').textContent = routes || 10;
    },

    generateRouteCoordinates(routeId) {
        const routeCoords = {
            1: [[108.94,34.26],[106.16,34.73],[103.83,36.06],[102.64,37.43],[100.45,38.93],[97.14,39.73],[94.66,40.14]],
            2: [[94.66,40.14],[92.24,40.51],[90.18,40.52],[89.55,40.51]],
            3: [[89.55,40.51],[87.31,40.53],[85.54,39.48],[83.13,38.32],[81.47,37.21],[80.05,36.95]],
            4: [[80.05,36.95],[78.38,37.12],[77.24,38.17],[76.87,39.42]],
            5: [[76.87,39.42],[75.99,39.47],[75.23,39.72],[74.87,40.12]],
            6: [[75.99,39.47],[73.75,39.63],[71.67,39.83],[69.28,40.11],[66.96,39.65]],
            7: [[94.66,40.14],[95.01,41.73],[93.51,42.83]],
            8: [[93.51,42.83],[91.62,42.82],[89.18,43.78],[88.23,44.01]],
            9: [[88.23,44.01],[86.18,44.28],[82.61,43.82],[80.03,43.27],[77.03,42.84],[74.58,42.78]],
            10: [[66.96,39.65],[64.43,39.65],[61.83,36.30],[58.35,36.28],[54.38,36.68],[51.42,35.69]]
        };
        return routeCoords[routeId] || [];
    },

    getRouteFirstPoint(routeId) {
        const coords = this.generateRouteCoordinates(routeId);
        return coords.length > 0 ? coords[0] : null;
    },

    getRouteLastPoint(routeId) {
        const coords = this.generateRouteCoordinates(routeId);
        return coords.length > 0 ? coords[coords.length - 1] : null;
    },

    getRiskLabel(level) {
        const labels = { 'LOW': '低', 'MODERATE': '中等', 'HIGH': '高', 'EXTREME': '极高' };
        return labels[level] || level || '未知';
    },

    getAlertTypeLabel(type) {
        const types = {
            'SANDSTORM_WARNING': '🌪️ 沙尘暴预警',
            'HIGH_WIND_WARNING': '💨 大风预警',
            'EXTREME_HEAT_WARNING': '🔥 高温预警',
            'EXTREME_COLD_WARNING': '❄️ 低温预警',
            'WATER_SHORTAGE': '💧 水源不足',
            'LOW_VISIBILITY': '🌫️ 低能见度'
        };
        return types[type] || type || '未知';
    },

    getStatusLabel(status) {
        const labels = { 'EN_ROUTE': '行进中', 'RESTING': '休整中', 'IDLE': '待命' };
        return labels[status] || status || '未知';
    }
};
