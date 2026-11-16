# 本地产物

这里存放临时检查报告、日志、数据库备份和交付包，除本说明外均由 Git 忽略。

- 历史业务验收结论与最终结果已移到 `docs/archive/business-check/`。
- 小程序源码以 `miniprogram/` 为准；ZIP 仅用于交付，不作为开发入口。
- `store-removal/` 包含升级前的数据库和程序备份，按恢复需求保留。

在仓库根目录执行 `node scripts/clean.mjs` 预览清理，执行 `node scripts/clean.mjs --apply` 删除已知构建产物、缓存、安装压缩包和顶层日志。此命令保留备份和交付包。
