# EFK Sidecar 日志采集方案

基于 Sidecar 模式的 Elasticsearch + Fluent Bit + Kibana 日志采集系统，适用于只有 Namespace 权限的 Kubernetes 环境。

## 📋 目录结构

```
efk-sidecar-deployment/
├── elasticsearch/          # Elasticsearch 部署配置
│   └── elasticsearch.yaml
├── kibana/                 # Kibana 部署配置
│   └── kibana.yaml
├── fluent-bit/             # Fluent Bit 配置
│   └── fluent-bit-configmap.yaml
├── examples/               # 示例应用
│   └── app-with-sidecar.yaml
├── deploy.sh               # 一键部署脚本
└── README.md               # 本文档
```

## 🏗️ 架构说明

### 工作流程

```
业务容器 → 输出 JSON 日志到 /var/log/app/*.log
    ↓ (共享 Volume)
Fluent Bit Sidecar → 读取日志 → 解析 JSON → 发送到 Elasticsearch
    ↓
Elasticsearch → 存储和索引日志
    ↓
Kibana → 可视化查询
```

### 组件说明

| 组件 | 类型 | 资源配置 | 说明 |
|------|------|---------|------|
| **Elasticsearch** | StatefulSet | 512Mi-1Gi 内存 | 单节点模式，10Gi 存储 |
| **Kibana** | Deployment | 512Mi-1Gi 内存 | NodePort 访问 |
| **Fluent Bit** | Sidecar 容器 | 100Mi-200Mi 内存 | 每个业务 Pod 一个 |

## 🚀 快速开始

### 前置要求

- Kubernetes 集群访问权限
- kubectl 已配置
- 至少有一个 Namespace 的完全权限
- 集群有可用的 StorageClass（用于 PVC）

### 步骤 1: 部署 EFK 基础设施

```bash
# 克隆或下载配置文件
cd efk-sidecar-deployment

# 修改脚本权限
chmod +x deploy.sh

# 执行部署（替换为你的 namespace）
./deploy.sh your-namespace
```

或者手动部署：

```bash
# 设置 namespace
export NAMESPACE=your-namespace

# 替换所有配置文件中的 namespace
find . -name "*.yaml" -exec sed -i "s/your-namespace/$NAMESPACE/g" {} \;

# 部署 Elasticsearch
kubectl apply -f elasticsearch/elasticsearch.yaml

# 部署 Kibana
kubectl apply -f kibana/kibana.yaml

# 部署 Fluent Bit ConfigMap
kubectl apply -f fluent-bit/fluent-bit-configmap.yaml
```

### 步骤 2: 验证部署

```bash
# 检查 Pod 状态
kubectl get pods -n your-namespace

# 应该看到：
# elasticsearch-0   1/1     Running
# kibana-xxx        1/1     Running

# 检查服务
kubectl get svc -n your-namespace

# 获取 Kibana 访问地址（如果使用 NodePort）
kubectl get svc kibana -n your-namespace
```

### 步骤 3: 业务应用集成 Sidecar

#### 方式 A: 修改现有 Deployment

在你的业务应用 Deployment YAML 中添加以下内容：

```yaml
spec:
  template:
    spec:
      containers:
      # 你的业务容器
      - name: your-app
        image: your-image
        volumeMounts:
        - name: app-logs
          mountPath: /var/log/app  # 应用日志输出目录

      # 添加 Fluent Bit Sidecar
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
          limits:
            memory: 200Mi
            cpu: 200m
          requests:
            memory: 100Mi
            cpu: 100m

      volumes:
      - name: app-logs
        emptyDir: {}
      - name: fluent-bit-config
        configMap:
          name: fluent-bit-config
```

#### 方式 B: 使用示例模板

```bash
# 复制示例文件
cp examples/app-with-sidecar.yaml my-app.yaml

# 修改镜像和配置
vim my-app.yaml

# 部署
kubectl apply -f my-app.yaml
```

### 步骤 4: 配置应用日志输出

确保你的应用：

1. **日志输出到文件**（不是 stdout）
   ```
   /var/log/app/app.log
   ```

2. **日志格式为 JSON**
   ```json
   {"time":"2025-01-07T10:00:00.000Z","level":"INFO","msg":"example log","service":"my-service"}
   ```

#### Java 应用示例 (Logback)

```xml
<!-- logback-spring.xml -->
<configuration>
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>/var/log/app/app.log</file>
        <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>/var/log/app/app.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>7</maxHistory>
        </rollingPolicy>
    </appender>
    <root level="INFO">
        <appender-ref ref="FILE"/>
    </root>
</configuration>
```

依赖：
```xml
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.4</version>
</dependency>
```

#### Go 应用示例

```go
import "github.com/sirupsen/logrus"

func init() {
    logrus.SetFormatter(&logrus.JSONFormatter{})
    file, _ := os.OpenFile("/var/log/app/app.log", os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0666)
    logrus.SetOutput(file)
}
```

### 步骤 5: 访问 Kibana

1. **获取访问地址**

```bash
# NodePort 方式
kubectl get svc kibana -n your-namespace
# 访问: http://<节点IP>:<NodePort>

# Port Forward 方式（本地测试）
kubectl port-forward svc/kibana 5601:5601 -n your-namespace
# 访问: http://localhost:5601
```

2. **配置 Index Pattern**

- 打开 Kibana → Management → Stack Management → Index Patterns
- 点击 "Create index pattern"
- Index pattern: `app-logs-*`
- Time field: `@timestamp`
- 点击 "Create index pattern"

3. **查看日志**

- 打开 Kibana → Discover
- 选择刚创建的 `app-logs-*` index pattern
- 查看实时日志

## 🔧 配置说明

### Elasticsearch 配置调整

编辑 `elasticsearch/elasticsearch.yaml`：

```yaml
# 调整内存
env:
- name: ES_JAVA_OPTS
  value: "-Xms1g -Xmx1g"  # 根据集群资源调整

# 调整存储大小
volumeClaimTemplates:
  resources:
    requests:
      storage: 50Gi  # 根据日志量调整
```

### Fluent Bit 配置调整

编辑 `fluent-bit/fluent-bit-configmap.yaml`：

```ini
# 修改日志路径
[INPUT]
    Path  /var/log/app/custom.log  # 自定义路径

# 修改 Elasticsearch 输出
[OUTPUT]
    Host  elasticsearch.your-namespace.svc.cluster.local
    Logstash_Prefix  custom-logs  # 自定义索引前缀
```

### Kibana 访问方式切换

#### 改为 ClusterIP (集群内访问)

```yaml
# kibana/kibana.yaml
spec:
  type: ClusterIP
  ports:
  - port: 5601
    targetPort: 5601
    # 删除 nodePort 行
```

#### 改为 LoadBalancer (云环境)

```yaml
spec:
  type: LoadBalancer
```

## 📊 监控和维护

### 检查日志采集状态

```bash
# 查看 Fluent Bit Sidecar 日志
kubectl logs <pod-name> -c fluent-bit -n your-namespace

# 检查 Elasticsearch 健康状态
kubectl exec -it elasticsearch-0 -n your-namespace -- curl http://localhost:9200/_cluster/health?pretty

# 检查索引
kubectl exec -it elasticsearch-0 -n your-namespace -- curl http://localhost:9200/_cat/indices?v
```

### 清理旧日志

```bash
# 删除 7 天前的索引
curl -X DELETE "http://elasticsearch:9200/app-logs-2025.01.01"
```

或在 Kibana 中配置 Index Lifecycle Management (ILM)。

## ⚠️ 注意事项

### 1. 资源限制

每个 Pod 会额外增加一个 Fluent Bit 容器：
- 内存: ~100-200Mi
- CPU: ~100-200m

确保节点有足够资源。

### 2. 日志路径约定

**重要**：所有业务应用必须统一日志输出路径，建议：
- 标准路径: `/var/log/app/app.log`
- 如需自定义，需修改 Fluent Bit ConfigMap

### 3. JSON 格式要求

日志必须是**单行 JSON**，例如：
```json
{"time":"2025-01-07T10:00:00Z","level":"INFO","message":"test"}
```

不支持多行 JSON 或纯文本日志。

### 4. 存储规划

Elasticsearch 存储大小取决于：
- 日志量（每天产生多少 GB）
- 保留时长（建议 7-30 天）
- 计算公式: `存储大小 = 日志量/天 × 保留天数 × 1.5（冗余）`

### 5. 性能优化

如果日志量很大：
- 增加 Elasticsearch replicas
- 使用 SSD 存储
- 调整 Fluent Bit 的 `Mem_Buf_Limit`

## 🐛 故障排查

### 问题 1: Elasticsearch Pod 无法启动

**症状**：CrashLoopBackOff 或 OOMKilled

**解决方案**：
```bash
# 检查日志
kubectl logs elasticsearch-0 -n your-namespace

# 常见原因：内存不足
# 解决：调整资源限制或增加节点内存
```

### 问题 2: Kibana 无法连接 Elasticsearch

**症状**：Kibana 日志显示连接错误

**解决方案**：
```bash
# 检查 Elasticsearch 服务
kubectl get svc elasticsearch -n your-namespace

# 确保 Kibana 环境变量正确
kubectl describe deployment kibana -n your-namespace
```

### 问题 3: 看不到日志

**检查清单**：
1. Fluent Bit sidecar 是否运行？
   ```bash
   kubectl get pods -n your-namespace
   kubectl logs <pod> -c fluent-bit
   ```

2. 应用是否输出日志到正确路径？
   ```bash
   kubectl exec -it <pod> -c app -- ls -la /var/log/app/
   ```

3. 日志是否为 JSON 格式？
   ```bash
   kubectl exec -it <pod> -c app -- cat /var/log/app/app.log
   ```

4. Elasticsearch 是否有数据？
   ```bash
   kubectl exec -it elasticsearch-0 -- curl localhost:9200/_cat/indices
   ```

### 问题 4: StorageClass 不存在

**症状**：PVC Pending

**解决方案**：
```bash
# 查看可用 StorageClass
kubectl get storageclass

# 修改 elasticsearch.yaml，指定正确的 storageClassName
# 或联系管理员创建 StorageClass
```

## 📚 参考资料

- [Elasticsearch 官方文档](https://www.elastic.co/guide/en/elasticsearch/reference/current/index.html)
- [Fluent Bit 官方文档](https://docs.fluentbit.io/manual/)
- [Kibana 官方文档](https://www.elastic.co/guide/en/kibana/current/index.html)

## 🤝 支持

如有问题，请检查：
1. 所有 Pod 状态
2. 相关日志输出
3. 网络连通性
4. 资源配额

---

**最后更新**: 2025-01-07
