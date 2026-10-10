#!/usr/bin/env node
/**
 * ai-code-review —— 跨平台 CLI 入口（Node）
 *
 * 为什么用 Node 而不是直接暴露 .sh / .cmd：
 * npm 只有一个 bin 字段，无法按平台选择入口。若指向 .cmd，Linux 上生成的
 * shim 会把Windows 批处理当 shell 执行（实际踩过：CI 里报
 * "@echo: command not found"）。若指向 .sh，Windows 又无法直接执行。
 *
 * Node 在两个平台都原生可用，且已是本工具链的依赖（JDK 之外唯一要求）。
 * 因此入口用 Node 写，内部再按平台转调对应的启动器。
 */

'use strict';

const { spawnSync } = require('child_process');
const path = require('path');
const fs = require('fs');

const PKG_ROOT = path.resolve(__dirname, '..');
const JAR = path.join(PKG_ROOT, 'lib', 'review.jar');

/** 选平台对应的启动器。 */
function launcher() {
  return process.platform === 'win32'
    ? path.join(__dirname, 'ai-code-review.cmd')
    : path.join(__dirname, 'ai-code-review.sh');
}

function fail(msg, code) {
  process.stderr.write(msg + '\n');
  process.exit(code);
}

if (!fs.existsSync(JAR)) {
  fail(
    `ERROR: review.jar not found at ${JAR}\n` +
    'Please reinstall, or build it first:\n' +
    '  mvn -f engine/pom.xml clean package && cp engine/target/review.jar lib/',
    2
  );
}

const args = process.argv.slice(2);

// 无参数或帮助类参数：直接由本脚本处理，避免多一层进程
if (args.length === 0 || args[0] === '-h' || args[0] === '--help' || args[0] === 'help') {
  const r = spawnSync('java', ['-jar', JAR, '--help'], { stdio: 'inherit' });
  process.exit(r.status === null ? 1 : r.status);
}
if (args[0] === '-v' || args[0] === '--version' || args[0] === 'version') {
  const r = spawnSync('java', ['-jar', JAR, '--version'], { stdio: 'inherit' });
  process.exit(r.status === null ? 1 : r.status);
}

// java 存在性检查：给出可读报错，而不是让人看 stacktrace
const probe = spawnSync('java', ['-version'], { stdio: 'ignore' });
if (probe.error) {
  fail(
    'ERROR: java not found in PATH.\n' +
    'Please install JDK 17 or later and make sure java is in PATH.',
    127
  );
}

// 透传给平台启动器（它会补 -Dfile.encoding=UTF-8 并解析 jar 位置）。
// Windows 上 .cmd 不是可执行文件，必须经 shell 转发；Linux 下 .sh 可直接执行。
const isWin = process.platform === 'win32';
const r = spawnSync(launcher(), args, {
  stdio: 'inherit',
  shell: isWin,
});
if (r.error) {
  fail('ERROR: failed to run review engine: ' + r.error.message, 1);
}
process.exit(r.status === null ? 1 : r.status);