import { Web3Provider } from '@trustwallet/web3-provider-core';
import { EthereumProvider } from '@trustwallet/web3-provider-ethereum';

interface ProviderInfo {
    uuid: string;
    name: string;
    icon: string;
    rdns: string;
}

interface RequestArguments {
    method: string;
    params?: unknown[] | object;
}

declare const window: any;

const NATIVE_INTERFACE = 'TonkeeperEvm';

const HANDSHAKE = 'wallet_provider_ready';

// Subscriptions and filters need a transport we don't have; reject them up front
// instead of letting the dApp wait on a request the wallet will never answer.
const UNSUPPORTED_METHODS = new Set([
    'eth_subscribe',
    'eth_unsubscribe',
    'eth_newFilter',
    'eth_newBlockFilter',
    'eth_newPendingTransactionFilter',
    'eth_uninstallFilter',
]);

function rpcError(code: number, message: string): Error {
    return Object.assign(new Error(message), { code });
}

// personal_sign is specified as [data, address], but some dApps still send the
// legacy [address, data] order.
function normalizeParams(args: RequestArguments, address: string): RequestArguments {
    if (args.method !== 'personal_sign' || !Array.isArray(args.params)) {
        return args;
    }
    const [first, second] = args.params as string[];
    if (typeof first !== 'string' || first.toLowerCase() !== address.toLowerCase()) {
        return args;
    }
    return { method: args.method, params: [second, first] };
}

function announceProvider(provider: unknown, info: ProviderInfo) {
    const detail = Object.freeze({ info, provider });
    const announce = () => {
        window.dispatchEvent(new CustomEvent('eip6963:announceProvider', { detail }));
    };
    window.addEventListener('eip6963:requestProvider', announce);
    announce();
}

function install() {
    // The document-start script is scoped by origin, not by frame, so it also runs in same-origin
    // iframes. Only the main frame gets an answer, and a provider nobody answers hangs forever.
    if (window.top !== window || window.__tkEvmInstalled) {
        return;
    }
    window.__tkEvmInstalled = true;

    const native = window[NATIVE_INTERFACE];

    const ethereum = new EthereumProvider({
        disableMobileAdapter: true,
        isTrust: false,
    });

    // The address and the chain are resolved natively, which the page never waits for. Requests
    // park on this promise instead of seeing an empty account.
    let ready: (value: unknown) => void;
    let unavailable: (error: Error) => void;
    const wallet = new Promise((resolve, reject) => {
        ready = resolve;
        unavailable = reject;
    });
    wallet.catch(() => {});

    new Web3Provider({ strategy: 'CALLBACK' })
        .registerProvider(ethereum)
        .setHandler((request: any) => {
            native.postMessage(JSON.stringify({
                id: request.id,
                method: request.name,
                params: request.params ?? [],
            }));
        });

    ethereum.isTonkeeper = true;

    const request = ethereum.request.bind(ethereum);
    ethereum.request = async (args: RequestArguments) => {
        if (UNSUPPORTED_METHODS.has(args?.method)) {
            throw rpcError(4200, `Unsupported method: ${args.method}`);
        }
        await wallet;
        return request(normalizeParams(args, ethereum.getAddress()));
    };

    // addEventListener rather than `onmessage`: the handler stays in this closure instead of
    // becoming a property of a global object that any script on the page could read or call.
    native.addEventListener('message', (event: { data: string }) => {
        const message = JSON.parse(event.data);
        switch (message.type) {
            case 'config':
                ethereum.setAddress(message.address);
                ethereum.setChainId(message.chainId);
                ready(message);
                ethereum.emit('connect', { chainId: message.chainId });
                if (message.info) {
                    announceProvider(ethereum, message.info);
                }
                break;
            case 'unavailable':
                unavailable(rpcError(message.code, message.message));
                break;
            case 'result':
                ethereum.sendResponse(message.id, message.value);
                break;
            case 'error':
                ethereum.sendError(message.id, rpcError(message.code, message.message));
                break;
            case 'chainSwitched':
                ethereum.sendResponse(message.id, null);
                ethereum.setChainId(message.chainId);
                ethereum.emit('chainChanged', message.chainId);
                break;
        }
    });

    window.ethereum = ethereum;

    native.postMessage(JSON.stringify({ method: HANDSHAKE }));
}

install();
