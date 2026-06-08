#!/bin/bash
set -euo pipefail

# ==========================================
# EasyPan Demo 一键部署脚本
# 目标: Ubuntu 20.04+ / Debian 11+
# ==========================================

APP_NAME="easypan"
APP_VERSION="1.0"
JAR_NAME="${APP_NAME}-${APP_VERSION}.jar"
DEPLOY_DIR="/opt/${APP_NAME}"
STORAGE_DIR="/opt/${APP_NAME}/storage"
DATA_DIR="/opt/${APP_NAME}/data"
FRONTEND_DIR="/var/www/${APP_NAME}-front"
NGINX_CONF="/etc/nginx/sites-available/${APP_NAME}"
SYSTEMD_SERVICE="/etc/systemd/system/${APP_NAME}.service"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC}  $*"; }
err()  { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }

# ── 1. 检查前置条件 ────────────────────────────────────
log "检查系统环境..."

# Java 8+
if ! command -v java &>/dev/null; then
    err "未安装 Java，请执行: sudo apt install openjdk-8-jdk -y"
fi
JAVA_VER=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
log "Java 版本: $(java -version 2>&1 | head -1)"

# nginx
if ! command -v nginx &>/dev/null; then
    warn "未安装 nginx，正在安装..."
    sudo apt update && sudo apt install nginx -y
fi
log "nginx: $(nginx -v 2>&1)"

# certbot
if ! command -v certbot &>/dev/null; then
    warn "未安装 certbot，正在安装..."
    sudo apt update && sudo apt install certbot python3-certbot-nginx -y
fi
log "certbot 已安装"

# ffmpeg (可选)
if ! command -v ffmpeg &>/dev/null; then
    warn "未安装 ffmpeg，视频转码功能将不可用"
    warn "如需安装: sudo apt install ffmpeg -y"
else
    log "ffmpeg: $(ffmpeg -version 2>&1 | head -1)"
fi

# ── 2. 构建后端 ────────────────────────────────────────
log "构建后端 (Maven)..."
cd "${SCRIPT_DIR}/easypan"
mvn clean package -DskipTests -q
log "后端构建完成"

# ── 3. 构建前端 ────────────────────────────────────────
log "构建前端 (Vite)..."
cd "${SCRIPT_DIR}/easypan-front"
if [ ! -d "node_modules" ]; then
    npm install --silent
fi
npm run build --silent
log "前端构建完成"

# ── 4. 停止旧服务 ──────────────────────────────────────
log "停止旧服务..."
if systemctl is-active --quiet "${APP_NAME}"; then
    sudo systemctl stop "${APP_NAME}"
    log "已停止 ${APP_NAME} 服务"
fi

# ── 5. 创建目录结构 ────────────────────────────────────
log "创建目录结构..."
sudo mkdir -p "${DEPLOY_DIR}" "${STORAGE_DIR}" "${DATA_DIR}" "${FRONTEND_DIR}"

# ── 6. 部署 storage_bak ──────────────────────────────────
log "部署 storage_bak（文件数据备份）..."
STORAGE_BAK_DIR="${DEPLOY_DIR}/storage_bak"
sudo rm -rf "${STORAGE_BAK_DIR:?}"
sudo cp -r "${SCRIPT_DIR}/easypan/storage_bak" "${STORAGE_BAK_DIR}"
log "storage_bak 部署完成"

# ── 7. 部署后端 ────────────────────────────────────────
log "部署后端..."
sudo cp "${SCRIPT_DIR}/easypan/target/${JAR_NAME}" "${DEPLOY_DIR}/"
sudo chmod 644 "${DEPLOY_DIR}/${JAR_NAME}"

# ── 8. 部署前端 ────────────────────────────────────────
log "部署前端..."
sudo rm -rf "${FRONTEND_DIR:?}/*"
sudo cp -r "${SCRIPT_DIR}/easypan-front/dist/"* "${FRONTEND_DIR}/"

# ── 9. 配置 systemd ────────────────────────────────────
log "配置 systemd 服务..."
sudo cp "${SCRIPT_DIR}/easypan.service" "${SYSTEMD_SERVICE}"
sudo systemctl daemon-reload
sudo systemctl enable "${APP_NAME}"

# ── 10. 配置 nginx ──────────────────────────────────────
log "配置 nginx..."
sudo cp "${SCRIPT_DIR}/nginx-easypan.conf" "${NGINX_CONF}"
if [ ! -L "/etc/nginx/sites-enabled/${APP_NAME}" ]; then
    sudo ln -sf "${NGINX_CONF}" "/etc/nginx/sites-enabled/${APP_NAME}"
fi

# 删除默认站点（如果存在）
if [ -L "/etc/nginx/sites-enabled/default" ]; then
    sudo rm -f "/etc/nginx/sites-enabled/default"
fi

# 测试 nginx 配置
if ! sudo nginx -t 2>&1; then
    err "nginx 配置有误，请检查 ${NGINX_CONF}"
fi
sudo systemctl reload nginx
log "nginx 配置完成"

# ── 11. 设置权限 ───────────────────────────────────────
log "设置文件权限..."
sudo chown -R www-data:www-data "${DEPLOY_DIR}" "${STORAGE_DIR}" "${DATA_DIR}" "${FRONTEND_DIR}"

# ── 12. 启动服务 ───────────────────────────────────────
log "启动 ${APP_NAME} 服务..."
sudo systemctl start "${APP_NAME}"

# 等待启动
sleep 3
if systemctl is-active --quiet "${APP_NAME}"; then
    log "服务启动成功"
else
    err "服务启动失败，请检查日志: sudo journalctl -u ${APP_NAME} -f"
fi

# ── 13. 配置 SSL 证书 ──────────────────────────────────
log "检查 SSL 证书..."
if [ ! -f "/etc/letsencrypt/live/pan.egon.chat/fullchain.pem" ]; then
    warn "未找到 SSL 证书，请手动执行:"
    warn "  sudo certbot --nginx -d pan.egon.chat"
else
    log "SSL 证书已存在"
fi

# ── 完成 ───────────────────────────────────────────────
echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  EasyPan Demo 部署完成！${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "  访问地址: https://pan.egon.chat"
echo "  管理员账号: admin@test.com"
echo "  管理员密码: 123456"
echo ""
echo "  常用命令:"
echo "    sudo systemctl status ${APP_NAME}   # 查看服务状态"
echo "    sudo journalctl -u ${APP_NAME} -f   # 查看日志"
echo "    sudo systemctl restart ${APP_NAME}  # 重启服务"
echo "    sudo systemctl stop ${APP_NAME}     # 停止服务"
echo ""
echo "  数据目录: ${STORAGE_DIR}"
echo "  数据库:   ${DATA_DIR}/easypan.mv.db"
echo ""