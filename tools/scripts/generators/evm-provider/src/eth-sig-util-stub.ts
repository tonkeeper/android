// Replaces @metamask/eth-sig-util at bundle time.
//
// Upstream pulls it in only for MobileAdapter's EIP-712 hashing. We run with
// `disableMobileAdapter: true` and hand the raw typed-data JSON to the native
// side, where chainkit hashes it. Keeping the real package would drag in the
// node polyfill chain and inflate the bundle from ~18 KB to megabytes.

export const SignTypedDataVersion = {
    V1: 'V1',
    V3: 'V3',
    V4: 'V4',
} as const;

export const TypedDataUtils = {
    eip712Hash(): never {
        throw new Error('eth-sig-util is stubbed out: typed data is hashed natively');
    },
};
