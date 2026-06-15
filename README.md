# 古代驼队丝绸之路 - 路径规划与气象风险分析系统

基于 Spring Boot + PostgreSQL/PostGIS + Leaflet 的丝绸之路驼队路径规划与气象风险分析系统，支持 A* + 遗传算法混合路径规划、八因子沙尘暴概率模型、WebSocket 实时告警、气象站模拟等功能。

## 系统架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                           Frontend (Nginx)                          │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌──────────┐  │
│  │ SilkRoadMap │  │ RiskPanel   │  │ WebGL       │  │ WebSocket│  │
│  │ (Leaflet)   │  │ (UI面板)    │  │ (热力图)    │  │ (告警)   │  │
│  └──────┬──────┘  └──────┬──────┘  └─────────────┘  └────┬─────┘  │
│         │                  │                               │        │
│         └───────────┬──────┴───────────────────────────────┘        │
│                     │ REST / WebSocket / SockJS                     │
└─────────────────────┼───────────────────────────────────────────────┘
                      │
┌─────────────────────┴───────────────────────────────────────────────┐
│                    Spring Boot Backend                              │
│                                                                     │
│  ┌──────────────┐  Spring Events  ┌──────────────────────────────┐ │
│  │ dtu_receiver │ ──────────────► │ weather_risk_analyzer        │ │
│  │ (数据采集校验)│  WeatherReport  │ (8因子沙尘暴模型+地形遮挡)    │ │
│  └──────┬───────┘   ReceivedEvent └───────────────┬──────────────┘ │
│         │                                          │                │
│         │                                   RiskAssessmentEvent    │
│         │                                          │                │
│         │                                          ▼                │
│  ┌──────▼───────┐                         ┌──────────────────┐     │
│  │ route_planner│                         │ alarm_ws         │     │
│  │ (A* + GA)    │                         │ (WebSocket告警)  │     │
│  │ 路径规划     │                         └──────────────────┘     │
│  └──────────────┘                                                  │
│                                                                     │
│  ┌───────────────────────────┐  ┌───────────────────────────────┐  │
│  │ Actuator / Prometheus     │  │ Spring Data JPA / Hibernate   │  │
│  │ (监控指标)                │  │ Spatial (PostGIS)             │  │
│  └───────────────────────────┘  └──────────────┬────────────────┘  │
└────────────────────────────────────────────────┼───────────────────┘
                                                 │
                        ┌────────────────────────┴─────────┐
                        │                                  │
                ┌───────▼───────┐                 ┌──────▼───────┐
                │ PostgreSQL    │                 │  MQTT Broker │
                │  + PostGIS    │                 │ (Mosquitto)  │
                │ (空间数据库)  │                 └──────┬───────┘
                └───────────────┘                        │
                                                         │
                                                ┌────────▼───────┐
                                                │ Weather        │
                                                │ Simulator      │
                                                │ (气象站模拟器) │
                                                └────────────────┘
```

### 核心模块

| 模块 | 职责 | 技术 |
|------|------|------|
| **dtu_receiver** | 气象数据采集与校验 | Spring Boot, Validation, Spring Events |
| **route_planner** | 路径规划与优化 | A\* 算法, 遗传算法, JTS, PostGIS |
| **weather_risk_analyzer** | 沙尘暴预测与风险评估 | 8因子模型, 地形遮挡因子, PostGIS |
| **alarm_ws** | 告警评估与 WebSocket 推送 | Spring WebSocket, STOMP, SockJS |

### 技术栈

- **后端**: Java 17, Spring Boot 3.2.5, Spring Data JPA, Hibernate Spatial, JTS
- **数据库**: PostgreSQL 16 + PostGIS 3.4 (GiST 空间索引)
- **前端**: Leaflet, Canvas 2D, WebGL, WebSocket/STOMP
- **消息中间件**: Eclipse Mosquitto (MQTT 3.1.1)
- **容器化**: Docker 多阶段构建, docker-compose
- **监控**: Spring Boot Actuator, Prometheus
- **前端服务器**: Nginx (Gzip/Brotli 压缩)

## 快速部署

### 前置要求

- Docker >= 20.10
- Docker Compose >= 2.0
- 至少 4GB 可用内存

### 一键启动

```bash
# 克隆项目
git clone <repository-url>
cd silk-road-system

# 启动所有服务
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f backend
```

### 访问地址

| 服务 | 地址 | 说明 |
|------|------|------|
| 前端 | http://localhost | 丝绸之路地图系统 |
| 后端 API | http://localhost/api | REST API 入口 |
| Actuator 健康检查 | http://localhost/api/actuator/health | 健康状态 |
| Prometheus 指标 | http://localhost/api/actuator/prometheus | 监控指标 |
| MQTT Broker | localhost:1883 | MQTT 协议 |
| MQTT WebSocket | localhost:9001 | MQTT over WebSocket |
| PostgreSQL | localhost:5432 | 数据库 (silkroad/silkroad123) |

### 服务依赖顺序

```
postgis (健康检查)
    ↓
  backend (等待 postgis 就绪)
    ↓
  frontend (依赖 backend)
  weather-simulator (依赖 backend + mqtt)
```

## 部署步骤详解

### 1. 数据库初始化

PostGIS 容器首次启动时会自动执行 `database/init.sql`，包括：

- 9 张核心表创建（路线、航点、气象站、气象报告、驼队、地形网格、水源、季节风险、告警）
- GiST 空间索引（routes, waypoints, weather_stations, caravans, terrain_grid, water_sources, alerts）
- 10 条丝绸之路历史路线数据
- 20 个气象站初始化数据
- 数据库调优配置（`deploy/postgres-tuning.conf`）

### 2. 后端服务

Spring Boot 多阶段构建 Docker 镜像：

```dockerfile
# 阶段1: Maven 构建
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn package -DskipTests

# 阶段2: JRE 运行时
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
```

Layertools 分层提取优化镜像缓存：

- `dependencies/` - 第三方依赖（很少变化）
- `spring-boot-loader/` - Spring Boot 加载器
- `snapshot-dependencies/` - SNAPSHOT 依赖
- `application/` - 应用代码（最常变化）

### 3. 前端服务

Nginx Alpine 镜像，启用 Gzip/Brotli 压缩：

```nginx
gzip on;
gzip_vary on;
gzip_proxied any;
gzip_comp_level 6;
gzip_types text/plain text/css text/xml text/javascript
           application/javascript application/json application/xml;
```

反向代理配置：

- `/api` → `backend:8080` (REST API)
- `/ws` → `backend:8080` (WebSocket 升级)
- 静态资源 7 天浏览器缓存

### 4. MQTT Broker

Eclipse Mosquitto 2.0：

- 1883 端口: MQTT TCP
- 9001 端口: MQTT over WebSocket
- 匿名访问（开发环境，生产环境请配置认证）

### 5. 气象站模拟器

Python 模拟器，支持 MQTT 发布和 HTTP API 上报两种模式。

## 气象站模拟器使用

### Docker 环境变量配置

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `SIMULATOR_MODE` | `demo` | 运行模式: `live`, `backfill`, `demo` |
| `SEASON` | 空（自动） | 固定季节: `spring`, `summer`, `autumn`, `winter` |
| `EXTREME_WEATHER` | `false` | 极端天气模式: `sandstorm`, `heatwave`, `coldwave`, `drought` |
| `REPORT_INTERVAL` | `3600` | 上报间隔（秒） |
| `SPEED_FACTOR` | `1200` | 时间加速因子 |
| `BACKEND_URL` | `http://backend:8080/api` | 后端 API 地址 |
| `MQTT_ENABLED` | `false` | 是否启用 MQTT 发布 |
| `MQTT_BROKER` | `tcp://mqtt:1883` | MQTT Broker 地址 |
| `MQTT_TOPIC` | `silkroad/weather` | MQTT 主题前缀 |

### 本地运行

```bash
cd simulator
pip install -r requirements.txt

# 演示模式（默认）
python weather_simulator.py

# 实时模式 + 夏季
python weather_simulator.py --mode live --season summer

# 强沙尘暴模式
python weather_simulator.py --extreme sandstorm

# 使用 MQTT 发布
python weather_simulator.py --mqtt --mqtt-broker localhost --mqtt-port 1883

# 历史数据回填
python weather_simulator.py --mode backfill \
  --start-date 2020-01-01T00:00:00 \
  --end-date 2020-01-07T00:00:00
```

### Docker Compose 切换模式

修改 `docker-compose.yml` 中 `weather-simulator` 服务的环境变量：

```yaml
weather-simulator:
  environment:
    SIMULATOR_MODE: live
    SEASON: summer
    EXTREME_WEATHER: sandstorm
    MQTT_ENABLED: "true"
```

然后重启：

```bash
docker-compose up -d --no-deps weather-simulator
```

### 极端天气模式说明

| 模式 | 效果 |
|------|------|
| **sandstorm** | 风速 ×2.5，湿度 ×0.3，沙尘暴概率 +0.4 |
| **heatwave** | 温度 +15°C，风速 ×1.2，湿度 ×0.5 |
| **coldwave** | 温度 -20°C，风速 ×1.8，湿度 ×0.8 |
| **drought** | 温度 +8°C，风速 ×1.5，湿度 ×0.2，沙尘暴 +0.25 |

### 季节参数

| 季节 | 基准温度 | 基准风速 | 基准湿度 | 沙尘暴因子 |
|------|---------|---------|---------|-----------|
| **春季** | 15°C | 25 km/h | 35% | 0.7 |
| **夏季** | 30°C | 20 km/h | 25% | 0.8 |
| **秋季** | 12°C | 22 km/h | 30% | 0.6 |
| **冬季** | -5°C | 30 km/h | 45% | 0.2 |

## 监控与运维

### Actuator 端点

| 端点 | 路径 | 说明 |
|------|------|------|
| 健康检查 | `/api/actuator/health` | 应用健康状态 |
| 存活探针 | `/api/actuator/health/liveness` | Kubernetes liveness |
| 就绪探针 | `/api/actuator/health/readiness` | Kubernetes readiness |
| 应用信息 | `/api/actuator/info` | 应用版本信息 |
| 指标 | `/api/actuator/metrics` | JVM、HTTP、数据源指标 |
| Prometheus | `/api/actuator/prometheus` | Prometheus 格式指标 |

### Prometheus 集成

添加 Prometheus 抓取配置：

```yaml
scrape_configs:
  - job_name: 'silkroad-backend'
    metrics_path: '/api/actuator/prometheus'
    static_configs:
      - targets: ['localhost:8080']
```

### 常用运维命令

```bash
# 查看所有服务状态
docker-compose ps

# 查看后端日志
docker-compose logs -f backend

# 重启后端
docker-compose restart backend

# 查看数据库
docker exec -it silkroad-postgis psql -U silkroad -d silkroad

# 清空数据并重新初始化
docker-compose down -v
docker-compose up -d
```

## 项目结构

```
silk-road-system/
├── backend/                    # Spring Boot 后端
│   ├── src/main/java/com/silkroad/
│   │   ├── dtu_receiver/       # 数据采集模块
│   │   ├── route_planner/      # 路径规划模块
│   │   ├── weather_risk_analyzer/  # 气象风险分析模块
│   │   ├── alarm_ws/           # 告警 WebSocket 模块
│   │   └── common/event/       # 公共事件 (Spring Events)
│   ├── src/main/resources/
│   │   ├── application.yml     # 主配置
│   │   ├── application-docker.yml  # Docker 环境配置
│   │   ├── terrain-params.yml  # 地形参数
│   │   └── weather-params.yml  # 气象参数
│   ├── Dockerfile              # 多阶段构建
│   └── pom.xml
├── frontend/                   # 前端静态资源
│   ├── js/
│   │   ├── silk_road_map.js    # 地图模块
│   │   ├── risk_panel.js       # 面板模块
│   │   ├── webgl-heatmap.js    # WebGL 热力图
│   │   └── app.js              # 协调层
│   ├── css/
│   ├── index.html
│   ├── Dockerfile
│   ├── nginx.conf              # Gzip/Brotli 配置
│   └── default.conf            # 反向代理配置
├── database/
│   └── init.sql                # PostGIS 初始化脚本
├── simulator/                  # 气象站模拟器
│   ├── weather_simulator.py
│   ├── requirements.txt
│   └── Dockerfile
├── deploy/                     # 部署配置
│   ├── mosquitto.conf          # MQTT Broker 配置
│   └── postgres-tuning.conf    # PostgreSQL 调优
├── docker-compose.yml          # 容器编排
└── README.md
```

## API 简介

### 气象数据

- `POST /api/weather/reports/{stationId}` - 提交气象报告
- `GET /api/weather/reports?stationId={id}&start={time}&end={time}` - 查询历史数据
- `GET /api/weather/stations` - 获取气象站列表

### 路径规划

- `POST /api/path/find?startLat={lat}&startLng={lng}&endLat={lat}&endLng={lng}` - 路径规划
- `GET /api/routes` - 获取所有路线
- `GET /api/routes/{id}` - 获取路线详情

### 风险分析

- `GET /api/risk/assess?routeId={id}&season={season}` - 路线风险评估
- `GET /api/risk/heatmap?bbox={minLng,minLat,maxLng,maxLat}` - 热力图数据

### 驼队管理

- `POST /api/caravans` - 创建驼队
- `GET /api/caravans` - 获取驼队列表
- `POST /api/caravans/{id}/dispatch` - 派遣驼队

### WebSocket 告警

- 连接端点: `/ws/weather`
- 订阅主题: `/topic/alerts`, `/topic/weather`, `/topic/caravans`
- 协议: STOMP over SockJS

## 许可证

MIT License
