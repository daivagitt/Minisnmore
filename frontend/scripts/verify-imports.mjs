#!/usr/bin/env node
// Resolve every static import, re-export, and literal dynamic import using the
// frontend's TypeScript settings. Vite asset queries resolve to the source file.
import { existsSync } from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const frontend = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const root = path.dirname(frontend);
const require = createRequire(path.join(frontend, 'package.json'));
const ts = require('typescript');
const configPath = path.join(frontend, 'tsconfig.json');
const config = ts.readConfigFile(configPath, ts.sys.readFile);
if (config.error) throw new Error(ts.flattenDiagnosticMessageText(config.error.messageText, '\n'));
const parsed = ts.parseJsonConfigFileContent(config.config, ts.sys, frontend);
if (parsed.errors.length) throw new Error(parsed.errors.map(e => ts.flattenDiagnosticMessageText(e.messageText, '\n')).join('\n'));
const cache = ts.createModuleResolutionCache(frontend, name => name, parsed.options);
const failures = [];
let count = 0;
for (const filename of parsed.fileNames) {
  const source = ts.createSourceFile(filename, ts.sys.readFile(filename) ?? '', ts.ScriptTarget.Latest, true);
  function check(node) {
    let specifier;
    if ((ts.isImportDeclaration(node) || ts.isExportDeclaration(node)) && node.moduleSpecifier && ts.isStringLiteral(node.moduleSpecifier)) {
      specifier = node.moduleSpecifier.text;
    } else if (ts.isCallExpression(node) && node.expression.kind === ts.SyntaxKind.ImportKeyword && ts.isStringLiteral(node.arguments[0])) {
      specifier = node.arguments[0].text;
    }
    if (specifier !== undefined) {
      count++;
      const resolved = ts.resolveModuleName(specifier, filename, parsed.options, ts.sys, cache).resolvedModule;
      if (!resolved) {
        const clean = specifier.split('?')[0];
        const asset = clean.startsWith('@/') ? path.join(frontend, 'src', clean.slice(2))
          : clean.startsWith('.') ? path.resolve(path.dirname(filename), clean) : undefined;
        // Non-code assets are handled by Vite; check that the referenced file exists.
        if (!asset || !existsSync(asset) || /\.[cm]?[jt]sx?$/.test(clean)) {
          const line = source.getLineAndCharacterOfPosition(node.getStart()).line + 1;
          failures.push(`${path.relative(root, filename)}:${line}: cannot resolve ${specifier}`);
        }
      }
    }
    ts.forEachChild(node, check);
  }
  check(source);
}
// Also catch imports of missing named/default exports, including type-only imports.
const importCodes = new Set([1192, 1259, 2305, 2307, 2459, 2497, 2613, 2614, 2688, 2724, 2792, 7016]);
const program = ts.createProgram(parsed.fileNames, parsed.options);
for (const diagnostic of ts.getPreEmitDiagnostics(program)) {
  if (!importCodes.has(diagnostic.code)) continue;
  const position = diagnostic.file && diagnostic.start !== undefined
    ? `${path.relative(root, diagnostic.file.fileName)}:${diagnostic.file.getLineAndCharacterOfPosition(diagnostic.start).line + 1}: ` : '';
  failures.push(position + ts.flattenDiagnosticMessageText(diagnostic.messageText, '\n'));
}
if (failures.length) {
  console.error(failures.join('\n'));
  process.exitCode = 1;
} else {
  console.log(`Verified ${count} frontend imports and named exports across ${parsed.fileNames.length} source/config files.`);
}
