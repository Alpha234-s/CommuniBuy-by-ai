# 后端协作约定

上游 `hyplant-team/CommuniBuy-by-ai:main`；使用个人fork作为origin、团队库作为upstream。从最新upstream/main创建独立任务分支；先检查 `git status` 和 `git log`，保留其他人的未提交修改。必要时另建检出目录，不擅自清理/重置别人工作区。不直接推main、不强推、不自动合并。

当前实际入口 `server/app/src/main/java/hy/CommunityBuy/MainApplication.java`。架构为Spring Boot、Security、JPA、MySQL、Flyway，JDK21；已包含业务测试、可执行Boot JAR和GitHub Actions。原Hello World/自定义修改JAR时间流程已移除。

任务开始在Issue或仓库任务记录写清目标、负责人、文件、验收与依赖；一人实现、另一人评审。公共认证、响应结构、异常和迁移由当前任务维护者统一修改，其他人通过评审参与。接口变更必须同步 `documents/server/design-api.md`；数据库变更新增Flyway版本，不修改已经部署的迁移。不得覆盖共享数据库或提交秘密、本机.env、build产物。

```bash
git remote -v
git status --short
git fetch upstream
git switch --no-track -c feat/backend-your-task upstream/main
cd server
./gradlew clean build            # JDK21 + Docker，真实MySQL集成测试
python3 scripts/check-tests.py   # 核实测试发现和零跳过
# Windows: .\gradlew.bat clean build; python scripts/check-tests.py
```

环境与无Docker测试方案见README；没有运行的测试不得记为通过。构建产物 `server/app/build/libs/communibuy.jar`，不再依赖 `python.exe` 修复JAR。提交前 `git diff --check`，显式暂存本任务文件，按阶段正常提交；向个人fork任务分支推送并开Draft PR到团队main，说明接口、迁移、测试、限制。至少由另一名维护者评审和通过CI后，由团队维护者决定合并。

上游更新时先确认工作区状态，再fetch并合并upstream/main；逐项解决冲突，保留双方意图，不整文件覆盖已有修复。前端不在后端任务默认范围，变更前需获得相应范围授权。

当前企业商城任务的状态与验收：[progress.md](../documents/server/progress.md)、[最终矩阵](../documents/server/final-acceptance.md)。backend-hardening.md/verification.md是上一轮证据，不代替当前测试结果。真实AI记录见ai-development-log.md，不得伪造外部人工评审或历史Prompt。

Java源统一google-java-format1.28.0：`./gradlew :app:formatJava`后再`./gradlew build`，CI的formatCheck只检查不自动改源码；unchecked警告视为错误。所有数据库版本通过新增Flyway迁移，不回写旧版本。测试当前至少78项且关键suite必须存在，脚本同时检查零失败/错误/跳过；增加测试后同步门槛，不得为掩盖失败降低门槛。JaCoCo报告说明未覆盖的核心分支，不用数字替代事务/权限/并发断言。部署与备份演练见deployment.md。
