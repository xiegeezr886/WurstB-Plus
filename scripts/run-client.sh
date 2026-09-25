#!/usr/bin/env bash
# 真实启动某个版本客户端的入口（Git Bash / Linux / macOS）。
#
#   ./scripts/run-client.sh 26.2                 # Forge 26.2 客户端
#   ./scripts/run-client.sh 1.21.9 fabric        # Fabric 1.21.9 客户端
#   ./scripts/run-client.sh 1.20.1 neoforge --offline
#
# 会自动带上仓库里的国内镜像初始化脚本（gradle/init-mirrors.gradle）。
# 首次启动要下载该版本的资源文件与依赖，国内走 BMCLAPI / 阿里云镜像。
set -euo pipefail

if [ $# -lt 1 ]; then
	printf '用法: %s <MC版本> [forge|fabric|neoforge] [额外的 gradle 参数...]\n' "$0" >&2
	printf '例如: %s 26.2\n' "$0" >&2
	exit 2
fi

VERSION="$1"; shift
LOADER="forge"
if [ $# -gt 0 ] && [ "${1#-}" = "$1" ]; then
	LOADER="$1"; shift
fi

case "$LOADER" in
	forge|fabric|neoforge) ;;
	*) printf '未知的加载器: %s\n' "$LOADER" >&2; exit 2 ;;
esac

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO=$(dirname "$SCRIPT_DIR")

# 仓库布局：1.20.1 的三个加载器是各自的根工程，其余版本在 <加载器>/versions/<版本>
if [ "$VERSION" = "1.20.1" ]; then
	case "$LOADER" in
		forge)    PROJECT="$REPO" ;;
		fabric)   PROJECT="$REPO/fabric" ;;
		neoforge) PROJECT="$REPO/neoforge" ;;
	esac
elif [ "$LOADER" = "forge" ]; then
	PROJECT="$REPO/versions/$VERSION"
else
	PROJECT="$REPO/$LOADER/versions/$VERSION"
fi

if [ ! -d "$PROJECT" ]; then
	printf '找不到工程目录: %s\n' "$PROJECT" >&2
	printf '可用版本: ' >&2
	for d in "$REPO"/versions "$REPO"/fabric/versions "$REPO"/neoforge/versions; do
		[ -d "$d" ] || continue
		ls -1 "$d" 2>/dev/null | tr '\n' ' ' >&2
	done
	printf '\n' >&2
	exit 1
fi

printf '工程: %s\n任务: runClient\n\n' "$PROJECT"
cd "$PROJECT"
exec ./gradlew runClient --init-script "$REPO/gradle/init-mirrors.gradle" "$@"
