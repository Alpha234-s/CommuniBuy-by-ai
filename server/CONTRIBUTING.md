# 后端协作说明

本文用于两名后端开发者通过个人 fork、独立任务分支和 Pull Request 协作。
这是首版协作约定，具体模块负责人和技术方案由两人讨论后在任务或 PR 中确认。

## 当前项目

- 上游仓库：<https://github.com/hyplant-team/CommuniBuy-by-ai>，当前默认分支为 `main`。
- 后端目录：`server/`，使用 Java 和 Gradle，包含 `app` 子项目。
- 当前入口：`app/src/main/java/hy/hello/Main.java`，目前只有 Hello World 示例。
- 尚未配置 Web 框架、数据库、业务接口和自动化测试；不要将规划中的能力当作现有功能。
- 前端目录为 `webui/`。后端任务如需修改前端，先与相关开发者确认范围。

## Fork 与分支

每位开发者使用自己的 fork：`origin` 指向个人仓库，`upstream` 指向团队仓库。
以下命令在仓库根目录执行，将 `<你的 GitHub 用户名>` 替换为自己的用户名。

```powershell
git clone https://github.com/<你的 GitHub 用户名>/CommuniBuy-by-ai.git
cd CommuniBuy-by-ai
git remote add upstream https://github.com/hyplant-team/CommuniBuy-by-ai.git
git remote -v
```

开始任务前，先检查工作区。存在未提交改动时，先妥善提交或保存，避免混入其他任务。
从最新上游 `main` 创建自己的任务分支，不直接在 `main` 上开发。

```powershell
git status
git fetch upstream
git switch --no-track -c docs/backend-collaboration upstream/main
```

上面的分支用于本次协作说明。后续任务使用新的分支名，例如：

- `feat/backend-<任务名>`：业务功能。
- `fix/backend-<问题名>`：缺陷修复。
- `docs/backend-<主题>`：后端文档。

每个分支聚焦一个任务。不要直接向上游 `main` 推送，不改写同学的分支，不使用强制推送覆盖远程历史。

## 两人分工与接口协作

1. 开始编码前，在 Issue 或双方认可的任务记录中写明目标、负责人、涉及文件或模块、验收条件和依赖。
2. 一人负责实现，另一人负责评审；具体负责哪些业务模块需双方确认，本文不预先指定。
3. 接口先确认请求路径与方法、字段及类型、必填项、响应格式、错误码、鉴权方式和请求/响应示例，再分别实现和联调。
4. 公共响应结构、鉴权、异常处理、依赖配置和数据库迁移由当前任务明确一名负责人；另一人通过评审参与，避免同时改动公共文件。
5. 数据库变更需说明迁移方式、已有数据影响和回退方案。不要通过覆盖共享数据库结构的方式解决冲突。
6. 如果依赖尚未合并的 PR，在任务记录和 PR 描述中标明依赖；接口约定变化时同步更新记录，并通知受影响的协作者。

## 本地构建与提交检查

当前仓库没有固定 Java toolchain；开始业务开发前，双方需要确认并记录一致的 JDK 版本。
Windows 下在 `server/` 目录使用仓库自带的 Gradle Wrapper：

```powershell
cd server
.\gradlew.bat build
```

首次构建需要联网下载 Gradle。构建收尾任务会调用 `scripts/fix_jar_time.py`，因此还需要可通过 `python.exe` 调用的 Python 3。
构建产物汇总在 `server/build/outputs/`。当前没有业务测试，构建通过不代表接口验收通过。
业务功能 PR 应补充与改动相关的有效测试或可重复的验证步骤。

提交前回到仓库根目录，检查改动范围并显式暂存本次任务的文件：

```powershell
git status
git diff
git diff --check
git add server/CONTRIBUTING.md
git diff --cached
git diff --cached --check
git commit -m "docs(backend): add collaboration guide"
git push -u origin docs/backend-collaboration
```

上面的暂存路径、提交信息和分支名对应本次文档 PR；业务任务应替换为实际文件、描述和分支。
不要提交密码、访问令牌、数据库凭据、本机配置或 `build/`、`.gradle/` 等生成文件。

## Pull Request 与评审

- 来源选择个人 fork 的任务分支，目标选择 `hyplant-team/CommuniBuy-by-ai:main`。
- 描述包含问题与目标、实际改动范围、接口/数据库影响、验证命令及结果，以及关联任务或依赖 PR。
- 未完成的工作使用 Draft PR；准备好后请另一名后端开发者评审。
- 合并前至少由另一名后端开发者评审通过，并通过项目已有检查；文档修改说明文档验证结果即可。
- 按评审意见继续提交到自己的任务分支，同一个 PR 会自动更新。
- 合并由团队约定的维护者执行；创建 PR 不等于授权自动合并。

若目标分支更新，先确认工作区干净，再将上游更新合并到自己的任务分支：

```powershell
git fetch upstream
git merge upstream/main
```

发生冲突时逐项核对双方意图，与相关作者确认后解决，再重新检查和验证。
不要通过整文件覆盖的方式丢弃同学的实现；解决完成后正常提交并推送自己的分支。
