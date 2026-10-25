# 发布代码目录说明

本目录为「03_代码（发布代码）」，承载**发布与运行所需的配置、数据、账号清单**。

> **源码不在此重复放置**：完整源码已随「编码」阶段交付于
> `02_项目开发/06.编码/`（`server/` + `frontend/`，365 个文件，已排除构建产物）。
> 本目录专注发布侧交付物，避免两份源码漂移。

## 一、本目录内容

| 内容 | 文件 | 说明 |
| --- | --- | --- |
| 配置手册 | `项目正常运行配置手册.md` | ✅ 环境变量表、启动步骤、FAQ、部署说明 |
| 密码与软件清单 | `用户密码与环境软件清单.md` | ✅ 账号密码、数据库账号、运行环境软件 |
| 数据库数据导出脚本 | `scripts/export-db.sh` | ✅ 运行后生成 `rentagent-data-*.sql` |
| （源码） | 见 `02_项目开发/06.编码/` | 后端 `server/` + 前端 `frontend/` |

## 二、配套源码的打包（如需单独压缩发布包）

> 在仓库根目录执行，产出干净 zip（不含 `.git` / `node_modules` / `target` / `dist`）：
> ```bash
> git archive --format=zip --output=RentAgent-发布代码-v1.10.zip HEAD
> ```

## 三、MySQL 数据导出

在数据库已运行（`docker compose up -d mysql`）且种子数据已写入（后端至少启动一次）后执行：

```bash
bash scripts/export-db.sh
```

生成 `rentagent-data-<日期>.sql`（结构与数据），作为「mysql 数据导出」交付物。
