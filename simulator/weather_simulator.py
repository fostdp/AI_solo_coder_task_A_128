#!/usr/bin/env python3
"""
古代驼队丝绸之路 - 气象站模拟器
模拟20个气象站每小时上报温度、降水、风速、沙尘暴概率等数据
"""

import requests
import json
import time
import random
import math
from datetime import datetime, timedelta
from typing import Dict, List, Tuple

API_BASE_URL = "http://localhost:8080/api"

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

def get_season(month: int) -> str:
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

def calculate_sandstorm_probability(wind_speed: float, humidity: float,
                                     temperature: float, terrain: str, season: str) -> float:
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
    
    probability = (wind_factor * 0.35 + humidity_factor * 0.25 +
                   temp_factor * 0.15 + terrain_factor * 0.15 +
                   season_factor * 0.1)
    
    return min(1.0, max(0.0, probability))

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
    sandstorm_prob = calculate_sandstorm_probability(wind_speed, humidity, temperature, terrain, season)
    sandstorm_prob = round(sandstorm_prob, 3)
    
    visibility = 10.0
    if sandstorm_prob > 0.5:
        visibility = 10 * (1 - sandstorm_prob * 0.8)
    visibility = round(max(0.1, visibility), 1)
    
    air_pressure = round(1013 - station["elevation"] * 0.012 + random.gauss(0, 3), 1)
    
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

def submit_report(station_id: int, report: Dict, use_mock: bool = False) -> bool:
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

def run_simulation(interval_seconds: int = 3600, speed_factor: int = 1, use_mock: bool = True):
    print("=" * 60)
    print("  古代驼队丝绸之路 - 气象站模拟器")
    print("=" * 60)
    print(f"  气象站数量: {len(STATIONS)}")
    print(f"  上报间隔: {interval_seconds} 秒 (模拟 {interval_seconds / 3600 / speed_factor:.1f} 小时)")
    print(f"  模式: {'模拟' if use_mock else 'API上报'}")
    print("=" * 60)
    
    current_time = datetime.now().replace(minute=0, second=0, microsecond=0)
    report_count = 0
    
    try:
        while True:
            print(f"\n[{current_time.strftime('%Y-%m-%d %H:%M:%S')}] 开始上报气象数据...")
            
            success_count = 0
            for station in STATIONS:
                report = generate_weather_data(station, current_time)
                if submit_report(station["id"], report, use_mock):
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

def batch_backfill(start_date: str, end_date: str, use_mock: bool = False):
    print("=" * 60)
    print("  历史气象数据回填")
    print("=" * 60)
    
    start = datetime.fromisoformat(start_date)
    end = datetime.fromisoformat(end_date)
    
    total_hours = int((end - start).total_seconds() / 3600)
    total_reports = total_hours * len(STATIONS)
    
    print(f"  时间范围: {start_date} 至 {end_date}")
    print(f"  预计数据量: {total_reports} 条")
    print("=" * 60)
    
    current = start
    count = 0
    
    try:
        while current < end:
            for station in STATIONS:
                report = generate_weather_data(station, current)
                submit_report(station["id"], report, use_mock)
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
                        default="demo", help="运行模式")
    parser.add_argument("--interval", type=int, default=3,
                        help="上报间隔（秒），默认3秒（模拟1小时）")
    parser.add_argument("--speed", type=int, default=1200,
                        help="时间加速因子，默认1200倍速")
    parser.add_argument("--start-date", type=str,
                        default="2020-01-01T00:00:00", help="回填开始时间")
    parser.add_argument("--end-date", type=str,
                        default="2020-01-07T00:00:00", help="回填结束时间")
    parser.add_argument("--mock", action="store_true", default=True,
                        help="使用模拟模式，不调用API")
    parser.add_argument("--api", action="store_true", default=False,
                        help="使用真实API上报")
    
    args = parser.parse_args()
    use_mock = not args.api
    
    if args.mode == "live":
        run_simulation(interval_seconds=args.interval, speed_factor=args.speed, use_mock=use_mock)
    elif args.mode == "backfill":
        batch_backfill(args.start_date, args.end_date, use_mock=use_mock)
    else:
        print("演示模式：模拟24小时数据，每0.5秒更新一次")
        run_simulation(interval_seconds=0.5, speed_factor=7200, use_mock=True)
