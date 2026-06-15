const API = {
    async get(endpoint) {
        try {
            const response = await fetch(`${CONFIG.API_BASE_URL}${endpoint}`, {
                method: 'GET',
                headers: { 'Content-Type': 'application/json' }
            });
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            return await response.json();
        } catch (error) {
            console.error(`GET ${endpoint} 失败:`, error);
            throw error;
        }
    },

    async post(endpoint, data) {
        try {
            const response = await fetch(`${CONFIG.API_BASE_URL}${endpoint}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(data)
            });
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            return await response.json();
        } catch (error) {
            console.error(`POST ${endpoint} 失败:`, error);
            throw error;
        }
    },

    async put(endpoint, data) {
        try {
            const response = await fetch(`${CONFIG.API_BASE_URL}${endpoint}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: data ? JSON.stringify(data) : undefined
            });
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            return await response.json();
        } catch (error) {
            console.error(`PUT ${endpoint} 失败:`, error);
            throw error;
        }
    },

    getRoutes: () => API.get('/routes'),
    getRoute: (id) => API.get(`/routes/${id}`),
    getRouteWaypoints: (id) => API.get(`/routes/${id}/waypoints`),

    getCaravans: () => API.get('/caravans'),
    getCaravan: (id) => API.get(`/caravans/${id}`),
    createCaravan: (data) => API.post('/caravans', data),
    startCaravan: (id) => API.post(`/caravans/${id}/start`),
    stopCaravan: (id) => API.post(`/caravans/${id}/stop`),

    planPath: (data) => API.post('/pathfinding/plan', data),
    quickPlan: (startLng, startLat, endLng, endLat, season, speed) =>
        API.get(`/pathfinding/quick?startLng=${startLng}&startLat=${startLat}&endLng=${endLng}&endLat=${endLat}&season=${season}&speed=${speed}`),

    getStations: () => API.get('/weather/stations'),
    getLatestReports: () => API.get('/weather/reports/latest'),
    getStationReports: (stationId, limit = 10) =>
        API.get(`/weather/reports/station/${stationId}?limit=${limit}`),

    getRouteRisk: (routeId, season = 'SPRING') =>
        API.get(`/weather/risk/route/${routeId}?season=${season}`),
    getAllRouteRisks: () => API.get('/weather/risk/all'),

    getSandstormHeatmap: (time = null) => {
        const param = time ? `?time=${time}` : '';
        return API.get(`/weather/heatmap/sandstorm${param}`);
    },
    getTemperatureHeatmap: (time = null) => {
        const param = time ? `?time=${time}` : '';
        return API.get(`/weather/heatmap/temperature${param}`);
    },

    getAlerts: () => API.get('/alerts'),
    getRouteAlerts: (routeId) => API.get(`/alerts/route/${routeId}`),
    getCaravanAlerts: (caravanId) => API.get(`/alerts/caravan/${caravanId}`),
    resolveAlert: (id) => API.put(`/alerts/${id}/resolve`),
    simulateSandstorm: (routeId) => API.post(`/alerts/simulate/sandstorm/${routeId}`),

    submitWeatherReport: (stationId, report) =>
        API.post(`/weather/reports/${stationId}`, report)
};
