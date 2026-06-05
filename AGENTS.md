## 项目目标

这是我自己的 Agentic RAG 项目。

目标：

- 用于 AI Agent / 后端 + AI Agent 面试
- 提炼核心 RAG 能力
- 构建自己的项目
- 能独立讲解系统设计

不是：

- 完整复刻 ragent
- 追求复杂工程化

---

## 当前项目原则

项目采用：

“简化版 + 面试导向”

原则：

- 先跑通
- 先实现核心链路
- 优先低耦合
- 优先可讲解性
- 优先理解数据流

---

## 当前进度（截至 2025-06-04）

编译状态：BUILD SUCCESS

### ✅ 已完成的模块

| 模块 | 状态 | 说明 |
|---|---|---|
| framework | 完成 | 异常体系、统一响应、全局异常处理、UserContext |
| infra-ai LLM 调用层 | 完成 | LLMService(同步+流式)、RoutingLLMService、SSE 解析 |
| 会话系统 | 完成 | Conversation + Message CRUD、MemoryService、3 张表 |
| QueryRewrite | 完成 | 多问句拆分 + 改写，LLM 兜底策略 |
| Intent 识别 | 完成 | IntentResolver 并行分类 + 保底截断 |
| 检索层 | 简化版 | 单通道向量检索，InMemoryVectorStore(返回空)，无 MCP |
| Prompt 构建 | 完成 | KB/MCP/Mixed 三场景模板选择 |
| Pipeline | 完成 | 7 阶段全部真实调用，短路设计，非占位 |
| Controller + SSE | 完成 | GET /rag/chat + 流式推送 + StreamTaskManager(内存版) |
| 配置 + 资源 | 完成 | application.yaml(MySQL+Ollama)、14 个 prompt 模板、建表 SQL |

### ❌ 刻意删除/简化

| 删除 | 原因 |
|---|---|
| @IdempotentSubmit / @ChatRateLimit | AOP 未引入 |
| IntentGuidanceService | MVP handleGuideAmbiguity=false |
| StreamTaskManager(Redisson版) | 换成 ConcurrentHashMap |
| RagTraceContext 依赖 | 直接 IdUtil 生成 taskId |
| spotless 插件 | 缺版权文件，删掉 |

### ⬜ 待完成

1. 建库建表 → 启动项目 → curl 测试全链路
2. InMemoryVectorStoreService 去掉 @Service 注释，或手动注册 Bean
3. 启动可能缺：Redis 连接、MySQL 密码、Ollama 模型拉取
4. 面试准备：画 Pipeline 数据流图 + 5 个高频问题逐字稿