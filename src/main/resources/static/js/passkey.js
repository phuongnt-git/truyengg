/**
 * Passkey (WebAuthn) Client Module
 * Handles passkey registration and authentication flows
 */
const PasskeyClient = {
    /**
     * Check if WebAuthn is supported by the browser
     */
    isSupported() {
        return window.PublicKeyCredential !== undefined &&
            typeof window.PublicKeyCredential === 'function';
    },

    /**
     * Detect current device information
     * @returns {Object} Device info with name, type, icon, and suggestions
     */
    detectDevice() {
        const ua = navigator.userAgent;
        const platform = navigator.platform || '';
        const vendor = navigator.vendor || '';

        let deviceType = 'desktop';
        let os = 'Unknown';
        let browser = 'Browser';
        let deviceModel = '';
        let icon = 'monitor';

        // Detect OS
        if (/iPhone/.test(ua)) {
            os = 'iPhone';
            deviceType = 'mobile';
            icon = 'smartphone';
            // Try to get iPhone model
            const match = ua.match(/iPhone\s*(\d+)?/);
            if (match) deviceModel = match[0];
        } else if (/iPad/.test(ua)) {
            os = 'iPad';
            deviceType = 'tablet';
            icon = 'tablet';
        } else if (/Android/.test(ua)) {
            deviceType = /Mobile/.test(ua) ? 'mobile' : 'tablet';
            icon = deviceType === 'mobile' ? 'smartphone' : 'tablet';
            // Try to get Android device model
            const match = ua.match(/;\s*([^;]+)\s*Build/);
            if (match) {
                deviceModel = match[1].trim();
                os = 'Android';
            } else {
                os = 'Android Device';
            }
        } else if (/Mac/.test(platform) || /Mac/.test(ua)) {
            os = 'Mac';
            if (/MacBook Pro/.test(ua) || (window.screen && window.screen.width <= 1680)) {
                deviceModel = 'MacBook';
            } else {
                deviceModel = 'Mac';
            }
            icon = 'monitor';
        } else if (/Win/.test(platform) || /Windows/.test(ua)) {
            os = 'Windows';
            deviceModel = 'Windows PC';
            icon = 'monitor';
        } else if (/Linux/.test(platform)) {
            os = 'Linux';
            deviceModel = 'Linux PC';
            icon = 'monitor';
        } else if (/CrOS/.test(ua)) {
            os = 'Chrome OS';
            deviceModel = 'Chromebook';
            icon = 'monitor';
        }

        // Detect Browser
        if (/Edg\//.test(ua)) {
            browser = 'Edge';
        } else if (/Chrome\//.test(ua) && !/Edg\//.test(ua)) {
            browser = 'Chrome';
        } else if (/Safari\//.test(ua) && !/Chrome\//.test(ua)) {
            browser = 'Safari';
        } else if (/Firefox\//.test(ua)) {
            browser = 'Firefox';
        } else if (/Opera|OPR\//.test(ua)) {
            browser = 'Opera';
        }

        // Build suggested name
        let suggestedName = deviceModel || os;
        if (suggestedName === 'Mac' || suggestedName === 'MacBook') {
            // Check if it's likely a MacBook Pro/Air based on screen
            if (window.devicePixelRatio >= 2) {
                suggestedName = 'MacBook Pro';
            }
        }

        // Generate suggestions
        const suggestions = [];

        // Primary suggestion - most specific
        if (deviceModel && deviceModel !== os) {
            suggestions.push({name: deviceModel, icon: icon});
        }

        // OS-based suggestion
        if (os !== 'Unknown') {
            const osName = os === 'Mac' ? 'MacBook' : (os === 'Windows' ? 'Windows PC' : os);
            if (!suggestions.find(s => s.name === osName)) {
                suggestions.push({name: osName, icon: icon});
            }
        }

        // Browser-based suggestion
        suggestions.push({name: `${browser} on ${os}`, icon: icon});

        // Context-based suggestions
        suggestions.push({name: 'Work Computer', icon: 'briefcase'});
        suggestions.push({name: 'Personal Device', icon: 'home'});
        if (deviceType === 'mobile') {
            suggestions.push({name: 'My Phone', icon: 'smartphone'});
        }

        return {
            type: deviceType,
            os: os,
            browser: browser,
            model: deviceModel,
            icon: icon,
            suggestedName: suggestedName,
            suggestions: suggestions.slice(0, 5) // Limit to 5 suggestions
        };
    },

    /**
     * Check if platform authenticator is available (Touch ID, Face ID, Windows Hello)
     */
    async isPlatformAuthenticatorAvailable() {
        if (!this.isSupported()) return false;
        try {
            return await PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable();
        } catch (e) {
            console.error('Error checking platform authenticator:', e);
            return false;
        }
    },

    /**
     * Check if conditional mediation is available (autofill UI)
     */
    async isConditionalMediationAvailable() {
        if (!this.isSupported()) return false;
        try {
            return await PublicKeyCredential.isConditionalMediationAvailable?.() ?? false;
        } catch (e) {
            return false;
        }
    },

    /**
     * Start passkey registration
     * @param {string} deviceName - User-friendly name for the passkey
     * @returns {Promise<Object>} The registered passkey response
     */
    async register(deviceName) {
        const self = this;
        if (!this.isSupported()) {
            throw new Error('WebAuthn is not supported in this browser');
        }

        // Step 1: Get registration options from server
        const startResponse = await new Promise((resolve, reject) => {
            ApiClient.post('/passkey/register/start', {deviceName: deviceName}, true)
                .done(response => resolve(response))
                .fail((xhr, status, error) => reject(new Error(xhr.responseJSON?.message || 'Failed to start registration')));
        });

        if (!startResponse.success) {
            throw new Error(startResponse.message || 'Failed to start registration');
        }

        // Parse the options JSON - handle both wrapped and unwrapped formats
        const options = JSON.parse(startResponse.data);
        const pkOptions = options.publicKey || options;

        if (!pkOptions.user || !pkOptions.user.id) {
            throw new Error('Invalid server response: missing user information');
        }

        // Convert base64url strings to ArrayBuffers
        const publicKeyOptions = {
            ...pkOptions,
            challenge: self.base64UrlToArrayBuffer(pkOptions.challenge),
            user: {
                ...pkOptions.user,
                id: self.base64UrlToArrayBuffer(pkOptions.user.id)
            },
            excludeCredentials: (pkOptions.excludeCredentials || []).map(cred => ({
                ...cred,
                id: self.base64UrlToArrayBuffer(cred.id)
            }))
        };

        // Step 2: Create credential using browser API
        let credential;
        try {
            credential = await navigator.credentials.create({
                publicKey: publicKeyOptions
            });
        } catch (e) {
            if (e.name === 'NotAllowedError') {
                throw new Error('Registration was cancelled or timed out');
            }
            throw e;
        }

        // Step 3: Prepare response for server
        const credentialResponse = {
            id: credential.id,
            rawId: self.arrayBufferToBase64Url(credential.rawId),
            type: credential.type,
            response: {
                clientDataJSON: self.arrayBufferToBase64Url(credential.response.clientDataJSON),
                attestationObject: self.arrayBufferToBase64Url(credential.response.attestationObject)
            },
            clientExtensionResults: credential.getClientExtensionResults()
        };

        // Add transports if available
        if (credential.response.getTransports) {
            credentialResponse.response.transports = credential.response.getTransports();
        }

        // Step 4: Send credential to server for verification
        const finishResponse = await new Promise((resolve, reject) => {
            ApiClient.post('/passkey/register/finish', {
                deviceName: deviceName,
                credential: JSON.stringify(credentialResponse)
            }, true)
                .done(response => resolve(response))
                .fail((xhr, status, error) => reject(new Error(xhr.responseJSON?.message || 'Failed to complete registration')));
        });

        if (!finishResponse.success) {
            throw new Error(finishResponse.message || 'Failed to complete registration');
        }

        return finishResponse.data;
    },

    /**
     * Authenticate with passkey
     * @param {string} [email] - Optional email to filter credentials
     * @returns {Promise<Object>} Token response with accessToken and refreshToken
     */
    async authenticate(email) {
        const self = this;
        
        if (!this.isSupported()) {
            throw new Error('WebAuthn is not supported in this browser');
        }

        // Step 1: Get authentication options from server
        const startResponse = await new Promise((resolve, reject) => {
            ApiClient.post('/passkey/login/start', email ? {email: email} : {}, false)
                .done(response => resolve(response))
                .fail((xhr, status, error) => reject(new Error(xhr.responseJSON?.message || 'Failed to start authentication')));
        });

        if (!startResponse.success) {
            throw new Error(startResponse.message || 'Failed to start authentication');
        }

        // Parse the response
        const responseData = JSON.parse(startResponse.data);
        const requestId = responseData.requestId;
        
        // Handle both structures: { publicKey: {...} } or direct {...}
        const credOptions = responseData.publicKeyCredentialRequestOptions;
        const options = credOptions.publicKey || credOptions;

        // Convert base64url strings to ArrayBuffers
        const publicKeyOptions = {
            ...options,
            challenge: self.base64UrlToArrayBuffer(options.challenge),
            allowCredentials: (options.allowCredentials || []).map(cred => ({
                ...cred,
                id: self.base64UrlToArrayBuffer(cred.id)
            }))
        };

        // Step 2: Get assertion using browser API
        let credential;
        try {
            credential = await navigator.credentials.get({
                publicKey: publicKeyOptions
            });
        } catch (e) {
            if (e.name === 'NotAllowedError') {
                throw new Error('Authentication was cancelled or timed out');
            }
            throw e;
        }

        // Step 3: Prepare response for server
        const assertionResponse = {
            id: credential.id,
            rawId: self.arrayBufferToBase64Url(credential.rawId),
            type: credential.type,
            response: {
                clientDataJSON: self.arrayBufferToBase64Url(credential.response.clientDataJSON),
                authenticatorData: self.arrayBufferToBase64Url(credential.response.authenticatorData),
                signature: self.arrayBufferToBase64Url(credential.response.signature)
            },
            clientExtensionResults: credential.getClientExtensionResults()
        };

        // Add userHandle if present
        if (credential.response.userHandle) {
            assertionResponse.response.userHandle = self.arrayBufferToBase64Url(credential.response.userHandle);
        }

        // Step 4: Send assertion to server for verification
        const finishResponse = await new Promise((resolve, reject) => {
            ApiClient.post(
                '/passkey/login/finish?requestId=' + encodeURIComponent(requestId),
                {credential: JSON.stringify(assertionResponse)},
                false
            )
                .done(response => resolve(response))
                .fail((xhr, status, error) => reject(new Error(xhr.responseJSON?.message || 'Failed to complete authentication')));
        });

        if (!finishResponse.success) {
            throw new Error(finishResponse.message || 'Failed to complete authentication');
        }

        return finishResponse.data;
    },

    /**
     * Get list of user's registered passkeys
     * @returns {Promise<Array>} List of passkey objects
     */
    listPasskeys() {
        return new Promise((resolve, reject) => {
            ApiClient.get('/passkey/list', null, true)
                .done(function (response) {
                    if (response && response.success) {
                        resolve(response.data || []);
                    } else {
                        reject(new Error(response?.message || 'Failed to fetch passkeys'));
                    }
                })
                .fail(function (xhr, status, error) {
                    reject(new Error(xhr.responseJSON?.message || error || 'Failed to fetch passkeys'));
                });
        });
    },

    /**
     * Delete a passkey
     * @param {string} passkeyId - UUID of the passkey to delete
     * @returns {Promise<void>}
     */
    deletePasskey(passkeyId) {
        return new Promise((resolve, reject) => {
            ApiClient.delete('/passkey/' + passkeyId, true)
                .done(function (response) {
                    if (response && response.success) {
                        resolve();
                    } else {
                        reject(new Error(response?.message || 'Failed to delete passkey'));
                    }
                })
                .fail(function (xhr, status, error) {
                    reject(new Error(xhr.responseJSON?.message || 'Failed to delete passkey'));
                });
        });
    },

    /**
     * Rename a passkey
     * @param {string} passkeyId - UUID of the passkey
     * @param {string} newName - New device name
     * @returns {Promise<Object>} Updated passkey object
     */
    renamePasskey(passkeyId, newName) {
        return new Promise((resolve, reject) => {
            ApiClient.put('/passkey/' + passkeyId + '/name', {name: newName}, true)
                .done(function (response) {
                    if (response && response.success) {
                        resolve(response.data);
                    } else {
                        reject(new Error(response?.message || 'Failed to rename passkey'));
                    }
                })
                .fail(function (xhr, status, error) {
                    reject(new Error(xhr.responseJSON?.message || 'Failed to rename passkey'));
                });
        });
    },

    // Utility functions for ArrayBuffer/Base64URL conversion

    /**
     * Convert base64url string to ArrayBuffer
     */
    base64UrlToArrayBuffer(base64url) {
        if (!base64url) return new ArrayBuffer(0);

        // Convert base64url to base64
        let base64 = base64url.replace(/-/g, '+').replace(/_/g, '/');

        // Add padding if needed
        while (base64.length % 4) {
            base64 += '=';
        }

        // Decode base64
        const binaryString = atob(base64);
        const bytes = new Uint8Array(binaryString.length);
        for (let i = 0; i < binaryString.length; i++) {
            bytes[i] = binaryString.charCodeAt(i);
        }
        return bytes.buffer;
    },

    /**
     * Convert ArrayBuffer to base64url string
     */
    arrayBufferToBase64Url(buffer) {
        const bytes = new Uint8Array(buffer);
        let binary = '';
        for (let i = 0; i < bytes.length; i++) {
            binary += String.fromCharCode(bytes[i]);
        }

        // Convert to base64
        const base64 = btoa(binary);

        // Convert to base64url
        return base64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
    }
};

// Export for module systems if available
if (typeof module !== 'undefined' && module.exports) {
    module.exports = PasskeyClient;
}

