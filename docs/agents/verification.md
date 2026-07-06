# WisePenCloud 验证策略

本文档约束构建、测试和无法运行 Maven 时的替代检查。目标是尽可能验证改动，同时避免在环境不具备时反复执行同类失败命令。

## 1. 常用命令

全量验证：

```bash
mvn clean verify
```

指定模块验证：

```bash
mvn -pl <module> -am test
```

示例：

```bash
mvn -pl wisepen-resource-service/wisepen-resource-biz -am test
```

只做编译检查：

```bash
mvn -pl <module> -am compile
```

## 2. 选择范围

- 只改文档时，不需要运行 Maven；做 Markdown 结构和 UTF-8 检查即可。
- 修改单个 `*-biz` 时，优先验证该 `*-biz` 及其依赖模块。
- 修改 `*-api` 时，同时验证依赖该 API 的相关 `*-biz`。
- 修改 `wisepen-common` 或 `wisepen-common-log` 时，应提高验证范围，至少覆盖受影响的代表性服务。
- 修改根 `pom.xml`、依赖版本或插件时，优先执行全量验证。

## 3. 无法 Maven 编译时

如果本机缺少 JDK、Maven、依赖，或网络导致 Maven 无法跑通：

- 记录失败命令和关键原因。
- 不要反复尝试同类 Maven 命令。
- 不要为了跑通构建随意改 POM、仓库地址、profile 或本地配置。
- 改用静态检查。

## 4. 静态检查清单

- 读根 `pom.xml` 和相关模块 `pom.xml`，确认模块路径、依赖和 Java 版本。
- 检查新增实现是否参考了同模块相邻代码，而不是引入孤立风格。
- 检查 imports 是否存在明显缺失或冲突。
- 检查类名、方法名、字段名、枚举常量是否真实存在。
- 检查 `ServiceException` 引用的错误枚举是否真实存在。
- 检查 Controller 是否读取 `SecurityContextHolder`，并只将 Service 真实需要的上下文作为参数传入。
- 检查 Service 是否没有依赖 `SecurityContextHolder`。
- 检查 Resource 动作鉴权是否尽可能在 Controller 完成；Service 内权限查询或安全上下文传递是否有业务必要。
- 检查跨服务调用是否使用已有 Feign/API 契约。
- 检查新增降级、catch 后返回空结果、默认值兜底是否有相邻代码先例或明确业务理由。
- 检查是否存在一次性小函数和无收益抽象，尤其是 `toXxx`、`checkXxx`、`newId`、`fetchXxxInfo`。
- 检查 Service 参数是否均被当前业务流程实际使用。
- 检查集合遍历中是否存在逐条数据库查询、远程调用或高成本序列化。
- 检查中文 Markdown 和 Java 注释是否按 UTF-8 读取后无乱码。

## 5. 文档检查

文档改动至少检查：

- `AGENTS.md` 非空。
- `docs/agents/` 下被引用的文档真实存在。
- 所有 Markdown 标题层级清晰。
- OpenAPI 指南中的 `description` 示例保留 `- 用途：` 等横线格式。
- 中文内容用 UTF-8 读取不乱码。

## 6. 汇报要求

最终说明中写清：

- 执行过的验证命令或静态检查。
- 未运行 Maven 的原因，如果没有运行。
- 发现的环境限制或残余风险。
