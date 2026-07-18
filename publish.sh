#!/usr/bin/env bash
# publish.sh — 一键发布 social-sdk 到 Maven 中央仓库（Sonatype OSSRH）
# 版本号从 0.0.1 依次递增，发布后写入 .publish-version 锚定下次递增
set -euo pipefail

cd "$(dirname "$0")"

# ===== OSSRH 账号（用环境变量或从 ~/.m2/settings.xml 读 serverId=ossrh）=====
# 优先环境变量；没设则依赖 settings.xml 中 serverId=ossrh 的 username/password
: "${OSSRH_USERNAME:=}"
: "${OSSRH_PASSWORD:=}"
: "${GPG_KEYNAME:=}"
: "${GPG_PASSPHRASE:=}"

# ===== 工具检测 =====
command -v mvn >/dev/null || { echo "ERROR: mvn 不在 PATH"; exit 1; }
command -v gpg >/dev/null || { echo "WARN: gpg 未安装，GPG 签名会失败"; }

# ===== 版本号管理：从 .publish-version 读上次版本，+1 末段得到本次版本 =====
VERSION_FILE=".publish-version"
if [ -f "$VERSION_FILE" ]; then
  LAST=$(cat "$VERSION_FILE" | tr -d '[:space:]')
else
  LAST="0.0.0"
fi
# 解析 X.Y.Z，末段 +1
MAJOR=$(echo "$LAST" | cut -d. -f1)
MINOR=$(echo "$LAST" | cut -d. -f2)
PATCH=$(echo "$LAST" | cut -d. -f3)
PATCH=$((PATCH + 1))
NEW_VERSION="${MAJOR}.${MINOR}.${PATCH}"

echo "================================================"
echo " social-sdk 发布到 Maven 中央仓库"
echo " 上次版本: $LAST"
echo " 本次版本: $NEW_VERSION"
echo "================================================"

# ===== 设置本次发布版本号（更新 parent pom + 各模块 pom 的 <version>）=====
# 用 versions:set 自动改所有模块的版本，再 commit 消除 -SNAPSHOT 后缀
mvn -q versions:set -DnewVersion="$NEW_VERSION" -DprocessAllModules=true -DgenerateBackupPoms=false
echo "已设置本次发布版本: $NEW_VERSION"

# ===== 构建 + 发布到 Sonatype Staging（含 source/javadoc/gpg 签名）=====
# -P release 启用 GPG + Staging 插件（已在 parent pom 中默认开启）
# gpg.passphraseServerId 指向 settings.xml 中 serverId=gpg 的 passphrase
MVN_GOALS="clean verify source:jar javadoc:jar deploy"
MVN_PROFILES="release"
MVN_ARGS="-DskipTests -Dgpg.passphraseServerId=gpg"

# OSSRH 账号从环境变量注入（settings.xml 也可提供，命令行参数优先）
if [ -n "$OSSRH_USERNAME" ]; then
  MVN_ARGS="$MVN_ARGS -Dossrh.username=$OSSRH_USERNAME"
fi
if [ -n "$OSSRH_PASSWORD" ]; then
  MVN_ARGS="$MVN_ARGS -Dossrh.password=$OSSRH_PASSWORD"
fi
if [ -n "$GPG_KEYNAME" ]; then
  MVN_ARGS="$MVN_ARGS -Dgpg.keyname=$GPG_KEYNAME"
fi

echo "正在构建并发布到 Sonatype Staging..."
mvn $MVN_GOALS -P $MVN_PROFILES $MVN_ARGS

# ===== Staging close + release（nexus-staging-maven-plugin 自动 close）=====
# autoReleaseAfterClose=true 已配，deploy 会自动 close+Release
echo "Sonatype Staging 已自动 Close+Release（autoReleaseAfterClose=true）"

# ===== 写入本次版本号锚点，下次脚本执行时递增 =====
echo "$NEW_VERSION" > "$VERSION_FILE"
echo ""
echo "================================================"
echo " 发布完成！版本: $NEW_VERSION"
echo " 中央仓库同步通常需要 30 分钟~2 小时"
echo " 下次执行本脚本将自动递增到 $MAJOR.$MINOR.$((PATCH + 1))"
echo "================================================"

# ===== 回滚 pom 版本为 -SNAPSHOT（保持开发态）=====
mvn -q versions:set -DnewVersion="${NEW_VERSION}-SNAPSHOT" -DprocessAllModules=true -DgenerateBackupPoms=false
echo "pom 已回滚到 ${NEW_VERSION}-SNAPSHOT 开发态"
