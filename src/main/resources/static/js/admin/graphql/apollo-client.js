/**
 * Apollo Client Setup for Crawl GraphQL API
 * Supports both HTTP queries/mutations and WebSocket subscriptions
 * ES Module version with CSRF support
 */

// Configuration
const CONFIG = {
    httpEndpoint: '/graphql',
    wsEndpoint: `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/graphql`,
    defaultFetchPolicy: 'network-only',
    connectionTimeout: 60000
};

// Apollo Client instance
let client = null;
let wsClient = null;
let initialized = false;

/**
 * Get CSRF token from meta tags or cookies
 */
function getCsrfToken() {
    // Try meta tag first (Spring Security default)
    const metaToken = document.querySelector('meta[name="_csrf"]');
    const metaHeader = document.querySelector('meta[name="_csrf_header"]');

    if (metaToken && metaHeader) {
        return {
            token: metaToken.getAttribute('content'),
            header: metaHeader.getAttribute('content')
        };
    }

    // Try cookie (XSRF-TOKEN is common)
    const cookies = document.cookie.split(';');
    for (const cookie of cookies) {
        const [name, value] = cookie.trim().split('=');
        if (name === 'XSRF-TOKEN') {
            return {
                token: decodeURIComponent(value),
                header: 'X-XSRF-TOKEN'
            };
        }
    }

    return null;
}

/**
 * Get JWT token from localStorage or ApiClient
 */
function getJwtToken() {
    // Try ApiClient first (if available)
    if (typeof ApiClient !== 'undefined' && ApiClient.getToken) {
        const token = ApiClient.getToken();
        if (token) return token;
    }

    // Try localStorage with common keys
    const token = localStorage.getItem('access_token')
        || localStorage.getItem('token')
        || localStorage.getItem('authToken')
        || localStorage.getItem('jwt');
    if (token) return token;

    // Try cookies
    const cookies = document.cookie.split(';');
    for (const cookie of cookies) {
        const [name, value] = cookie.trim().split('=');
        if (name === 'access_token' || name === 'token' || name === 'jwt' || name === 'authToken') {
            return decodeURIComponent(value);
        }
    }

    return null;
}

/**
 * Build headers with CSRF token and JWT
 */
function buildHeaders() {
    const headers = {
        'Content-Type': 'application/json',
        'Accept': 'application/json'
    };

    // Add CSRF token
    const csrf = getCsrfToken();
    if (csrf) {
        headers[csrf.header] = csrf.token;
    }

    // Add JWT token
    const jwt = getJwtToken();
    if (jwt) {
        headers['Authorization'] = `Bearer ${jwt}`;
    }

    return headers;
}

/**
 * GraphQL tag function for tagged template literals
 */
export function gql(strings, ...values) {
    let result = strings[0];
    for (let i = 0; i < values.length; i++) {
        result += values[i] + strings[i + 1];
    }
    return parseGraphQL(result);
}

/**
 * Simple GraphQL parser
 */
function parseGraphQL(source) {
    return {
        kind: 'Document',
        definitions: [{
            kind: 'OperationDefinition',
            operation: getOperation(source),
            selectionSet: {kind: 'SelectionSet', selections: []},
            variableDefinitions: [],
            name: null
        }],
        loc: {source: {body: source}},
        _source: source
    };
}

function getOperation(source) {
    const trimmed = source.trim();
    if (trimmed.startsWith('mutation')) return 'mutation';
    if (trimmed.startsWith('subscription')) return 'subscription';
    return 'query';
}

/**
 * Execute GraphQL request with proper error handling
 */
async function executeGraphQL(queryString, variables) {
    const headers = buildHeaders();

    const response = await fetch(CONFIG.httpEndpoint, {
        method: 'POST',
        headers: headers,
        credentials: 'same-origin',
        body: JSON.stringify({
            query: queryString,
            variables
        })
    });

    // Check content type
    const contentType = response.headers.get('content-type') || '';

    if (!contentType.includes('application/json')) {
        // Got HTML or other non-JSON response
        const text = await response.text();
        console.error('[Apollo] Non-JSON response:', text.substring(0, 200));

        if (response.status === 401 || response.status === 403) {
            throw new Error('Authentication required. Please refresh the page and login.');
        }
        if (text.includes('<!DOCTYPE') || text.includes('<html')) {
            throw new Error('Server returned HTML instead of JSON. The GraphQL endpoint may not be available.');
        }
        throw new Error(`Unexpected response: ${response.status} ${response.statusText}`);
    }

    const result = await response.json();

    if (result.errors && result.errors.length > 0) {
        const errorMessage = result.errors.map(e => e.message).join(', ');
        throw new Error(errorMessage);
    }

    return result;
}

/**
 * Initialize Apollo Client with HTTP and WebSocket support
 */
function init() {
    if (initialized) return client;

    try {
        // Check if Apollo dependencies are loaded
        if (typeof ApolloClient === 'undefined' || typeof ApolloLink === 'undefined') {
            console.log('[Apollo] Apollo libraries not loaded. Using fetch-based client.');
            return createFallbackClient();
        }

        // Create auth link that adds dynamic headers to each request
        const authLink = new ApolloLink.ApolloLink((operation, forward) => {
            operation.setContext(({headers = {}}) => ({
                headers: {
                    ...headers,
                    ...buildHeaders()
                }
            }));
            return forward(operation);
        });

        // Create HTTP link (without static headers - auth link handles them)
        const httpLink = new ApolloLink.HttpLink({
            uri: CONFIG.httpEndpoint,
            credentials: 'same-origin'
        });

        // Combine auth link with http link
        let link = ApolloLink.concat(authLink, httpLink);

        if (typeof graphqlWs !== 'undefined') {
            // Create WebSocket client
            wsClient = graphqlWs.createClient({
                url: CONFIG.wsEndpoint,
                connectionParams: () => {
                    const token = getJwtToken();
                    return token ? {Authorization: `Bearer ${token}`} : {};
                },
                keepAlive: 10000,
                retryAttempts: 5,
                shouldRetry: () => true,
                on: {
                    connected: () => console.log('[Apollo] WebSocket connected'),
                    closed: () => console.log('[Apollo] WebSocket closed'),
                    error: (err) => console.error('[Apollo] WebSocket error:', err)
                }
            });

            // Create WebSocket link
            const wsLink = new ApolloLink.GraphQLWsLink(wsClient);

            // Split link: subscriptions go to WS, queries/mutations go to HTTP
            link = ApolloLink.split(
                ({query}) => {
                    const definition = ApolloLink.getMainDefinition(query);
                    return (
                        definition.kind === 'OperationDefinition' &&
                        definition.operation === 'subscription'
                    );
                },
                wsLink,
                ApolloLink.concat(authLink, httpLink)
            );
        }

        // Create Apollo Client
        client = new ApolloClient.ApolloClient({
            link: link,
            cache: new ApolloClient.InMemoryCache({
                typePolicies: {
                    CrawlJob: {
                        keyFields: ['id']
                    },
                    Query: {
                        fields: {
                            crawlJobs: {
                                keyArgs: ['filter', 'sort'],
                                merge(existing, incoming, {args}) {
                                    if (!args?.after) return incoming;
                                    return {
                                        ...incoming,
                                        edges: [...(existing?.edges || []), ...incoming.edges]
                                    };
                                }
                            }
                        }
                    }
                }
            }),
            defaultOptions: {
                watchQuery: {fetchPolicy: CONFIG.defaultFetchPolicy},
                query: {fetchPolicy: CONFIG.defaultFetchPolicy}
            }
        });

        initialized = true;
        console.log('[Apollo] Client initialized with Apollo libraries');
        return client;

    } catch (error) {
        console.error('[Apollo] Init error:', error);
        return createFallbackClient();
    }
}

/**
 * Fallback fetch-based client when Apollo is not available
 */
function createFallbackClient() {
    initialized = true;
    console.log('[Apollo] Using fetch-based fallback client');

    client = {
        query: async ({query, variables, fetchPolicy}) => {
            const queryString = query._source || query.loc?.source?.body;
            return executeGraphQL(queryString, variables);
        },

        mutate: async ({mutation, variables}) => {
            const queryString = mutation._source || mutation.loc?.source?.body;
            return executeGraphQL(queryString, variables);
        },

        subscribe: ({query, variables}) => {
            // Fallback: polling-based subscription
            console.warn('[Apollo] WebSocket not available, subscriptions will use polling');
            return {
                subscribe: (callbacks) => {
                    let active = true;
                    let lastData = null;

                    const poll = async () => {
                        while (active) {
                            try {
                                const queryString = query._source || query.loc?.source?.body;
                                const result = await executeGraphQL(queryString, variables);

                                // Only notify if data changed
                                const dataStr = JSON.stringify(result.data);
                                if (dataStr !== lastData) {
                                    lastData = dataStr;
                                    if (callbacks.next) {
                                        callbacks.next(result);
                                    }
                                }
                            } catch (error) {
                                console.error('[Apollo] Poll error:', error);
                                if (callbacks.error) callbacks.error(error);
                            }
                            await new Promise(r => setTimeout(r, 3000));
                        }
                    };

                    poll();
                    return {
                        unsubscribe: () => {
                            active = false;
                        }
                    };
                }
            };
        },

        resetStore: () => {
        }
    };

    return client;
}

/**
 * Get the Apollo Client instance
 */
export function getClient() {
    if (!initialized) init();
    return client;
}

/**
 * Apollo Client proxy for direct use
 */
export const apolloClient = {
    query: async (options) => {
        if (!initialized) init();
        return client.query(options);
    },

    mutate: async (options) => {
        if (!initialized) init();
        return client.mutate(options);
    },

    subscribe: (options) => {
        if (!initialized) init();
        return client.subscribe(options);
    },

    clearCache: () => {
        if (client?.resetStore) {
            client.resetStore();
        }
    }
};

// Also expose as window globals for non-module scripts
window.CrawlApolloClient = {
    init,
    getClient,
    query: apolloClient.query,
    mutate: apolloClient.mutate,
    subscribe: apolloClient.subscribe,
    clearCache: apolloClient.clearCache,
    gql
};

// Initialize on load
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
} else {
    init();
}
