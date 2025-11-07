#!/bin/bash
set -e

# EFK Sidecar 部署脚本
# 用法: ./deploy.sh <namespace>

NAMESPACE=${1:-"your-namespace"}

echo "=========================================="
echo "EFK Sidecar 部署脚本"
echo "目标 Namespace: $NAMESPACE"
echo "=========================================="

# 检查 namespace 是否存在
if ! kubectl get namespace "$NAMESPACE" &> /dev/null; then
    echo "错误: Namespace '$NAMESPACE' 不存在"
    echo "请先创建 namespace 或使用正确的 namespace"
    exit 1
fi

# 替换所有 YAML 文件中的 namespace
echo ""
echo "[1/5] 准备配置文件..."
find . -name "*.yaml" -type f -exec sed -i "s/your-namespace/$NAMESPACE/g" {} \;
echo "✓ Namespace 已更新为: $NAMESPACE"

# 部署 Elasticsearch
echo ""
echo "[2/5] 部署 Elasticsearch..."
kubectl apply -f elasticsearch/elasticsearch.yaml
echo "✓ Elasticsearch 已部署"

# 等待 Elasticsearch 就绪
echo "等待 Elasticsearch 启动..."
kubectl wait --for=condition=ready pod -l app=elasticsearch -n "$NAMESPACE" --timeout=300s
echo "✓ Elasticsearch 已就绪"

# 部署 Kibana
echo ""
echo "[3/5] 部署 Kibana..."
kubectl apply -f kibana/kibana.yaml
echo "✓ Kibana 已部署"

# 等待 Kibana 就绪
echo "等待 Kibana 启动..."
kubectl wait --for=condition=ready pod -l app=kibana -n "$NAMESPACE" --timeout=300s
echo "✓ Kibana 已就绪"

# 部署 Fluent Bit ConfigMap
echo ""
echo "[4/5] 部署 Fluent Bit ConfigMap..."
kubectl apply -f fluent-bit/fluent-bit-configmap.yaml
echo "✓ Fluent Bit ConfigMap 已部署"

# 提示下一步
echo ""
echo "[5/5] 部署完成！"
echo ""
echo "=========================================="
echo "访问信息："
echo "=========================================="
echo "Elasticsearch: http://elasticsearch.$NAMESPACE.svc.cluster.local:9200"
echo "Kibana: http://kibana.$NAMESPACE.svc.cluster.local:5601"
echo ""
echo "如果使用 NodePort，可以通过以下方式访问 Kibana："
KIBANA_NODEPORT=$(kubectl get svc kibana -n "$NAMESPACE" -o jsonpath='{.spec.ports[0].nodePort}')
if [ -n "$KIBANA_NODEPORT" ]; then
    echo "Kibana NodePort: http://<任意节点IP>:$KIBANA_NODEPORT"
fi
echo ""
echo "=========================================="
echo "下一步："
echo "=========================================="
echo "1. 在业务应用中添加 Fluent Bit Sidecar"
echo "   参考: examples/app-with-sidecar.yaml"
echo ""
echo "2. 确保应用日志输出到 /var/log/app 目录"
echo "   并且是 JSON 格式"
echo ""
echo "3. 访问 Kibana 配置 Index Pattern:"
echo "   - Index pattern: app-logs-*"
echo "   - Time field: @timestamp"
echo ""
echo "=========================================="
