/**
 * Admin Authentication Helper
 * Handles auto-refresh and redirect for admin pages
 */
const AdminAuth = {
    /**
     * Initialize admin authentication check
     */
    init: function () {
        // Check if we're on an admin page
        if (!globalThis.location.pathname.startsWith('/admin')) {
            return;
        }

        // Verify we have tokens
        const accessToken = ApiClient.getToken();
        const refreshToken = ApiClient.getRefreshToken();

        if (!accessToken && !refreshToken) {
            // No tokens, redirect to login
            this.redirectToLogin();
            return;
        }

        // If we have refresh token but no access token, try to refresh
        if (!accessToken && refreshToken) {
            this.refreshAndRetry();
        }
    },

    /**
     * Redirect to login page with redirect parameter
     */
    redirectToLogin: function () {
        const currentPath = globalThis.location.pathname;
        globalThis.location.href = `/auth/login?redirect=${encodeURIComponent(currentPath)}`;
    },

    /**
     * Try to refresh access token and reload page
     */
    refreshAndRetry: function () {
        ApiClient.refreshAccessToken()
            .then(() => {
                // Refresh successful, reload page
                globalThis.location.reload();
            })
            .catch(() => {
                // Refresh failed, redirect to login
                this.redirectToLogin();
            });
    }
};

/**
 * Logout function
 * Handles logout for both admin and normal users
 * This function is available globally for use in templates
 */
function logout() {
    const accessToken = ApiClient.getToken();
    const refreshToken = ApiClient.getRefreshToken();
    const currentPath = globalThis.location.pathname;
    const isAdminPage = currentPath.startsWith('/admin');

    // If we have tokens, call API to blacklist them
    if (accessToken || refreshToken) {
        ApiClient.post('/auth/logout', null, true).done(function () {
            // Clear tokens from localStorage
            ApiClient.clearTokens();

            // Show success message if showToast is available
            if (typeof showToast === 'function') {
                showToast('Đăng xuất thành công', true);
            }

            // Redirect after short delay
            setTimeout(function () {
                if (isAdminPage) {
                    globalThis.location.href = '/auth/login';
                } else {
                    globalThis.location.href = '/';
                }
            }, 1000);
        }).fail(function () {
            // Even if API call fails, clear tokens and redirect
            ApiClient.clearTokens();

            if (isAdminPage) {
                globalThis.location.href = '/auth/login';
            } else {
                globalThis.location.href = '/';
            }
        });
    } else if (isAdminPage) {
        // No tokens, just redirect
        globalThis.location.href = '/auth/login';
    } else {
        globalThis.location.href = '/';
    }
}

// Auto-init on page load
$(document).ready(function () {
    AdminAuth.init();
});

