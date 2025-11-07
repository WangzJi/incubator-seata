#!/bin/bash
set -e

# EFK Sidecar 清理脚本
# 用法: ./cleanup.sh <namespace>

NAMESPACE=${1:-"your-namespace"}

echo "=========================================="
echo "EFK Sidecar 清理脚本"
echo "目标 Namespace: $NAMESPACE"
echo "=========================================="
echo ""
echo "警告: 此操作将删除所有 EFK 组件和数据！"
read -p "确认删除? (yes/no): " CONFIRM

if [ "$CONFIRM" != "yes" ]; then
    echo "操作已取消"
    exit 0
fi

echo ""
echo "[1/4] 删除 Kibana..."
kubectl delete -f kibana/kibana.yaml --ignore-not-found=true
echo "✓ Kibana 已删除"

echo ""
echo "[2/4] 删除 Fluent Bit ConfigMap..."
kubectl delete -f fluent-bit/fluent-bit-configmap.yaml --ignore-not-found=true
echo "✓ Fluent Bit ConfigMap 已删除"

echo ""
echo "[3/4] 删除 Elasticsearch..."
kubectl delete -f elasticsearch/elasticsearch.yaml --ignore-not-found=true
echo "✓ Elasticsearch 已删除"

echo ""
echo "[4/4] 清理 PVC (可选)..."
read -p "是否删除 Elasticsearch 数据卷? 数据将永久丢失! (yes/no): " DELETE_PVC

if [ "$DELETE_PVC" == "yes" ]; then
    kubectl delete pvc -l app=elasticsearch -n "$NAMESPACE"
    echo "✓ PVC 已删除"
else
    echo "✓ PVC 已保留"
fi

echo ""
echo "=========================================="
echo "清理完成！"
echo "=========================================="
echo ""
echo "提示: 业务应用中的 Fluent Bit Sidecar 需要手动移除"
echo "请编辑相关 Deployment 并删除 fluent-bit 容器定义"
echo ""
