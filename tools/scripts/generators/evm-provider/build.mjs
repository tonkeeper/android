import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { build } from 'esbuild';

const root = dirname(fileURLToPath(import.meta.url));

const UPSTREAM_URL = 'https://github.com/trustwallet/trust-web3-provider.git';
const UPSTREAM_TAG = 'v4.9.4';

const upstream = join(root, '.upstream');
const stamp = join(upstream, '.tag');
const output = resolve(process.argv[2] ?? join(root, 'dist', 'evm_provider.js'));

function syncUpstream() {
    if (existsSync(stamp) && readFileSync(stamp, 'utf8').trim() === UPSTREAM_TAG) {
        return;
    }
    rmSync(upstream, { recursive: true, force: true });
    execFileSync('git', ['clone', '--quiet', '--depth', '1', '--branch', UPSTREAM_TAG, UPSTREAM_URL, upstream], {
        stdio: 'inherit',
    });
    writeFileSync(stamp, `${UPSTREAM_TAG}\n`);
}

syncUpstream();
mkdirSync(dirname(output), { recursive: true });

await build({
    entryPoints: [join(root, 'src', 'index.ts')],
    outfile: output,
    bundle: true,
    minify: true,
    format: 'iife',
    target: ['chrome90'],
    legalComments: 'none',
    banner: {
        js: `/*! Tonkeeper EVM provider \u2014 bundled from trust-web3-provider ${UPSTREAM_TAG} (MIT)\n`
            + ` * https://github.com/trustwallet/trust-web3-provider\n`
            + ` * Rebuild: tools/scripts/generators/evm-provider/README.md\n`
            + ` */`,
    },
    alias: {
        '@trustwallet/web3-provider-core': join(upstream, 'packages', 'core', 'index.ts'),
        '@trustwallet/web3-provider-ethereum': join(upstream, 'packages', 'ethereum', 'index.ts'),
        '@metamask/eth-sig-util': join(root, 'src', 'eth-sig-util-stub.ts'),
        events: join(root, 'node_modules', 'events', 'events.js'),
    },
});

const size = statSync(output).size;
console.log(`${output} — ${(size / 1024).toFixed(1)} KB (upstream ${UPSTREAM_TAG})`);
