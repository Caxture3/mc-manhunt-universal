# Existing Features Migration Implementation Plan

> **For agentic workers:** 依据平台文件归属并行执行；保留旧实现，集中构建验收。

**Goal:** 迁入旧工程除局中角色轮换外的全部功能，构建两个版本的插件及模组。

**Architecture:** Paper / Fabric 共享源码、版本专用 API 适配、原样共享协议。

**Tech Stack:** Gradle、Paper、Fabric、Java 21 / 25。

**Spec:** `docs/superpowers/specs/2026-10-24-migration-design.md`

## Global Constraints

- 原有功能照搬，不重写；不增加新玩法。
- 移除局中角色轮换，保留初始随机分配。
- 用户要求精简测试，仅执行构建、针对性行为与产物检查。
- 保留原命令、权限、协议和资源命名空间。

## Review Focus

- 轮换是否从调度、debug、配置、提示及状态输出中完全移除。
- 1.21.11 的 Paper API / Adventure 4 与 26.3 的差异。
- Fabric 渲染、网络和输入 API 是否按实际版本适配。
- 发布包是否带齐协议类、语言、字体、纹理并保留客户端环境声明。
- 倒计时、指南针调度、结算及停止清理是否在移除轮换时被误删。

## Tasks

- [x] 复制 Paper 与协议源码和资源，删除随机轮换相关实现、配置与文案。
- [x] 迁移 Fabric 资源和 UI / 网络，适配 1.21.11 与 26.3。
- [x] 构建两个版本，修复必要的版本 API 差异。
- [x] 精简行为与产物核对，更新开发说明、迁移与验证记录，提供四个部署 JAR。

完成于 2026-10-25，详见 [迁移验证](../../migration-verification.md)。
