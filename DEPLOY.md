# EasyPan Demo 部署文档

## 环境要求

| 组件 | 版本要求 | 必需 | 说明 |
|------|---------|------|------|
| Ubuntu | 20.04+ / Debian 11+ | 是 | 服务器操作系统 |
| Java | 8+ | 是 | `sudo apt install openjdk-8-jdk -y` |
| nginx | 1.18+ | 是 | `sudo apt install nginx -y` |
| certbot | latest | 是 | HTTPS 证书: `sudo apt install certbot python3-certbot-nginx -y` |
| FFmpeg | 4.x+ | 否 | 视频转码和缩略图，未安装时视频上传后显示"转码失败" |
| Maven | 3.6+ | 是(构建) | 后端构建工具 |
| Node.js | 16+ | 是(构建) | 前端构建工具 |

## 快速部署

### 1. 克隆项目

```bash
git clone <repo-url> /opt/easypan-src
cd /opt/easypan-src
```

### 2. 一键部署

```bash
chmod +x deploy-demo.sh
sudo ./deploy-demo.sh
```

脚本会自动完成：构建后端 → 构建前端 → 部署文件 → 配置 systemd → 配置 nginx → 启动服务。

### 3. 配置 SSL 证书（首次部署）

```bash
sudo certbot --nginx -d pan.egon.chat
```

选择自动重定向 HTTP → HTTPS 即可。

### 4. 验证部署

```bash
# 检查服务状态
sudo systemctl status easypan

# 检查日志
sudo journalctl -u easypan -f

# 访问
curl -I https://pan.egon.chat
```

## 手动部署步骤

如果一键脚本失败，可手动执行以下步骤：

### 构建后端

```bash
cd easypan
mvn clean package -DskipTests
# 产出: target/easypan-1.0.jar
```

### 构建前端

```bash
cd easypan-front
npm install
npm run build
# 产出: dist/
```

### 部署文件

```bash
# 创建目录
sudo mkdir -p /opt/easypan /opt/easypan/storage /opt/easypan/data /var/www/easypan-front

# 复制后端
sudo cp easypan/target/easypan-1.0.jar /opt/easypan/
sudo chmod 644 /opt/easypan/easypan-1.0.jar

# 复制前端
sudo cp -r easypan-front/dist/* /var/www/easypan-front/

# 设置权限
sudo chown -R www-data:www-data /opt/easypan /var/www/easypan-front
```

### 配置 systemd

```bash
sudo cp easypan.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable easypan
sudo systemctl start easypan
```

### 配置 nginx

```bash
sudo cp nginx-easypan.conf /etc/nginx/sites-available/easypan
sudo ln -sf /etc/nginx/sites-available/easypan /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx
```

## 目录结构

```
/opt/easypan/
├── easypan-1.0.jar          # Spring Boot 应用
├── data/
│   └── easypan.mv.db        # H2 数据库文件
└── storage/
    ├── file/                # 用户上传文件
    ├── temp/                # 临时文件
    └── avatar/              # 用户头像

/var/www/easypan-front/     # 前端静态文件
/etc/nginx/sites-available/
└── easypan                  # nginx 配置
/etc/systemd/system/
└── easypan.service          # systemd 服务
```

## 常用管理命令

```bash
# 服务管理
sudo systemctl start easypan      # 启动
sudo systemctl stop easypan       # 停止
sudo systemctl restart easypan    # 重启
sudo systemctl status easypan     # 查看状态

# 日志查看
sudo journalctl -u easypan -f              # 实时日志
sudo journalctl -u easypan -n 100          # 最近 100 行
sudo journalctl -u easypan --since today   # 今日日志

# nginx
sudo nginx -t                  # 测试配置
sudo systemctl reload nginx    # 重载配置
sudo systemctl restart nginx   # 重启 nginx
```

## 默认账号

| 角色 | 邮箱 | 密码 |
|------|------|------|
| 管理员 | admin@test.com | 123456 |

## 功能说明

- **管理员空间**: 50MB
- **新注册用户空间**: 5MB（需要邮箱验证码）
- **单文件上传上限**: 50MB
- **每日重置**: 每天凌晨 2 点自动清空所有数据并重新初始化 demo 数据
- **视频转码**: 需要服务器安装 FFmpeg，未安装时视频文件标记为"转码失败"
- **图片缩略图**: 需要 FFmpeg，未安装时跳过压缩

## 故障排查

### 服务启动失败

```bash
# 查看详细错误
sudo journalctl -u easypan -n 50 --no-pager

# 手动启动测试
cd /opt/easypan
sudo -u www-data java -Ddemo.mode=true -jar easypan-1.0.jar
```

常见原因：
- 端口 7090 被占用：`sudo lsof -i :7090`
- 权限不足：确保 `/opt/easypan` 目录属于 `www-data`
- Java 版本不兼容：确认 Java 8+

### nginx 配置错误

```bash
sudo nginx -t           # 查看具体错误
sudo nginx -T           # 查看完整配置
```

### SSL 证书过期

```bash
sudo certbot renew --dry-run    # 测试续期
sudo certbot renew              # 执行续期
```

### 手动重置 demo 数据

```bash
# 停止服务
sudo systemctl stop easypan

# 删除数据
sudo rm -rf /opt/easypan/data/*
sudo rm -rf /opt/easypan/storage/*

# 重启服务（自动重新初始化）
sudo systemctl start easypan
```

### 查看 H2 数据库

```bash
# 方法 1: 通过 H2 Console（需临时开启）
# 编辑 application.properties，设置 spring.h2.console.enabled=true
# 然后访问 http://localhost:7090/api/h2-console
# JDBC URL: jdbc:h2:file:./data/easypan;MODE=MySQL
# 用户名: sa，密码: 空

# 方法 2: 通过 H2 Shell 工具
java -cp /opt/easypan/easypan-1.0.jar org.h2.tools.Shell
```