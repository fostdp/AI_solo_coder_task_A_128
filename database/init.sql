CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

DROP TABLE IF EXISTS alerts CASCADE;
DROP TABLE IF EXISTS weather_reports CASCADE;
DROP TABLE IF EXISTS weather_stations CASCADE;
DROP TABLE IF EXISTS waypoints CASCADE;
DROP TABLE IF EXISTS routes CASCADE;
DROP TABLE IF EXISTS caravans CASCADE;
DROP TABLE IF EXISTS terrain_grid CASCADE;
DROP TABLE IF EXISTS water_sources CASCADE;
DROP TABLE IF EXISTS seasonal_risk_profiles CASCADE;

CREATE TABLE routes (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    name_en VARCHAR(100),
    description TEXT,
    geom LINESTRING NOT NULL,
    total_distance_km DOUBLE PRECISION,
    difficulty_level VARCHAR(20) DEFAULT 'MODERATE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE waypoints (
    id SERIAL PRIMARY KEY,
    route_id INTEGER REFERENCES routes(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    name_en VARCHAR(100),
    geom POINT NOT NULL,
    elevation_m DOUBLE PRECISION,
    waypoint_order INTEGER NOT NULL,
    is_oasis BOOLEAN DEFAULT FALSE,
    water_available BOOLEAN DEFAULT FALSE,
    supply_station BOOLEAN DEFAULT FALSE,
    description TEXT
);

CREATE TABLE weather_stations (
    id SERIAL PRIMARY KEY,
    station_code VARCHAR(20) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    geom POINT NOT NULL,
    elevation_m DOUBLE PRECISION,
    route_id INTEGER REFERENCES routes(id),
    coverage_radius_km DOUBLE PRECISION DEFAULT 50.0,
    is_active BOOLEAN DEFAULT TRUE,
    installed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE weather_reports (
    id SERIAL PRIMARY KEY,
    station_id INTEGER REFERENCES weather_stations(id) ON DELETE CASCADE,
    report_time TIMESTAMP NOT NULL,
    temperature_c DOUBLE PRECISION,
    precipitation_mm DOUBLE PRECISION DEFAULT 0,
    wind_speed_kmh DOUBLE PRECISION DEFAULT 0,
    wind_direction INTEGER,
    humidity_pct DOUBLE PRECISION,
    sandstorm_probability DOUBLE PRECISION DEFAULT 0,
    visibility_km DOUBLE PRECISION,
    air_pressure_hpa DOUBLE PRECISION,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE caravans (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    route_id INTEGER REFERENCES routes(id),
    current_position POINT,
    current_waypoint_id INTEGER REFERENCES waypoints(id),
    speed_kmh DOUBLE PRECISION DEFAULT 5.0,
    status VARCHAR(20) DEFAULT 'IDLE',
    cargo_type VARCHAR(50),
    cargo_weight_kg DOUBLE PRECISION,
    crew_count INTEGER DEFAULT 20,
    camel_count INTEGER DEFAULT 50,
    water_supply_liters DOUBLE PRECISION DEFAULT 2000,
    food_supply_days DOUBLE PRECISION DEFAULT 30,
    departure_time TIMESTAMP,
    estimated_arrival TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE terrain_grid (
    id SERIAL PRIMARY KEY,
    geom POLYGON NOT NULL,
    grid_row INTEGER NOT NULL,
    grid_col INTEGER NOT NULL,
    elevation_m DOUBLE PRECISION,
    terrain_type VARCHAR(30) NOT NULL,
    passability DOUBLE PRECISION DEFAULT 1.0,
    water_accessibility DOUBLE PRECISION DEFAULT 0.0,
    vegetation_index DOUBLE PRECISION DEFAULT 0.0
);

CREATE TABLE water_sources (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    geom POINT NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    reliability VARCHAR(20) DEFAULT 'SEASONAL',
    average_flow_liters_per_day DOUBLE PRECISION,
    is_permanent BOOLEAN DEFAULT FALSE,
    nearest_waypoint_id INTEGER REFERENCES waypoints(id)
);

CREATE TABLE seasonal_risk_profiles (
    id SERIAL PRIMARY KEY,
    route_id INTEGER REFERENCES routes(id) ON DELETE CASCADE,
    season VARCHAR(20) NOT NULL,
    avg_temperature_c DOUBLE PRECISION,
    max_temperature_c DOUBLE PRECISION,
    min_temperature_c DOUBLE PRECISION,
    avg_precipitation_mm DOUBLE PRECISION,
    avg_wind_speed_kmh DOUBLE PRECISION,
    sandstorm_frequency DOUBLE PRECISION,
    water_availability_pct DOUBLE PRECISION,
    overall_risk_score DOUBLE PRECISION,
    risk_level VARCHAR(20),
    notes TEXT
);

CREATE TABLE alerts (
    id SERIAL PRIMARY KEY,
    alert_type VARCHAR(30) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    route_id INTEGER REFERENCES routes(id),
    station_id INTEGER REFERENCES weather_stations(id),
    caravan_id INTEGER REFERENCES caravans(id),
    message TEXT NOT NULL,
    geom POINT,
    is_active BOOLEAN DEFAULT TRUE,
    triggered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP
);

CREATE INDEX idx_routes_geom ON routes USING GIST (geom);
CREATE INDEX idx_waypoints_geom ON waypoints USING GIST (geom);
CREATE INDEX idx_waypoints_route ON waypoints (route_id, waypoint_order);
CREATE INDEX idx_stations_geom ON weather_stations USING GIST (geom);
CREATE INDEX idx_stations_route ON weather_stations (route_id);
CREATE INDEX idx_reports_station_time ON weather_reports (station_id, report_time DESC);
CREATE INDEX idx_reports_sandstorm ON weather_reports (sandstorm_probability) WHERE sandstorm_probability > 0.5;
CREATE INDEX idx_caravans_route ON caravans (route_id);
CREATE INDEX idx_caravans_position ON caravans USING GIST (current_position);
CREATE INDEX idx_terrain_geom ON terrain_grid USING GIST (geom);
CREATE INDEX idx_terrain_type ON terrain_grid (terrain_type);
CREATE INDEX idx_water_geom ON water_sources USING GIST (geom);
CREATE INDEX idx_alerts_active ON alerts (is_active, triggered_at DESC) WHERE is_active = TRUE;
CREATE INDEX idx_alerts_route ON alerts (route_id) WHERE is_active = TRUE;

INSERT INTO routes (name, name_en, description, geom, total_distance_km, difficulty_level) VALUES
('长安-敦煌主线', 'Chang''an-Dunhuang Main Route', '从长安出发经河西走廊至敦煌的丝绸之路主线', ST_GeomFromText('LINESTRING(108.94 34.26, 106.16 34.73, 103.83 36.06, 102.64 37.43, 100.45 38.93, 97.14 39.73, 94.66 40.14)', 4326), 1800, 'MODERATE'),
('敦煌-楼兰道', 'Dunhuang-Loulan Route', '从敦煌沿白龙堆沙漠至楼兰古城', ST_GeomFromText('LINESTRING(94.66 40.14, 92.24 40.51, 90.18 40.52, 89.55 40.51)', 4326), 500, 'HARD'),
('楼兰-于阗道', 'Loulan-Yutian Route', '从楼兰沿塔里木盆地南缘至于阗', ST_GeomFromText('LINESTRING(89.55 40.51, 87.31 40.53, 85.54 39.48, 83.13 38.32, 81.47 37.21, 80.05 36.95)', 4326), 1000, 'HARD'),
('于阗-莎车道', 'Yutian-Shache Route', '从于阗沿昆仑山北麓西行至莎车', ST_GeomFromText('LINESTRING(80.05 36.95, 78.38 37.12, 77.24 38.17, 76.87 39.42)', 4326), 450, 'MODERATE'),
('莎车-疏勒道', 'Shache-Shule Route', '从莎车翻越帕米尔高原至疏勒(喀什)', ST_GeomFromText('LINESTRING(76.87 39.42, 75.99 39.47, 75.23 39.72, 74.87 40.12, 75.99 39.47)', 4326), 350, 'HARD'),
('疏勒-撒马尔罕道', 'Shule-Samarkand Route', '从疏勒翻越葱岭至撒马尔罕', ST_GeomFromText('LINESTRING(75.99 39.47, 73.75 39.63, 71.67 39.83, 69.28 40.11, 66.96 39.65)', 4326), 900, 'EXTREME'),
('敦煌-伊吾道', 'Dunhuang-Yiwu Route', '从敦煌北上经莫贺延碛至伊吾(哈密)', ST_GeomFromText('LINESTRING(94.66 40.14, 95.01 41.73, 93.51 42.83)', 4326), 350, 'HARD'),
('伊吾-庭州道', 'Yiwu-Tingzhou Route', '从伊吾西行至庭州(吉木萨尔)', ST_GeomFromText('LINESTRING(93.51 42.83, 91.62 42.82, 89.18 43.78, 88.23 44.01)', 4326), 500, 'MODERATE'),
('庭州-碎叶道', 'Tingzhou-Suiye Route', '从庭州西行经伊犁河谷至碎叶城', ST_GeomFromText('LINESTRING(88.23 44.01, 86.18 44.28, 82.61 43.82, 80.03 43.27, 77.03 42.84, 74.58 42.78)', 4326), 1400, 'HARD'),
('撒马尔罕-波斯道', 'Samarkand-Persia Route', '从撒马尔罕西行至波斯帝国腹地', ST_GeomFromText('LINESTRING(66.96 39.65, 64.43 39.65, 61.83 36.30, 58.35 36.28, 54.38 36.68, 51.42 35.69)', 4326), 1800, 'EXTREME');

INSERT INTO waypoints (route_id, name, name_en, geom, elevation_m, waypoint_order, is_oasis, water_available, supply_station, description) VALUES
(1, '长安', 'Chang''an', ST_GeomFromText('POINT(108.94 34.26)', 4326), 405, 1, FALSE, TRUE, TRUE, '丝绸之路东方起点'),
(1, '天水', 'Tianshui', ST_GeomFromText('POINT(106.16 34.73)', 4326), 1100, 2, FALSE, TRUE, TRUE, '陇右重镇'),
(1, '兰州', 'Lanzhou', ST_GeomFromText('POINT(103.83 36.06)', 4326), 1520, 3, FALSE, TRUE, TRUE, '黄河渡口'),
(1, '武威', 'Wuwei', ST_GeomFromText('POINT(102.64 37.43)', 4326), 1530, 4, FALSE, TRUE, TRUE, '河西四郡之首'),
(1, '张掖', 'Zhangye', ST_GeomFromText('POINT(100.45 38.93)', 4326), 1470, 5, FALSE, TRUE, TRUE, '甘州'),
(1, '嘉峪关', 'Jiayuguan', ST_GeomFromText('POINT(97.14 39.73)', 4326), 1700, 6, FALSE, TRUE, TRUE, '天下第一雄关'),
(1, '敦煌', 'Dunhuang', ST_GeomFromText('POINT(94.66 40.14)', 4326), 1139, 7, TRUE, TRUE, TRUE, '丝路咽喉'),
(2, '玉门关', 'Yumen Pass', ST_GeomFromText('POINT(92.24 40.51)', 4326), 1250, 1, FALSE, FALSE, FALSE, '西出阳关无故人'),
(2, '白龙堆', 'Bailongdui', ST_GeomFromText('POINT(90.18 40.52)', 4326), 800, 2, FALSE, FALSE, FALSE, '雅丹地貌危险区'),
(2, '楼兰', 'Loulan', ST_GeomFromText('POINT(89.55 40.51)', 4326), 850, 3, TRUE, TRUE, FALSE, '楼兰古城遗址'),
(3, '若羌', 'Ruoqiang', ST_GeomFromText('POINT(87.31 40.53)', 4326), 890, 1, FALSE, TRUE, TRUE, '塔里木盆地南缘'),
(3, '且末', 'Qiemo', ST_GeomFromText('POINT(85.54 39.48)', 4326), 1250, 2, TRUE, TRUE, TRUE, '古代且末国'),
(3, '民丰', 'Minfeng', ST_GeomFromText('POINT(83.13 38.32)', 4326), 1380, 3, TRUE, TRUE, FALSE, '尼雅遗址附近'),
(3, '于阗', 'Yutian', ST_GeomFromText('POINT(80.05 36.95)', 4326), 1420, 5, TRUE, TRUE, TRUE, '古于阗国'),
(4, '皮山', 'Pishan', ST_GeomFromText('POINT(78.38 37.12)', 4326), 1370, 1, FALSE, TRUE, FALSE, '昆仑山北麓'),
(4, '叶城', 'Yecheng', ST_GeomFromText('POINT(77.24 38.17)', 4326), 1360, 2, FALSE, TRUE, TRUE, '通往喀喇昆仑山口'),
(4, '莎车', 'Shache', ST_GeomFromText('POINT(76.87 39.42)', 4326), 1230, 3, TRUE, TRUE, TRUE, '古莎车国'),
(5, '英吉沙', 'Yingjisha', ST_GeomFromText('POINT(75.99 39.47)', 4326), 1310, 1, FALSE, TRUE, FALSE, '帕米尔东麓'),
(5, '塔什库尔干', 'Tashkurgan', ST_GeomFromText('POINT(75.23 39.72)', 4326), 3100, 2, FALSE, TRUE, TRUE, '石头城'),
(5, '疏勒', 'Shule', ST_GeomFromText('POINT(74.87 40.12)', 4326), 1290, 3, TRUE, TRUE, TRUE, '古疏勒国'),
(6, '喀什', 'Kashgar', ST_GeomFromText('POINT(75.99 39.47)', 4326), 1290, 1, TRUE, TRUE, TRUE, '丝路重镇'),
(6, '奥什', 'Osh', ST_GeomFromText('POINT(73.75 39.63)', 4326), 1020, 2, FALSE, TRUE, TRUE, '费尔干纳盆地入口'),
(6, '浩罕', 'Kokand', ST_GeomFromText('POINT(71.67 39.83)', 4326), 460, 3, TRUE, TRUE, TRUE, '费尔干纳古城'),
(6, '撒马尔罕', 'Samarkand', ST_GeomFromText('POINT(66.96 39.65)', 4326), 702, 4, TRUE, TRUE, TRUE, '中亚明珠'),
(7, '星星峡', 'Xingxingxia', ST_GeomFromText('POINT(95.01 41.73)', 4326), 1100, 1, FALSE, FALSE, FALSE, '莫贺延碛入口'),
(7, '伊吾', 'Yiwu', ST_GeomFromText('POINT(93.51 42.83)', 4326), 780, 2, TRUE, TRUE, TRUE, '哈密地区'),
(8, '巴里坤', 'Barkol', ST_GeomFromText('POINT(91.62 42.82)', 4326), 1650, 1, TRUE, TRUE, TRUE, '天山北麓草场'),
(8, '木垒', 'Mori', ST_GeomFromText('POINT(89.18 43.78)', 4326), 1280, 2, FALSE, TRUE, FALSE, '天山牧场'),
(8, '庭州', 'Tingzhou', ST_GeomFromText('POINT(88.23 44.01)', 4326), 580, 3, TRUE, TRUE, TRUE, '北庭都护府'),
(9, '乌鲁木齐', 'Urumqi', ST_GeomFromText('POINT(86.18 44.28)', 4326), 800, 1, FALSE, TRUE, TRUE, '天山北麓'),
(9, '精河', 'Jinghe', ST_GeomFromText('POINT(82.61 43.82)', 4326), 320, 2, FALSE, TRUE, FALSE, '艾比湖畔'),
(9, '伊宁', 'Yining', ST_GeomFromText('POINT(80.03 43.27)', 4326), 640, 3, TRUE, TRUE, TRUE, '伊犁河谷'),
(9, '碎叶城', 'Suiye', ST_GeomFromText('POINT(74.58 42.78)', 4326), 850, 5, TRUE, TRUE, FALSE, '李白出生地'),
(10, '布哈拉', 'Bukhara', ST_GeomFromText('POINT(64.43 39.65)', 4326), 225, 1, TRUE, TRUE, TRUE, '布哈拉古城'),
(10, '马什哈德', 'Mashhad', ST_GeomFromText('POINT(58.35 36.28)', 4326), 985, 2, FALSE, TRUE, TRUE, '波斯圣城'),
(10, '德黑兰', 'Tehran', ST_GeomFromText('POINT(51.42 35.69)', 4326), 1190, 3, FALSE, TRUE, TRUE, '波斯腹地');

INSERT INTO weather_stations (station_code, name, geom, elevation_m, route_id, coverage_radius_km) VALUES
('WS-CG-001', '长安气象站', ST_GeomFromText('POINT(108.94 34.26)', 4326), 405, 1, 60),
('WS-LZ-002', '兰州气象站', ST_GeomFromText('POINT(103.83 36.06)', 4326), 1520, 1, 80),
('WS-WW-003', '武威气象站', ST_GeomFromText('POINT(102.64 37.43)', 4326), 1530, 1, 70),
('WS-ZY-004', '张掖气象站', ST_GeomFromText('POINT(100.45 38.93)', 4326), 1470, 1, 75),
('WS-DH-005', '敦煌气象站', ST_GeomFromText('POINT(94.66 40.14)', 4326), 1139, 1, 90),
('WS-YM-006', '玉门关气象站', ST_GeomFromText('POINT(92.24 40.51)', 4326), 1250, 2, 50),
('WS-LL-007', '楼兰气象站', ST_GeomFromText('POINT(89.55 40.51)', 4326), 850, 2, 60),
('WS-RQ-008', '若羌气象站', ST_GeomFromText('POINT(87.31 40.53)', 4326), 890, 3, 70),
('WS-QM-009', '且末气象站', ST_GeomFromText('POINT(85.54 39.48)', 4326), 1250, 3, 65),
('WS-YT-010', '于阗气象站', ST_GeomFromText('POINT(80.05 36.95)', 4326), 1420, 3, 80),
('WS-SC-011', '莎车气象站', ST_GeomFromText('POINT(76.87 39.42)', 4326), 1230, 4, 60),
('WS-KS-012', '喀什气象站', ST_GeomFromText('POINT(75.99 39.47)', 4326), 1290, 5, 75),
('WS-TK-013', '塔什库尔干气象站', ST_GeomFromText('POINT(75.23 39.72)', 4326), 3100, 5, 50),
('WS-SM-014', '撒马尔罕气象站', ST_GeomFromText('POINT(66.96 39.65)', 4326), 702, 6, 80),
('WS-YW-015', '伊吾气象站', ST_GeomFromText('POINT(93.51 42.83)', 4326), 780, 7, 65),
('WS-TZ-016', '庭州气象站', ST_GeomFromText('POINT(88.23 44.01)', 4326), 580, 8, 70),
('WS-YN-017', '伊宁气象站', ST_GeomFromText('POINT(80.03 43.27)', 4326), 640, 9, 80),
('WS-SY-018', '碎叶城气象站', ST_GeomFromText('POINT(74.58 42.78)', 4326), 850, 9, 60),
('WS-BH-019', '布哈拉气象站', ST_GeomFromText('POINT(64.43 39.65)', 4326), 225, 10, 70),
('WS-MH-020', '马什哈德气象站', ST_GeomFromText('POINT(58.35 36.28)', 4326), 985, 10, 75);

INSERT INTO terrain_grid (geom, grid_row, grid_col, elevation_m, terrain_type, passability, water_accessibility, vegetation_index) VALUES
(ST_MakeEnvelope(108, 34, 109, 35, 4326), 0, 0, 405, 'PLAINS', 0.95, 0.9, 0.7),
(ST_MakeEnvelope(107, 34, 108, 35, 4326), 0, 1, 650, 'HILLS', 0.8, 0.7, 0.5),
(ST_MakeEnvelope(106, 34, 107, 35, 4326), 0, 2, 1100, 'MOUNTAINS', 0.6, 0.5, 0.3),
(ST_MakeEnvelope(105, 35, 106, 36, 4326), 1, 0, 1300, 'MOUNTAINS', 0.5, 0.4, 0.25),
(ST_MakeEnvelope(104, 35, 105, 36, 4326), 1, 1, 1450, 'PLATEAU', 0.65, 0.5, 0.3),
(ST_MakeEnvelope(103, 36, 104, 37, 4326), 1, 2, 1520, 'VALLEY', 0.85, 0.8, 0.5),
(ST_MakeEnvelope(102, 37, 103, 38, 4326), 2, 0, 1530, 'DESERT_STEPPE', 0.7, 0.3, 0.15),
(ST_MakeEnvelope(101, 37, 102, 38, 4326), 2, 1, 1500, 'DESERT_STEPPE', 0.65, 0.25, 0.1),
(ST_MakeEnvelope(100, 38, 101, 39, 4326), 2, 2, 1470, 'OASIS', 0.9, 0.85, 0.6),
(ST_MakeEnvelope(99, 39, 100, 40, 4326), 3, 0, 1600, 'DESERT', 0.5, 0.1, 0.05),
(ST_MakeEnvelope(98, 39, 99, 40, 4326), 3, 1, 1700, 'MOUNTAINS', 0.4, 0.15, 0.05),
(ST_MakeEnvelope(97, 39, 98, 40, 4326), 3, 2, 1650, 'DESERT', 0.55, 0.15, 0.08),
(ST_MakeEnvelope(96, 40, 97, 41, 4326), 4, 0, 1400, 'DESERT', 0.45, 0.08, 0.03),
(ST_MakeEnvelope(95, 40, 96, 41, 4326), 4, 1, 1200, 'DESERT', 0.5, 0.1, 0.05),
(ST_MakeEnvelope(94, 40, 95, 41, 4326), 4, 2, 1139, 'OASIS', 0.9, 0.85, 0.65),
(ST_MakeEnvelope(93, 40, 94, 41, 4326), 5, 0, 1100, 'DESERT', 0.4, 0.05, 0.02),
(ST_MakeEnvelope(92, 40, 93, 41, 4326), 5, 1, 1250, 'DESERT', 0.35, 0.05, 0.02),
(ST_MakeEnvelope(91, 40, 92, 41, 4326), 5, 2, 900, 'SAND_DUNES', 0.25, 0.02, 0.01),
(ST_MakeEnvelope(90, 40, 91, 41, 4326), 6, 0, 800, 'SAND_DUNES', 0.2, 0.02, 0.01),
(ST_MakeEnvelope(89, 40, 90, 41, 4326), 6, 1, 850, 'SALINE', 0.3, 0.1, 0.03),
(ST_MakeEnvelope(88, 40, 89, 41, 4326), 6, 2, 890, 'DESERT_STEPPE', 0.6, 0.3, 0.12),
(ST_MakeEnvelope(87, 39, 88, 40, 4326), 7, 0, 950, 'DESERT_STEPPE', 0.55, 0.25, 0.1),
(ST_MakeEnvelope(86, 39, 87, 40, 4326), 7, 1, 1200, 'DESERT', 0.45, 0.1, 0.04),
(ST_MakeEnvelope(85, 38, 86, 39, 4326), 7, 2, 1250, 'OASIS', 0.85, 0.75, 0.5),
(ST_MakeEnvelope(84, 38, 85, 39, 4326), 8, 0, 1300, 'DESERT_STEPPE', 0.5, 0.2, 0.08),
(ST_MakeEnvelope(83, 38, 84, 39, 4326), 8, 1, 1380, 'OASIS', 0.8, 0.7, 0.45),
(ST_MakeEnvelope(82, 38, 83, 39, 4326), 8, 2, 1400, 'DESERT', 0.4, 0.08, 0.03),
(ST_MakeEnvelope(81, 37, 82, 38, 4326), 9, 0, 1420, 'OASIS', 0.85, 0.8, 0.55),
(ST_MakeEnvelope(80, 36, 81, 37, 4326), 9, 1, 1420, 'OASIS', 0.88, 0.82, 0.58),
(ST_MakeEnvelope(79, 37, 80, 38, 4326), 9, 2, 1370, 'DESERT_STEPPE', 0.6, 0.25, 0.1),
(ST_MakeEnvelope(78, 37, 79, 38, 4326), 10, 0, 1360, 'FOOTHILLS', 0.7, 0.4, 0.2),
(ST_MakeEnvelope(77, 38, 78, 39, 4326), 10, 1, 1250, 'VALLEY', 0.8, 0.65, 0.4),
(ST_MakeEnvelope(76, 39, 77, 40, 4326), 10, 2, 1230, 'OASIS', 0.85, 0.75, 0.5),
(ST_MakeEnvelope(75, 39, 76, 40, 4326), 11, 0, 1800, 'HIGH_MOUNTAINS', 0.3, 0.15, 0.05),
(ST_MakeEnvelope(74, 39, 75, 40, 4326), 11, 1, 2500, 'HIGH_MOUNTAINS', 0.2, 0.1, 0.03),
(ST_MakeEnvelope(73, 39, 74, 40, 4326), 11, 2, 3100, 'HIGH_MOUNTAINS', 0.15, 0.08, 0.02),
(ST_MakeEnvelope(72, 39, 73, 40, 4326), 12, 0, 2200, 'MOUNTAINS', 0.25, 0.15, 0.05),
(ST_MakeEnvelope(71, 39, 72, 40, 4326), 12, 1, 1200, 'VALLEY', 0.75, 0.6, 0.35),
(ST_MakeEnvelope(70, 40, 71, 41, 4326), 12, 2, 460, 'OASIS', 0.9, 0.85, 0.65),
(ST_MakeEnvelope(69, 40, 70, 41, 4326), 13, 0, 600, 'PLAINS', 0.9, 0.7, 0.45),
(ST_MakeEnvelope(68, 40, 69, 41, 4326), 13, 1, 750, 'PLAINS', 0.88, 0.65, 0.4),
(ST_MakeEnvelope(67, 39, 68, 40, 4326), 13, 2, 702, 'OASIS', 0.92, 0.88, 0.7);

INSERT INTO water_sources (name, geom, source_type, reliability, average_flow_liters_per_day, is_permanent, nearest_waypoint_id) VALUES
('月牙泉', ST_GeomFromText('POINT(94.67 40.13)', 4326), 'SPING', 'PERMANENT', 5000, TRUE, 7),
('疏勒河', ST_GeomFromText('POINT(96.5 40.3)', 4326), 'RIVER', 'SEASONAL', 50000, FALSE, 6),
('孔雀河', ST_GeomFromText('POINT(89.55 40.52)', 4326), 'RIVER', 'SEASONAL', 30000, FALSE, 10),
('尼雅河', ST_GeomFromText('POINT(83.13 38.3)', 4326), 'RIVER', 'SEASONAL', 15000, FALSE, 14),
('克里雅河', ST_GeomFromText('POINT(81.47 37.2)', 4326), 'RIVER', 'SEASONAL', 20000, FALSE, 15),
('叶尔羌河', ST_GeomFromText('POINT(77.0 38.5)', 4326), 'RIVER', 'PERMANENT', 80000, TRUE, 18),
('伊犁河', ST_GeomFromText('POINT(80.03 43.27)', 4326), 'RIVER', 'PERMANENT', 200000, TRUE, 33),
('天山水源', ST_GeomFromText('POINT(86.18 44.28)', 4326), 'SPRING', 'SEASONAL', 10000, FALSE, 31),
('伊塞克湖', ST_GeomFromText('POINT(76.5 42.4)', 4326), 'LAKE', 'PERMANENT', 0, TRUE, 34),
('阿姆河', ST_GeomFromText('POINT(67.0 39.5)', 4326), 'RIVER', 'PERMANENT', 150000, TRUE, 24);

INSERT INTO seasonal_risk_profiles (route_id, season, avg_temperature_c, max_temperature_c, min_temperature_c, avg_precipitation_mm, avg_wind_speed_kmh, sandstorm_frequency, water_availability_pct, overall_risk_score, risk_level, notes) VALUES
(1, 'SPRING', 15, 30, 0, 25, 20, 0.1, 0.75, 0.25, 'LOW', '春季适宜通行，偶有风沙'),
(1, 'SUMMER', 28, 42, 15, 10, 15, 0.15, 0.5, 0.35, 'MODERATE', '夏季高温，水源减少'),
(1, 'AUTUMN', 12, 25, -5, 15, 25, 0.2, 0.6, 0.3, 'LOW', '秋季风沙增多但尚可通行'),
(1, 'WINTER', -5, 5, -25, 5, 30, 0.05, 0.3, 0.6, 'HIGH', '冬季严寒，大雪封山'),
(2, 'SPRING', 18, 35, 0, 5, 30, 0.3, 0.25, 0.55, 'MODERATE', '白龙堆春季风沙较大'),
(2, 'SUMMER', 35, 48, 20, 2, 25, 0.4, 0.1, 0.75, 'HIGH', '夏季极端高温，极度危险'),
(2, 'AUTUMN', 15, 28, -2, 3, 35, 0.35, 0.15, 0.6, 'HIGH', '秋季沙尘暴频发'),
(2, 'WINTER', -10, 0, -30, 3, 40, 0.1, 0.05, 0.85, 'EXTREME', '冬季严寒且风沙大，极度危险'),
(6, 'SPRING', 5, 15, -10, 40, 35, 0.05, 0.5, 0.55, 'MODERATE', '帕米尔高原春季仍寒'),
(6, 'SUMMER', 15, 25, 0, 30, 20, 0.02, 0.7, 0.3, 'LOW', '夏季唯一适宜翻越帕米尔的季节'),
(6, 'AUTUMN', 0, 10, -15, 20, 30, 0.05, 0.35, 0.6, 'HIGH', '秋季降温快，大雪风险'),
(6, 'WINTER', -20, -5, -40, 15, 50, 0.02, 0.1, 0.95, 'EXTREME', '冬季帕米尔不可通行');

INSERT INTO caravans (name, route_id, current_position, speed_kmh, status, cargo_type, cargo_weight_kg, crew_count, camel_count, water_supply_liters, food_supply_days) VALUES
('西行商队甲', 1, ST_GeomFromText('POINT(103.83 36.06)', 4326), 5.0, 'EN_ROUTE', 'SILK', 2000, 25, 60, 2500, 30),
('西域商队乙', 3, ST_GeomFromText('POINT(85.54 39.48)', 4326), 4.5, 'EN_ROUTE', 'JADE', 1500, 20, 50, 2000, 25),
('波斯商队丙', 6, ST_GeomFromText('POINT(73.75 39.63)', 4326), 3.5, 'RESTING', 'SPICE', 3000, 30, 70, 3000, 35),
('北道商队丁', 9, ST_GeomFromText('POINT(82.61 43.82)', 4326), 4.0, 'EN_ROUTE', 'HORSE', 1000, 15, 40, 1800, 20);
