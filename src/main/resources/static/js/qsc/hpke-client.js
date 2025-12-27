/**
 * Quantum-Safe HPKE Client
 * Handles client-side encryption/decryption using Kyber1024 + AES-256-GCM
 */
class QSCHPKEClient {
    constructor() {
        this.kyberModule = null;
        this.publicKey = null;
        this.keyId = null;
        this.keyExpiry = null;
        this.initialized = false;
    }

    /**
     * Initialize WASM module and fetch server's public key
     */
    async init() {
        if (this.initialized && this.keyExpiry && new Date() < new Date(this.keyExpiry)) {
            return;
        }

        // Load Kyber WASM module (to be implemented)
        // if (!this.kyberModule) {
        //   this.kyberModule = await import('/wasm/kyber1024.wasm');
        //   await this.kyberModule.ready();
        // }

        // Fetch server's Kyber public key
        try {
            const response = await fetch('/api/qsc/public-key');
            if (!response.ok) {
                throw new Error('Failed to fetch QSC public key');
            }

            const data = await response.json();
            if (data.success && data.data) {
                this.keyId = data.data.keyId;
                this.publicKey = this.base64ToUint8Array(data.data.publicKey);
                this.keyExpiry = data.data.expiresAt;
                this.initialized = true;

                console.log('[QSC] Initialized with key ID:', this.keyId);
            }
        } catch (error) {
            console.error('[QSC] Initialization failed:', error);
            throw error;
        }
    }

    /**
     * Encrypt data using HPKE
     * @param {Object} data - JavaScript object to encrypt
     * @returns {Uint8Array} - HPKE encrypted message
     */
    async encrypt(data) {
        await this.init();

        const jsonString = JSON.stringify(data);
        const plaintext = new TextEncoder().encode(jsonString);

        // Generate random AES-256 key
        const aesKey = crypto.getRandomValues(new Uint8Array(32));

        // Encapsulate AES key with Kyber (placeholder - needs WASM)
        // const encapsulatedKey = await this.kyberModule.encapsulate(this.publicKey, aesKey);
        const encapsulatedKey = new Uint8Array(1568); // Placeholder

        // Encrypt plaintext with AES-256-GCM
        const nonce = crypto.getRandomValues(new Uint8Array(12));
        const cryptoKey = await crypto.subtle.importKey(
            'raw',
            aesKey,
            {name: 'AES-GCM', length: 256},
            false,
            ['encrypt']
        );

        const ciphertext = await crypto.subtle.encrypt(
            {name: 'AES-GCM', iv: nonce, tagLength: 128},
            cryptoKey,
            plaintext
        );

        return this.combineHPKEMessage(this.keyId, encapsulatedKey, nonce, new Uint8Array(ciphertext));
    }

    /**
     * Decrypt HPKE message
     * @param {Uint8Array} encryptedData - Encrypted data
     * @returns {Object} - Decrypted JavaScript object
     */
    async decrypt(encryptedData) {
        // Decryption happens server-side for security
        // This is a placeholder for future client-side decryption if needed
        throw new Error('Client-side decryption not implemented - server handles this');
    }

    /**
     * Combine HPKE message components
     */
    combineHPKEMessage(keyId, encapsulatedKey, nonce, ciphertext) {
        const keyIdBytes = new Uint8Array(8);
        new DataView(keyIdBytes.buffer).setBigUint64(0, BigInt(keyId), false);

        const combined = new Uint8Array(
            8 + encapsulatedKey.length + nonce.length + ciphertext.length
        );

        let offset = 0;
        combined.set(keyIdBytes, offset);
        offset += 8;
        combined.set(encapsulatedKey, offset);
        offset += encapsulatedKey.length;
        combined.set(nonce, offset);
        offset += nonce.length;
        combined.set(ciphertext, offset);

        return combined;
    }

    base64ToUint8Array(base64) {
        const binaryString = atob(base64);
        const bytes = new Uint8Array(binaryString.length);
        for (let i = 0; i < binaryString.length; i++) {
            bytes[i] = binaryString.charCodeAt(i);
        }
        return bytes;
    }
}

// Global instance
window.QSCClient = new QSCHPKEClient();

