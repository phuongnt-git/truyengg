/**
 * Centralized API Client for TruyenGG
 * Handles all REST API calls with authentication and error handling
 * Supports access token and refresh token with auto-refresh
 */
const ApiClient = {
  baseUrl: '/api',
  isRefreshing: false,
  refreshPromise: null,

  /**
   * Get cookie value by name
   */
  getCookie: function (name) {
    const value = `; ${document.cookie}`;
    const parts = value.split(`; ${name}=`);
    if (parts.length === 2) return parts.pop().split(';').shift();
    return null;
  },

  /**
   * Get access token from cookie or localStorage
   */
  getToken: function () {
    // Try cookie first (set by server)
    const cookieToken = this.getCookie('access_token');
    if (cookieToken) {
      return cookieToken;
    }
    // Fallback to localStorage
    return localStorage.getItem('access_token');
  },

  /**
   * Get refresh token from cookie or localStorage
   */
  getRefreshToken: function () {
    // Try cookie first (set by server)
    const cookieToken = this.getCookie('refresh_token');
    if (cookieToken) {
      return cookieToken;
    }
    // Fallback to localStorage
    return localStorage.getItem('refresh_token');
  },

  /**
   * Set access token and refresh token
   */
  setToken: function (accessToken, refreshToken) {
    if (accessToken) {
      localStorage.setItem('access_token', accessToken);
    } else {
      localStorage.removeItem('access_token');
    }

    if (refreshToken) {
      localStorage.setItem('refresh_token', refreshToken);
    } else {
      localStorage.removeItem('refresh_token');
    }
  },

  /**
   * Clear all tokens
   */
  clearTokens: function () {
    localStorage.removeItem('access_token');
    localStorage.removeItem('refresh_token');
    localStorage.removeItem('token'); // Legacy support
  },

  /**
   * Get request headers with authentication
   */
  getHeaders: function (includeAuth = true) {
    const headers = {
      'Content-Type': 'application/json',
      'Accept': 'application/json'
    };

    if (includeAuth) {
      const token = this.getToken();
      if (token) {
        headers['Authorization'] = token.startsWith('Bearer ') ? token : 'Bearer ' + token;
      }
    }

    return headers;
  },

  /**
   * Refresh access token using refresh token
   */
  refreshAccessToken: function () {
    // If already refreshing, return the existing promise
    if (this.isRefreshing && this.refreshPromise) {
      return this.refreshPromise;
    }

    const refreshToken = this.getRefreshToken();
    if (!refreshToken) {
      return Promise.reject(new Error('No refresh token available'));
    }

    this.isRefreshing = true;
    this.refreshPromise = $.ajax({
      url: this.baseUrl + '/auth/refresh',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        'Authorization': refreshToken.startsWith('Bearer ') ? refreshToken : 'Bearer ' + refreshToken
      },
      dataType: 'json'
    }).then((response) => {
      if (response.success && response.data) {
        const tokenResponse = response.data;
        // Update tokens (cookies are set by server, but also update localStorage)
        this.setToken(tokenResponse.accessToken, tokenResponse.refreshToken);
        return tokenResponse;
      } else {
        throw new Error(response.message || 'Failed to refresh token');
      }
    }).catch((error_) => {
      // Refresh failed, clear tokens
      this.clearTokens();
      throw error_;
    }).finally(() => {
      this.isRefreshing = false;
      this.refreshPromise = null;
    });

    return this.refreshPromise;
  },

  /**
   * Make API request with auto-refresh on 401
   */
  request: function (method, url, data = null, includeAuth = true, useQueryParams = false, retryCount = 0) {
    const self = this;
    // Ensure URL doesn't start with baseUrl to avoid duplication
    let cleanUrl = url.startsWith(this.baseUrl) ? url.substring(this.baseUrl.length) : url;
    let finalUrl = this.baseUrl + cleanUrl;
    let requestData = data;

    // Handle query params for GET or when explicitly requested (e.g., POST with @RequestParam)
    if ((method === 'GET' || useQueryParams) && data) {
      const queryString = Object.keys(data)
        .filter(key => data[key] !== null && data[key] !== undefined && data[key] !== '')
        .map(key => key + '=' + encodeURIComponent(data[key]))
        .join('&');
      if (queryString) {
        finalUrl += (url.indexOf('?') === -1 ? '?' : '&') + queryString;
      }
      requestData = null; // Don't send in body when using query params
    }

    const config = {
      url: finalUrl,
      method: method,
      headers: this.getHeaders(includeAuth),
      dataType: 'json'
    };

    if (requestData && method !== 'GET' && !useQueryParams) {
      config.data = JSON.stringify(requestData);
      config.contentType = 'application/json';
    }

    return $.ajax(config)
      .fail(function (xhr, status, error) {
        console.error('API Error:', {url: config.url, status: status, error: error, response: xhr.responseJSON});

        // Handle 401 Unauthorized - try to refresh token
        if (xhr.status === 401 && includeAuth && retryCount === 0) {
          const refreshToken = self.getRefreshToken();

          if (refreshToken) {
            // Try to refresh token and retry request
            return self.refreshAccessToken()
              .then(() => {
                // Retry the original request with new token
                return self.request(method, url, data, includeAuth, useQueryParams, 1);
              })
              .catch(() => {
                // Refresh failed, handle redirect
                self.handleAuthFailure();
                return $.Deferred().reject(xhr).promise();
              });
          } else {
            // No refresh token, handle auth failure
            self.handleAuthFailure();
          }
        } else if (xhr.status === 401) {
          // Already retried or no refresh token, handle auth failure
          self.handleAuthFailure();
        }
      });
  },

  /**
   * Handle authentication failure - redirect appropriately
   */
  handleAuthFailure: function () {
    this.clearTokens();
    const currentPath = globalThis.location.pathname;

    // Don't redirect if on login/register pages - they handle errors themselves
    if (currentPath.startsWith('/auth/')) {
      return;
    }

    // If on admin page, redirect to login with redirect parameter
    if (currentPath.startsWith('/admin')) {
      globalThis.location.href = '/auth/login?redirect=' + encodeURIComponent(currentPath);
    } else if (currentPath !== '/') {
      // For other pages, redirect to home
      globalThis.location.href = '/';
    }
  },

  /**
   * GET request
   */
  get: function (url, params = null, includeAuth = true) {
    return this.request('GET', url, params, includeAuth);
  },

  /**
   * POST request
   */
  post: function (url, data = null, includeAuth = true) {
    return this.request('POST', url, data, includeAuth);
  },

  /**
   * PUT request
   */
  put: function (url, data = null, includeAuth = true) {
    return this.request('PUT', url, data, includeAuth);
  },

  /**
   * DELETE request
   */
  delete: function (url, includeAuth = true) {
    return this.request('DELETE', url, null, includeAuth);
  },

  /**
   * POST request with query params (for endpoints using @RequestParam)
   */
  postWithQuery: function (url, params = null, includeAuth = true) {
    return this.request('POST', url, params, includeAuth, true);
  }
};
