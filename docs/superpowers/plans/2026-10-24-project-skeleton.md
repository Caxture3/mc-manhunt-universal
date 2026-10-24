# Project Skeleton Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 建立两个 Minecraft 版本的 Paper / Fabric 可构建空骨架。

**Architecture:** 纯 Java 公共模块，共享平台源码，四个版本产物。

**Tech Stack:** Gradle Kotlin DSL、JDK 21 / 25、Paper API、Fabric Loom。

**Spec:** `docs/superpowers/specs/2026-10-24-project-skeleton-design.md`

## Global Constraints

- 只搭建骨架；已有功能随后迁移，不重写。
- 1.21.11 使用 Java 21，26.3 使用 Java 25；公共模块使用 Java 21。
- 不定义新网络协议，不加入玩法、命令、配置或 HUD 实现。

## Review Focus

- 两种 Loom 插件在同一构建中能否共存。
- 单版本选择是否真正排除另一版本模块，未知版本是否报错。
- 发布 JAR 是否包含正确入口和元数据，元数据变量是否完全展开。
- 1.21.11 发布客户端是否经过 remap，26.3 是否使用普通 JAR。
- 产物字节码版本是否符合对应 Java 基线。

## Tasks

- [x] 建立 Wrapper、集中依赖目录、模块选择和公共 Java 模块。
- [x] 建立 Paper / Fabric 共享入口、版本模块和独立产物。
- [x] 编写中文说明、迁移约束、CI。
- [x] 执行 `gradlew.bat clean build`、两个 `-PmcVersion` 构建和非法版本检查，检查四个发布 JAR。

本次为可逆工程配置与空入口，不新增业务行为测试；以实际构建及产物检查验收。保留当前空仓库工作目录，不创建工作树，不自动提交。

## 执行结果

全部任务完成，见 [验证记录](../../verification.md)。独立只读审查发现的 Paper 传递依赖仓库过滤问题和 Fabric remap 产物命名问题均已修复，并重新完成全部构建及产物检查。


