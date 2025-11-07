# EFK Sidecar 快速开始指南

## 🚀 5 分钟部署

### 1. 部署 EFK 基础组件

```bash
cd efk-sidecar-deployment
./deploy.sh your-namespace
```

### 2. 修改业务应用 Deployment

在你的应用 YAML 中添加以下配置：

```yaml
spec:
  template:
    spec:
      containers:
      # 现有业务容器
      - name: your-app
        # ... 现有配置 ...
        volumeMounts:
        - name: app-logs
          mountPath: /var/log/app  # 👈 添加日志卷挂载

      # 👇 添加 Fluent Bit Sidecar
      - name: fluent-bit
        image: fluent/fluent-bit:2.2.0
        volumeMounts:
        - name: app-logs
          mountPath: /var/log/app
          readOnly: true
        - name: fluent-bit-config
          mountPath: /fluent-bit/etc/fluent-bit.conf
          subPath: fluent-bit.conf
        - name: fluent-bit-config
          mountPath: /fluent-bit/etc/parsers.conf
          subPath: parsers.conf
        resources:
          requests:
            memory: 100Mi
            cpu: 100m
          limits:
            memory: 200Mi
            cpu: 200m

      # 👇 添加 Volumes
      volumes:
      - name: app-logs
        emptyDir: {}
      - name: fluent-bit-config
        configMap:
          name: fluent-bit-config
```

### 3. 配置应用输出 JSON 日志

#### Java (Logback + Logstash Encoder)

**pom.xml:**
```xml
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.4</version>
</dependency>
```

**logback-spring.xml:**
```xml
<configuration>
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>/var/log/app/app.log</file>
        <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
    </appender>
    <root level="INFO">
        <appender-ref ref="FILE"/>
    </root>
</configuration>
```

#### Go (Logrus)

```go
import (
    "os"
    "github.com/sirupsen/logrus"
)

func init() {
    logrus.SetFormatter(&logrus.JSONFormatter{})
    file, _ := os.OpenFile("/var/log/app/app.log",
        os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0666)
    logrus.SetOutput(file)
}
```

#### Node.js (Winston)

```javascript
const winston = require('winston');

const logger = winston.createLogger({
  format: winston.format.json(),
  transports: [
    new winston.transports.File({
      filename: '/var/log/app/app.log'
    })
  ]
});
```

#### Python (structlog)

```python
import structlog
import logging

logging.basicConfig(
    filename='/var/log/app/app.log',
    format='%(message)s'
)
structlog.configure(
    processors=[
        structlog.processors.JSONRenderer()
    ],
    logger_factory=structlog.stdlib.LoggerFactory(),
)
```

### 4. 重新部署应用

```bash
kubectl apply -f your-app.yaml
```

### 5. 访问 Kibana

```bash
# 获取访问地址
kubectl get svc kibana -n your-namespace

# 或使用端口转发
kubectl port-forward svc/kibana 5601:5601 -n your-namespace
```

访问: http://localhost:5601

### 6. 配置 Kibana Index Pattern

1. 打开 Kibana → Management → Index Patterns
2. 创建 Index Pattern: `app-logs-*`
3. Time field: `@timestamp`
4. 进入 Discover 查看日志

## ✅ 验证清单

- [ ] Elasticsearch Pod 运行中
- [ ] Kibana Pod 运行中
- [ ] 业务应用已添加 Fluent Bit Sidecar
- [ ] 应用日志输出到 `/var/log/app/`
- [ ] 日志格式为 JSON
- [ ] Kibana 中能看到日志数据

## 🐛 快速故障排查

```bash
# 1. 检查所有 Pod 状态
kubectl get pods -n your-namespace

# 2. 查看 Fluent Bit 日志
kubectl logs <your-pod> -c fluent-bit -n your-namespace

# 3. 检查应用日志文件
kubectl exec -it <your-pod> -c your-app -- ls -la /var/log/app/
kubectl exec -it <your-pod> -c your-app -- tail /var/log/app/app.log

# 4. 检查 Elasticsearch 索引
kubectl exec -it elasticsearch-0 -n your-namespace -- \
  curl localhost:9200/_cat/indices?v

# 5. 测试 Elasticsearch 连接
kubectl exec -it <your-pod> -c fluent-bit -- \
  wget -O- http://elasticsearch:9200
```

## 📊 常用命令

```bash
# 查看实时日志采集
kubectl logs -f <pod> -c fluent-bit -n your-namespace

# 检查 ES 集群健康
kubectl exec -it elasticsearch-0 -n your-namespace -- \
  curl localhost:9200/_cluster/health?pretty

# 清理旧索引（删除 7 天前）
kubectl exec -it elasticsearch-0 -n your-namespace -- \
  curl -X DELETE localhost:9200/app-logs-2025.01.01

# 查看日志索引大小
kubectl exec -it elasticsearch-0 -n your-namespace -- \
  curl localhost:9200/_cat/indices/app-logs-*?v
```

## 🔧 配置调整

### 修改日志路径

编辑 `fluent-bit/fluent-bit-configmap.yaml`:

```yaml
[INPUT]
    Path  /var/log/app/custom.log  # 改为你的路径
```

应用更改：
```bash
kubectl apply -f fluent-bit/fluent-bit-configmap.yaml
kubectl rollout restart deployment <your-app> -n your-namespace
```

### 增加 Elasticsearch 存储

```bash
# 编辑 elasticsearch.yaml
vim elasticsearch/elasticsearch.yaml

# 修改存储大小
storage: 50Gi  # 增加到 50G

# 重新部署（注意：会丢失数据）
kubectl delete -f elasticsearch/elasticsearch.yaml
kubectl apply -f elasticsearch/elasticsearch.yaml
```

## 🗑️ 清理

```bash
# 删除所有组件
./cleanup.sh your-namespace
```

## 📚 更多信息

详细文档: [README.md](README.md)

---

**快速链接:**
- [完整部署文档](README.md)
- [示例应用](examples/app-with-sidecar.yaml)
- [Elasticsearch 配置](elasticsearch/elasticsearch.yaml)
- [Kibana 配置](kibana/kibana.yaml)
