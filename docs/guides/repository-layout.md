# 项目目录与维护

## 目录边界

| 路径 | 内容与约定 |
| --- | --- |
| `src/main/java/` | 后端业务源码，沿用现有 Spring Boot 包结构 |
| `src/main/resources/` | 应用配置、Mapper、区划数据、搜索映射、Flyway 迁移 |
| `src/test/` | 后端测试与测试资源 |
| `frontend/src/` | Web 商城和后台代码，测试跟随对应模块 |
| `frontend/public/` | 前端和本地小程序图片服务使用的静态资源 |
| `frontend/android/` | Capacitor 原生工程，保留 Gradle Wrapper 和原生配置 |
| `miniprogram/` | 微信小程序页面、工具、测试和本端检查脚本 |
| `scripts/` | 全项目维护和业务检查，`load/` 放压测，`sql/` 放手工 SQL |
| `docs/` | 按 API、指南、架构、历史记录、笔记分类，入口见 [文档索引](../README.md) |
| `artifacts/` | 本地报告、日志、升级备份及交付包，除 README 外忽略提交 |

保留 Maven、Vite、微信工具和 Android 的标准工程布局，构建入口保持在原目录。数据库历史迁移参与 Flyway 校验和全新数据库初始化，即使包含已移除模块，也必须保留。

## 本地文件与清理

在仓库根目录运行，脚本也支持从其他工作目录通过完整路径调用：

```shell
node scripts/clean.mjs
node scripts/clean.mjs --apply
```

默认只预览；`--apply` 删除以下已知生成文件：

- 后端 `target/`、前端 `dist/`、浏览器测试截图。
- `frontend/.npm-cache/`、Android 工程构建目录与工程级 `.gradle/`。
- 已解压使用的 Android 工具、JDK 21 和 Gradle 安装压缩包及下载元数据。
- `artifacts/` 和 `frontend/` 顶层的 `.log` 文件。

脚本限定工作区内的明确路径，拒绝通过符号链接或目录联接清理；检测到候选路径内有已修改或已暂存的受 Git 管理文件时停止。

以下文件继续保留在本机，忽略提交：

- `.m2/`、`frontend/node_modules/`：日常构建依赖，保留后可继续离线工作。
- `frontend/.android-sdk/`、`.jdk/`、`.gradle-runtime/`、`.gradle-cache/`：本机 Android 工具及依赖缓存，现有打包脚本仍使用它们。
- 环境配置、密钥、上传图片和 `artifacts/store-removal/` 升级备份。
- 已有小程序交付 ZIP；源码开发使用 `miniprogram/`。新产物按忽略规则处理，已纳入 Git 的交付包不会因忽略规则自动退出版本管理。

清理后用 `mvn compile`、前端 `npm run build`、`npm run app:apk` 或浏览器测试分别重建对应产物。安装压缩包已解压为工具目录，删除压缩包不会卸载工具。

## 本次整理

移除误纳入版本管理的 836 个 npm 缓存文件、四份早期业务检查中间 JSON，以及 Android 的两份模板示例测试；保留真实业务测试、最终验收报告及结果。零散 Java/Docker 笔记移至 `docs/notes/`，历史盘查与验收移至 `docs/archive/`，手工购物车 SQL 移至 `scripts/sql/`。

`.gitignore` 补充依赖、缓存和本地产物规则；`.dockerignore` 排除前端工具、客户端、报告、文档和本地配置，缩小后端镜像构建上下文。删除工作区缓存不会缩减已有 Git 历史，本次不重写历史。
