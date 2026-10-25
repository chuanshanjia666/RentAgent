# 发布代码目录说明

本目录为「03_代码（发布代码）」，对应仓库根目录 `RentAgent/` 的源码与数据。请按下列清单打包放入：

## 一、需放入的内容

| 内容 | 源路径 | 说明 |
| --- | --- | --- |
| 后端源码 | `server/` | Spring Boot 3.3.4，端口 8080 |
| 前端源码 | `frontend/` | React 18 + Vite（Web 版 + Electron 桌面端同源） |
| 数据库初始化脚本 | `docker/mysql-init/01_schema.sql` | 19 张表 |
| 容器编排 | `docker-compose.yml` | mysql / redis / server |
| 集成冒烟脚本 | `scripts/` | ci-smoke.sh / smoke.sh / ci-no-model-check.sh |
| 配置手册 | 本目录 `项目正常运行配置手册.md` | ✅ 已生成 |
| 密码与软件清单 | 本目录 `用户密码与环境软件清单.md` | ✅ 已生成 |
| 数据库数据导出 | 本目录 `scripts/export-db.sh` 生成 | ✅ 脚本已生成 |
| 文档 | `doc/` | 六阶段交付物 |

## 二、打包排除项（勿打包）

- `.git/`
- `frontend/node_modules/`
- `frontend/dist/`、`frontend/release/`
- `server/target/`
- `uploads/`（运行期生成）
- `*.docx / *.pdf / *.xlsx` 的中间脚本产物（`doc/*/assets/`）

> 建议在仓库根执行：
> `git archive --format=zip --output=RentAgent-发布代码-v1.10.zip HEAD`
> 或手动拷贝后压缩。

## 三、MySQL 数据导出

在数据库已运行（`docker compose up -d mysql`）且种子数据已写入（后端至少启动一次）后执行：

```bash
bash scripts/export-db.sh
```

生成 `rentagent-data-<日期>.sql`（结构与数据），作为「mysql 数据导出」交付物。
