#!/usr/bin/env python3
"""
古代驼队丝绸之路 - 气象站模拟器
模拟20个气象站每小时上报温度、降水、风速、沙尘暴概率等数据

支持：
- 实时模式 / 历史回填模式 / 演示模式
- 四季模拟（自动按月份切换 / 手动指定）
- 极端天气模式（强沙尘暴、热浪、寒潮）
- HTTP API上报 / MQTT发布
- 环境变量配置（Docker友好）
"""

import requests
import json
import time
import random
import math
import os
import sys
from datetime import datetime, timedelta
from typing import Dict, List, Tuple

try:
    import paho.mqtt.client as mqtt
    HAS_MQTT = True
except ImportError:
    HAS_MQTT = False

API_BASE_URL = os.getenv("BACKEND_URL", "http://localhost:8080/api")
MQTT_BROKER = os.getenv("MQTT_BROKER", "localhost")
MQTT_PORT = int(os.getenv("MQTT_PORT", "1883"))
MQTT_TOPIC = os.getenv("MQTT_TOPIC", "silkroad/weather")
MQTT_ENABLED = os.getenv("MQTT_ENABLED", "false").lower() == "true"
SIMULATOR_MODE = os.getenv("SIMULATOR_MODE", "demo")
SEASON_OVERRIDE = os.getenv("SEASON", "").lower()
EXTREME_WEATHER = os.getenv("EXTREME_WEATHER", "false").lower()
REPORT_INTERVAL = int(os.getenv("REPORT_INTERVAL", "3600"))
SPEED_FACTOR = int(os.getenv("SPEED_FACTOR", "1200"))

STATIONS = [
    {"id": 1, "code": "WS-CG-001", "name": "长安气象站", "lng": 108.94, "lat": 34.26, "elevation": 405, "route_id": 1},
    {"id": 2, "code": "WS-LZ-002", "name": "兰州气象站", "lng": 103.83, "lat": 36.06, "elevation": 1520, "route_id": 1},
    {"id": 3, "code": "WS-WW-003", "name": "武威气象站", "lng": 102.64, "lat": 37.43, "elevation": 1530, "route_id": 1},
    {"id": 4, "code": "WS-ZY-004", "name": "张掖气象站", "lng": 100.45, "lat": 38.93, "elevation": 1470, "route_id": 1},
    {"id": 5, "code": "WS-DH-005", "name": "敦煌气象站", "lng": 94.66, "lat": 40.14, "elevation": 1139, "route_id": 1},
    {"id": 6, "code": "WS-YM-006", "name": "玉门关气象站", "lng": 92.24, "lat": 40.51, "elevation": 1250, "route_id": 2},
    {"id": 7, "code": "WS-LL-007", "name": "楼兰气象站", "lng": 89.55, "lat": 40.51, "elevation": 850, "route_id": 2},
    {"id": 8, "code": "WS-RQ-008", "name": "若羌气象站", "lng": 87.31, "lat": 40.53, "elevation": 890, "route_id": 3},
    {"id": 9, "code": "WS-QM-009", "name": "且末气象站", "lng": 85.54, "lat": 39.48, "elevation": 1250, "route_id": 3},
    {"id": 10, "code": "WS-YT-010", "name": "于阗气象站", "lng": 80.05, "lat": 36.95, "elevation": 1420, "route_id": 3},
    {"id": 11, "code": "WS-SC-011", "name": "莎车气象站", "lng": 76.87, "lat": 39.42, "elevation": 1230, "route_id": 4},
    {"id": 12, "code": "WS-KS-012", "name": "喀什气象站", "lng": 75.99, "lat": 39.47, "elevation": 1290, "route_id": 5},
    {"id": 13, "code": "WS-TK-013", "name": "塔什库尔干气象站", "lng": 75.23, "lat": 39.72, "elevation": 3100, "route_id": 5},
    {"id": 14, "code": "WS-SM-014", "name": "撒马尔罕气象站", "lng": 66.96, "lat": 39.65, "elevation": 702, "route_id": 6},
    {"id": 15, "code": "WS-YW-015", "name": "伊吾气象站", "lng": 93.51, "lat": 42.83, "elevation": 780, "route_id": 7},
    {"id": 16, "code": "WS-TZ-016", "name": "庭州气象站", "lng": 88.23, "lat": 44.01, "elevation": 580, "route_id": 8},
    {"id": 17, "code": "WS-YN-017", "name": "伊宁气象站", "lng": 80.03, "lat": 43.27, "elevation": 640, "route_id": 9},
    {"id": 18, "code": "WS-SY-018", "name": "碎叶城气象站", "lng": 74.58, "lat": 42.78, "elevation": 850, "route_id": 9},
    {"id": 19, "code": "WS-BH-019", "name": "布哈拉气象站", "lng": 64.43, "lat": 39.65, "elevation": 225, "route_id": 10},
    {"id": 20, "code": "WS-MH-020", "name": "马什哈德气象站", "lng": 58.35, "lat": 36.28, "elevation": 985, "route_id": 10},
]

SEASON_BASE_TEMPS = {
    "spring": {"base": 15, "min": 0, "max": 30},
    "summer": {"base": 30, "min": 15, "max": 45},
    "autumn": {"base": 12, "min": -5, "max": 25},
    "winter": {"base": -5, "min": -25, "max": 5},
}

SEASON_WIND = {
    "spring": {"base": 25, "variance": 15},
    "summer": {"base": 20, "variance": 12},
    "autumn": {"base": 22, "variance": 18},
    "winter": {"base": 30, "variance": 20},
}

SEASON_HUMIDITY = {
    "spring": {"base": 35, "variance": 15},
    "summer": {"base": 25, "variance": 15},
    "autumn": {"base": 30, "variance": 12},
    "winter": {"base": 45, "variance": 15},
}

EXTREME_WEATHER_MODES = {
    "sandstorm": {
        "wind_multiplier": 2.5,
        "humidity_multiplier": 0.3,
        "temp_offset": 5,
        "sandstorm_boost": 0.4,
        "description": "强沙尘暴模式"
    },
    "heatwave": {
        "wind_multiplier": 1.2,
        "humidity_multiplier": 0.5,
        "temp_offset": 15,
        "sandstorm_boost": 0.1,
        "description": "热浪模式"
    },
    "coldwave": {
        "wind_multiplier": 1.8,
        "humidity_multiplier": 0.8,
        "temp_offset": -20,
        "sandstorm_boost": 0.05,
        "description": "寒潮模式"
    },
    "drought": {
        "wind_multiplier": 1.5,
        "humidity_multiplier": 0.2,
        "temp_offset": 8,
        "sandstorm_boost": 0.25,
        "description": "极端干旱模式"
    }
}

_mqtt_client = None
_mqtt_connected = False

def get_season(month: int) -> str:
    if SEASON_OVERRIDE and SEASON_OVERRIDE in ("spring", "summer", "autumn", "winter"):
        return SEASON_OVERRIDE
    if 3 <= month <= 5:
        return "spring"
    elif 6 <= month <= 8:
        return "summer"
    elif 9 <= month <= 11:
        return "autumn"
    else:
        return "winter"

def get_terrain_type(station: Dict) -> str:
    elevation = station["elevation"]
    lng = station["lng"]
    
    if elevation > 3000:
        return "HIGH_MOUNTAINS"
    if elevation > 1800:
        return "MOUNTAINS"
    if 75 < lng < 90 and elevation < 1500:
        return "OASIS"
    if 90 < lng < 100:
        return "DESERT"
    return "DESERT_STEPPE"

MAJOR_MOUNTAINS = [
    {"min_lat": 35.0, "max_lat": 39.0, "min_lng": 75.0, "max_lng": 80.0, "height": 5500, "name": "昆仑山"},
    {"min_lat": 39.0, "max_lat": 45.0, "min_lng": 74.0, "max_lng": 96.0, "height": 4000, "name": "天山山脉"},
    {"min_lat": 35.0, "max_lat": 40.0, "min_lng": 95.0, "max_lng": 104.0, "height": 3500, "name": "祁连山"},
    {"min_lat": 32.0, "max_lat": 36.0, "min_lng": 80.0, "max_lng": 95.0, "height": 6000, "name": "喀喇昆仑山"},
    {"min_lat": 40.0, "max_lat": 42.0, "min_lng": 92.0, "max_lng": 100.0, "height": 2500, "name": "马鬃山"}
]

SAND_SOURCE_AREAS = [
    {"min_lat": 38.0, "max_lat": 42.0, "min_lng": 88.0, "max_lng": 95.0, "intensity": 0.9, "name": "塔克拉玛干沙漠东缘"},
    {"min_lat": 39.0, "max_lat": 42.0, "min_lng": 95.0, "max_lng": 102.0, "intensity": 0.7, "name": "巴丹吉林沙漠"},
    {"min_lat": 40.0, "max_lat": 42.0, "min_lng": 102.0, "max_lng": 106.0, "intensity": 0.6, "name": "腾格里沙漠"},
    {"min_lat": 38.0, "max_lat": 40.0, "min_lng": 80.0, "max_lng": 88.0, "intensity": 0.85, "name": "塔克拉玛干沙漠腹地"}
]

def haversine_distance(lng1: float, lat1: float, lng2: float, lat2: float) -> float:
    R = 6371.0
    dLat = math.radians(lat2 - lat1)
    dLng = math.radians(lng2 - lng1)
    a = math.sin(dLat/2)**2 + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dLng/2)**2
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1-a))
    return R * c

def calculate_bearing(lng1: float, lat1: float, lng2: float, lat2: float) -> float:
    dLng = math.radians(lng2 - lng1)
    lat1Rad = math.radians(lat1)
    lat2Rad = math.radians(lat2)
    y = math.sin(dLng) * math.cos(lat2Rad)
    x = math.cos(lat1Rad) * math.sin(lat2Rad) - math.sin(lat1Rad) * math.cos(lat2Rad) * math.cos(dLng)
    return math.degrees(math.atan2(y, x))

def normalize_angle(angle: float) -> float:
    while angle < 0:
        angle += 360
    while angle >= 360:
        angle -= 360
    return angle

def get_terrain_elevation(lng: float, lat: float) -> float:
    base_elevation = 1000
    
    for mountain in MAJOR_MOUNTAINS:
        if mountain["min_lat"] <= lat <= mountain["max_lat"] and mountain["min_lng"] <= lng <= mountain["max_lng"]:
            center_lat = (mountain["min_lat"] + mountain["max_lat"]) / 2
            center_lng = (mountain["min_lng"] + mountain["max_lng"]) / 2
            dist_to_center = math.sqrt((lng - center_lng)**2 + (lat - center_lat)**2)
            height_factor = max(0, 1.0 - dist_to_center * 2)
            return mountain["height"] * height_factor + base_elevation * (1 - height_factor)
    
    if 75 < lng < 90 and 37 < lat < 42:
        return 800 + random.random() * 200
    if 90 < lng < 100 and 38 < lat < 42:
        return 1200 + random.random() * 300
    if 74 < lng < 76 and 39 < lat < 40:
        return 3000 + random.random() * 500
    
    return base_elevation + random.random() * 500

def calculate_destination_point(lng: float, lat: float, bearing: float, distance_km: float) -> Tuple[float, float]:
    R = 6371.0
    latRad = math.radians(lat)
    lngRad = math.radians(lng)
    bearingRad = math.radians(bearing)
    distRatio = distance_km / R
    
    destLat = math.asin(math.sin(latRad) * math.cos(distRatio) + 
                       math.cos(latRad) * math.sin(distRatio) * math.cos(bearingRad))
    destLng = lngRad + math.atan2(math.sin(bearingRad) * math.sin(distRatio) * math.cos(latRad),
                                 math.cos(distRatio) - math.sin(latRad) * math.sin(destLat))
    
    return math.degrees(destLng), math.degrees(destLat)

def calculate_sand_source_factor(lng: float, lat: float, wind_speed: float, wind_direction: float) -> float:
    source_factor = 0
    effective_radius = 300 + wind_speed * 5
    
    for source in SAND_SOURCE_AREAS:
        source_center_lat = (source["min_lat"] + source["max_lat"]) / 2
        source_center_lng = (source["min_lng"] + source["max_lng"]) / 2
        distance = haversine_distance(lng, lat, source_center_lng, source_center_lat)
        
        if distance < effective_radius:
            bearing_to_source = calculate_bearing(lng, lat, source_center_lng, source_center_lat)
            wind_angle_diff = abs(normalize_angle(bearing_to_source - wind_direction))
            
            alignment_factor = max(0, math.cos(math.radians(wind_angle_diff)))
            distance_factor = 1.0 - min(1.0, distance / effective_radius)
            contribution = source["intensity"] * alignment_factor * distance_factor * 0.8
            source_factor = max(source_factor, contribution)
    
    return min(1.0, source_factor)

def calculate_terrain_blocking_factor(station_lng: float, station_lat: float, 
                                       station_elevation: float, wind_speed: float, 
                                       wind_direction: float) -> float:
    block_factor = 1.0
    upwind_direction = normalize_angle(wind_direction + 180)
    sample_distance = 50
    sample_count = 10
    
    max_elevation_diff = 0
    max_blocking_angle = 0
    
    for i in range(1, sample_count + 1):
        distance = sample_distance * i
        upwind_lng, upwind_lat = calculate_destination_point(station_lng, station_lat, upwind_direction, distance)
        sampled_elevation = get_terrain_elevation(upwind_lng, upwind_lat)
        elevation_diff = sampled_elevation - station_elevation
        
        if elevation_diff > 0:
            blocking_angle = math.degrees(math.atan2(elevation_diff, distance * 1000))
            if blocking_angle > max_blocking_angle:
                max_blocking_angle = blocking_angle
            if elevation_diff > max_elevation_diff:
                max_elevation_diff = elevation_diff
    
    for mountain in MAJOR_MOUNTAINS:
        m_center_lat = (mountain["min_lat"] + mountain["max_lat"]) / 2
        m_center_lng = (mountain["min_lng"] + mountain["max_lng"]) / 2
        m_height = mountain["height"]
        distance = haversine_distance(station_lng, station_lat, m_center_lng, m_center_lat)
        
        if distance < 500:
            bearing_to_mountain = calculate_bearing(station_lng, station_lat, m_center_lng, m_center_lat)
            angle_diff = abs(normalize_angle(bearing_to_mountain - upwind_direction))
            
            if angle_diff < 45:
                elevation_diff = m_height - station_elevation
                if elevation_diff > 0:
                    blocking_angle = math.degrees(math.atan2(elevation_diff, distance * 1000))
                    wind_factor = min(1.0, wind_speed / 50.0)
                    mountain_block = max(0, 1.0 - blocking_angle / 30.0 * (1.0 - wind_factor * 0.5))
                    block_factor = min(block_factor, mountain_block)
    
    if max_blocking_angle > 0:
        local_block = max(0, 1.0 - max_blocking_angle / 20.0)
        block_factor = min(block_factor, local_block)
    
    return max(0.1, block_factor)

def calculate_sandstorm_probability(wind_speed: float, humidity: float,
                                     temperature: float, terrain: str, season: str,
                                     station_lng: float = None, station_lat: float = None,
                                     station_elevation: float = None, wind_direction: float = 180) -> float:
    wind_factor = min(1.0, wind_speed / 40.0)
    humidity_factor = max(0.0, 1.0 - humidity / 30.0)
    temp_factor = min(1.0, max(0.0, (temperature - 10) / 30.0))
    
    terrain_factors = {
        "DESERT": 0.9, "SAND_DUNES": 0.95, "DESERT_STEPPE": 0.7,
        "OASIS": 0.1, "MOUNTAINS": 0.2, "HIGH_MOUNTAINS": 0.15,
        "PLAINS": 0.4, "PLATEAU": 0.5, "HILLS": 0.3
    }
    terrain_factor = terrain_factors.get(terrain, 0.5)
    
    season_factors = {"spring": 0.7, "summer": 0.8, "autumn": 0.6, "winter": 0.2}
    season_factor = season_factors.get(season, 0.5)
    
    sand_source_factor = 0
    terrain_block_factor = 1.0
    sand_transport_factor = 0
    
    if station_lng is not None and station_lat is not None:
        sand_source_factor = calculate_sand_source_factor(station_lng, station_lat, wind_speed, wind_direction)
        terrain_block_factor = calculate_terrain_blocking_factor(
            station_lng, station_lat, station_elevation or 1000, wind_speed, wind_direction)
        
        velocity_factor = min(1.0, wind_speed / 50.0) ** 1.5
        elevation_factor = max(0.3, 1.0 - (station_elevation or 1000) / 4000.0)
        sand_transport_factor = velocity_factor * elevation_factor
    
    probability = (wind_factor * 0.25 + humidity_factor * 0.15 +
                   temp_factor * 0.1 + terrain_factor * 0.12 +
                   season_factor * 0.08 + sand_source_factor * 0.2 +
                   sand_transport_factor * 0.1)
    
    probability *= terrain_block_factor
    
    return min(1.0, max(0.0, probability))

def init_mqtt() -> bool:
    global _mqtt_client, _mqtt_connected
    if not HAS_MQTT or not MQTT_ENABLED:
        return False
    
    try:
        _mqtt_client = mqtt.Client(client_id=f"silkroad-simulator-{random.randint(1000,9999)}")
        _mqtt_client.connect(MQTT_BROKER, MQTT_PORT, keepalive=60)
        _mqtt_client.loop_start()
        _mqtt_connected = True
        print(f"  MQTT: 已连接到 {MQTT_BROKER}:{MQTT_PORT}")
        return True
    except Exception as e:
        print(f"  MQTT 连接失败: {e}")
        _mqtt_connected = False
        return False

def publish_mqtt(station_id: int, report: Dict) -> bool:
    if not _mqtt_connected or not _mqtt_client:
        return False
    try:
        topic = f"{MQTT_TOPIC}/{station_id}"
        payload = json.dumps(report)
        _mqtt_client.publish(topic, payload, qos=0)
        return True
    except Exception as e:
        print(f"  MQTT发布失败: {e}")
        return False

def apply_extreme_weather(temperature: float, wind_speed: float, humidity: float,
                           sandstorm_prob: float, mode: str) -> Tuple[float, float, float, float]:
    if mode not in EXTREME_WEATHER_MODES:
        return temperature, wind_speed, humidity, sandstorm_prob
    
    params = EXTREME_WEATHER_MODES[mode]
    new_temp = temperature + params["temp_offset"]
    new_wind = wind_speed * params["wind_multiplier"]
    new_humidity = max(2, humidity * params["humidity_multiplier"])
    new_sandstorm = min(1.0, sandstorm_prob + params["sandstorm_boost"])
    
    return round(new_temp, 1), round(new_wind, 1), round(new_humidity, 1), round(min(1.0, new_sandstorm), 3)

def generate_weather_data(station: Dict, current_time: datetime) -> Dict:
    season = get_season(current_time.month)
    hour = current_time.hour
    
    temp_params = SEASON_BASE_TEMPS[season]
    daily_variation = -7 * math.cos((hour - 14) * math.pi / 12)
    elevation_correction = (station["elevation"] - 1000) / 1000 * 6
    temperature = temp_params["base"] + daily_variation - elevation_correction + random.gauss(0, 2)
    temperature = round(temperature, 1)
    
    wind_params = SEASON_WIND[season]
    hour_factor = 1.3 if 10 <= hour <= 18 else 0.7
    wind_speed = max(0, wind_params["base"] * hour_factor + random.gauss(0, wind_params["variance"] / 2))
    wind_speed = round(wind_speed, 1)
    wind_direction = random.randint(0, 359)
    
    humidity_params = SEASON_HUMIDITY[season]
    humidity = max(5, min(95, humidity_params["base"] - wind_speed * 0.5 + random.gauss(0, humidity_params["variance"] / 3)))
    humidity = round(humidity, 1)
    
    precipitation = 0.0
    if random.random() < 0.1:
        precipitation = round(random.random() * 5, 1)
    
    terrain = get_terrain_type(station)
    sandstorm_prob = calculate_sandstorm_probability(
        wind_speed, humidity, temperature, terrain, season,
        station["lng"], station["lat"], station["elevation"], wind_direction
    )
    sandstorm_prob = round(sandstorm_prob, 3)
    
    visibility = 10.0
    if sandstorm_prob > 0.5:
        visibility = 10 * (1 - sandstorm_prob * 0.8)
    visibility = round(max(0.1, visibility), 1)
    
    air_pressure = round(1013 - station["elevation"] * 0.012 + random.gauss(0, 3), 1)
    
    if EXTREME_WEATHER and EXTREME_WEATHER in EXTREME_WEATHER_MODES:
        temperature, wind_speed, humidity, sandstorm_prob = apply_extreme_weather(
            temperature, wind_speed, humidity, sandstorm_prob, EXTREME_WEATHER
        )
        visibility = 10.0
        if sandstorm_prob > 0.5:
            visibility = 10 * (1 - sandstorm_prob * 0.8)
        visibility = round(max(0.1, visibility), 1)
    
    return {
        "stationId": station["id"],
        "reportTime": current_time.isoformat(),
        "temperatureC": temperature,
        "precipitationMm": precipitation,
        "windSpeedKmh": wind_speed,
        "windDirection": wind_direction,
        "humidityPct": humidity,
        "sandstormProbability": sandstorm_prob,
        "visibilityKm": visibility,
        "airPressureHpa": air_pressure
    }

def submit_report(station_id: int, report: Dict, use_mock: bool = False, use_mqtt: bool = False) -> bool:
    if use_mqtt and _mqtt_connected:
        publish_mqtt(station_id, report)
    
    if use_mock:
        print(f"  [模拟] 气象站 {station_id}: {report['temperatureC']}°C, "
              f"风速 {report['windSpeedKmh']}km/h, 沙尘暴概率 {report['sandstormProbability']*100:.1f}%")
        return True
    
    try:
        url = f"{API_BASE_URL}/weather/reports/{station_id}"
        response = requests.post(url, json=report, timeout=5)
        if response.status_code == 200:
            print(f"  ✓ 气象站 {station_id} 数据上报成功")
            return True
        else:
            print(f"  ✗ 气象站 {station_id} 上报失败: HTTP {response.status_code}")
            return False
    except requests.exceptions.RequestException as e:
        print(f"  ✗ 气象站 {station_id} 连接失败: {e}")
        return False

def run_simulation(interval_seconds: int = 3600, speed_factor: int = 1,
                    use_mock: bool = True, use_mqtt: bool = False):
    print("=" * 60)
    print("  古代驼队丝绸之路 - 气象站模拟器")
    print("=" * 60)
    print(f"  气象站数量: {len(STATIONS)}")
    print(f"  上报间隔: {interval_seconds} 秒 (模拟 {interval_seconds / 3600 / speed_factor:.1f} 小时)")
    print(f"  模式: {'模拟' if use_mock else 'API上报'}")
    if SEASON_OVERRIDE:
        print(f"  季节: {SEASON_OVERRIDE} (固定)")
    if EXTREME_WEATHER and EXTREME_WEATHER in EXTREME_WEATHER_MODES:
        print(f"  极端天气: {EXTREME_WEATHER_MODES[EXTREME_WEATHER]['description']}")
    if use_mqtt or MQTT_ENABLED:
        print(f"  MQTT: {MQTT_BROKER}:{MQTT_PORT} -> {MQTT_TOPIC}")
    print("=" * 60)
    
    if use_mqtt or MQTT_ENABLED:
        init_mqtt()
    
    current_time = datetime.now().replace(minute=0, second=0, microsecond=0)
    report_count = 0
    
    try:
        while True:
            print(f"\n[{current_time.strftime('%Y-%m-%d %H:%M:%S')}] 开始上报气象数据...")
            
            success_count = 0
            for station in STATIONS:
                report = generate_weather_data(station, current_time)
                if submit_report(station["id"], report, use_mock, use_mqtt or MQTT_ENABLED):
                    success_count += 1
                    report_count += 1
            
            print(f"本次上报: {success_count}/{len(STATIONS)} 站成功 | 累计上报: {report_count} 条")
            
            high_risk_stations = []
            for station in STATIONS:
                report = generate_weather_data(station, current_time)
                if report["sandstormProbability"] >= 0.6:
                    high_risk_stations.append(station["name"])
            
            if high_risk_stations:
                print(f"⚠️  沙尘暴预警: {', '.join(high_risk_stations)}")
            
            current_time += timedelta(hours=1)
            sleep_time = interval_seconds / speed_factor
            time.sleep(sleep_time)
            
    except KeyboardInterrupt:
        print(f"\n\n模拟器已停止，累计上报 {report_count} 条气象数据")

def batch_backfill(start_date: str, end_date: str, use_mock: bool = False, use_mqtt: bool = False):
    print("=" * 60)
    print("  历史气象数据回填")
    print("=" * 60)
    
    start = datetime.fromisoformat(start_date)
    end = datetime.fromisoformat(end_date)
    
    total_hours = int((end - start).total_seconds() / 3600)
    total_reports = total_hours * len(STATIONS)
    
    print(f"  时间范围: {start_date} 至 {end_date}")
    print(f"  预计数据量: {total_reports} 条")
    if SEASON_OVERRIDE:
        print(f"  季节: {SEASON_OVERRIDE} (固定)")
    if EXTREME_WEATHER:
        print(f"  极端天气: {EXTREME_WEATHER}")
    print("=" * 60)
    
    if use_mqtt or MQTT_ENABLED:
        init_mqtt()
    
    current = start
    count = 0
    
    try:
        while current < end:
            for station in STATIONS:
                report = generate_weather_data(station, current)
                submit_report(station["id"], report, use_mock, use_mqtt or MQTT_ENABLED)
                count += 1
            
            if count % 200 == 0:
                print(f"进度: {count}/{total_reports} ({count/total_reports*100:.1f}%)")
            
            current += timedelta(hours=1)
    
    except KeyboardInterrupt:
        print(f"\n回填中断，已生成 {count} 条数据")
        return
    
    print(f"\n✓ 数据回填完成，共生成 {count} 条气象数据")

if __name__ == "__main__":
    import argparse
    
    parser = argparse.ArgumentParser(description="丝绸之路气象站模拟器")
    parser.add_argument("--mode", choices=["live", "backfill", "demo"],
                        default=SIMULATOR_MODE, help="运行模式")
    parser.add_argument("--interval", type=int, default=REPORT_INTERVAL,
                        help="上报间隔（秒）")
    parser.add_argument("--speed", type=int, default=SPEED_FACTOR,
                        help="时间加速因子")
    parser.add_argument("--start-date", type=str,
                        default="2020-01-01T00:00:00", help="回填开始时间")
    parser.add_argument("--end-date", type=str,
                        default="2020-01-07T00:00:00", help="回填结束时间")
    parser.add_argument("--mock", action="store_true", default=True,
                        help="使用模拟模式，不调用API")
    parser.add_argument("--api", action="store_true", default=False,
                        help="使用真实API上报")
    parser.add_argument("--season", choices=["spring", "summer", "autumn", "winter"],
                        default="", help="固定季节，不按月份自动切换")
    parser.add_argument("--extreme", choices=["sandstorm", "heatwave", "coldwave", "drought"],
                        default="", help="极端天气模式")
    parser.add_argument("--mqtt", action="store_true", default=MQTT_ENABLED,
                        help="启用MQTT发布")
    parser.add_argument("--mqtt-broker", type=str, default=MQTT_BROKER,
                        help="MQTT Broker地址")
    parser.add_argument("--mqtt-port", type=int, default=MQTT_PORT,
                        help="MQTT端口")
    parser.add_argument("--mqtt-topic", type=str, default=MQTT_TOPIC,
                        help="MQTT主题前缀")
    
    args = parser.parse_args()
    use_mock = not args.api
    
    if args.season:
        SEASON_OVERRIDE = args.season
    if args.extreme:
        EXTREME_WEATHER = args.extreme
    if args.mqtt_broker:
        MQTT_BROKER = args.mqtt_broker
    if args.mqtt_port:
        MQTT_PORT = args.mqtt_port
    if args.mqtt_topic:
        MQTT_TOPIC = args.mqtt_topic
    
    if args.mode == "live":
        run_simulation(interval_seconds=args.interval, speed_factor=args.speed,
                       use_mock=use_mock, use_mqtt=args.mqtt)
    elif args.mode == "backfill":
        batch_backfill(args.start_date, args.end_date, use_mock=use_mock, use_mqtt=args.mqtt)
    else:
        print("演示模式：模拟24小时数据，每0.5秒更新一次")
        run_simulation(interval_seconds=0.5, speed_factor=7200, use_mock=True, use_mqtt=args.mqtt)
