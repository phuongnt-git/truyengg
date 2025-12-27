-- Setting categories (3-level hierarchy)
INSERT INTO setting_categories (id, parent_id, code, name, description, is_system)
VALUES
-- Level 0: Root categories
(1, NULL, 'security', 'Security', 'Authentication and encryption settings', true),
(2, NULL, 'integrations', 'Integrations', 'Third-party services configuration', false),
(3, NULL, 'features', 'Features', 'Application features settings', false),
(4, NULL, 'storage', 'Storage', 'Data storage and caching configuration', true),
(5, NULL, 'jobs', 'Jobs', 'Background jobs settings', false),
-- Level 1: Sub-categories
(10, 1, 'jwt', 'JWT', 'JSON Web Token configuration', true),
(11, 1, 'qsc', 'QSC', 'Quantum-Safe Cryptography settings', true),
(12, 1, 'turnstile', 'Turnstile', 'Cloudflare Turnstile CAPTCHA', false),
(13, 1, 'auth', 'Auth', 'Authentication settings', true),
(30, 2, 'oauth2', 'OAuth 2.0', 'Social login providers', false),
(31, 2, 'external-api', 'External APIs', 'External service integrations', false),
(60, 3, 'crawl', 'Crawling', 'Web content crawling settings', false),
(70, 4, 'minio', 'MinIO', 'Object storage configuration', true),
(71, 4, 'cache', 'Cache', 'Application caching settings', true),
(90, 5, 'jobrunr', 'JobRunr', 'Job scheduler configuration', false),
-- Level 2: Leaf categories
(20, 10, 'tokens', 'Tokens', 'Token configuration', true),
(21, 10, 'refresh', 'Refresh', 'Refresh token settings', true),
(110, 11, 'hpke', 'HPKE', 'Hybrid Public Key Encryption', true),
(111, 11, 'jwt', 'JWT', 'JWT quantum-safe signatures', true),
(112, 11, 'keys', 'Keys', 'Key management settings', true),
(113, 11, 'performance', 'Performance', 'Performance tuning', false),
(40, 30, 'google', 'Google', 'Google OAuth configuration', false),
(51, 31, 'imgbb', 'ImgBB', 'ImgBB image upload service', false),
(80, 71, 'image', 'Image', 'Image cache settings', false);

SELECT setval('setting_categories_id_seq', 200);

-- Settings data (description column includes former help_text content)
DELETE
FROM settings;

INSERT INTO settings (category_id, key, value, value_type, constraints, is_sensitive, is_readonly, description)
VALUES
-- JWT Token Settings (category: security.jwt.tokens)
(20, 'secret', 'your-secret-key-change-in-production-min-256-bits', 'SECRET', '{
  "minLength": 32
}', true, false, 'JWT signing secret key. Must be at least 32 characters for security.'),
(20, 'access_expiration', '3600000', 'LONG', '{
  "min": 300000,
  "max": 86400000,
  "unit": "ms"
}', false, false, 'Access token time-to-live in milliseconds. Range: 5 minutes to 24 hours.'),

-- JWT Refresh Token Settings (category: security.jwt.refresh)
(21, 'expiration', '604800000', 'LONG', '{
  "min": 86400000,
  "max": 2592000000,
  "unit": "ms"
}', false, false, 'Refresh token time-to-live in milliseconds. Range: 1 day to 30 days.'),
(21, 'cookie_max_age', '604800', 'INT', '{
  "min": 86400,
  "max": 2592000,
  "unit": "seconds"
}', false, false, 'Cookie max-age in seconds for refresh token cookie.'),
(21, 'max_per_user', '5', 'INT', '{
  "min": 1,
  "max": 20
}', false, false, 'Maximum number of active refresh tokens per user.'),

-- QSC HPKE Settings (category: security.qsc.hpke)
(110, 'enabled', 'false', 'BOOLEAN', NULL, false, false, 'Enable Hybrid Public Key Encryption using Kyber1024.'),
(110, 'kem_algorithm', 'KYBER1024', 'STRING', '{
  "allowedValues": [
    "KYBER512",
    "KYBER768",
    "KYBER1024"
  ]
}', false, false, 'Key Encapsulation Mechanism algorithm for HPKE.'),
(110, 'kdf_algorithm', 'HKDF-SHA256', 'STRING', NULL, false, true, 'Key Derivation Function algorithm (read-only).'),
(110, 'aead_algorithm', 'AES-256-GCM', 'STRING', NULL, false, true, 'Authenticated Encryption algorithm (read-only).'),
(110, 'compression_enabled', 'true', 'BOOLEAN', NULL, false, false, 'Enable GZIP compression before encryption.'),
(110, 'compression_threshold', '1024', 'INT', '{
  "min": 256,
  "max": 10240,
  "unit": "bytes"
}', false, false, 'Compress data larger than this threshold in bytes.'),

-- QSC JWT Settings (category: security.qsc.jwt)
(111, 'access_token_algorithm', 'DILITHIUM3', 'STRING', '{
  "allowedValues": [
    "DILITHIUM2",
    "DILITHIUM3",
    "DILITHIUM5"
  ]
}', false, false, 'Quantum-safe signature algorithm for access tokens.'),
(111, 'refresh_token_algorithm', 'DILITHIUM3', 'STRING', '{
  "allowedValues": [
    "DILITHIUM2",
    "DILITHIUM3",
    "DILITHIUM5"
  ]
}', false, false, 'Quantum-safe signature algorithm for refresh tokens.'),

-- QSC Key Management Settings (category: security.qsc.keys)
(112, 'rotation_enabled', 'true', 'BOOLEAN', NULL, false, false, 'Enable automatic cryptographic key rotation.'),
(112, 'rotation_interval', '86400', 'INT', '{
  "min": 3600,
  "max": 604800,
  "unit": "seconds"
}', false, false, 'Key rotation interval. Range: 1 hour to 7 days.'),
(112, 'rotation_overlap', '3600', 'INT', '{
  "min": 300,
  "max": 7200,
  "unit": "seconds"
}', false, false, 'Key overlap period during rotation. Range: 5 minutes to 2 hours.'),
(112, 'retention_days', '30', 'INT', '{
  "min": 7,
  "max": 90
}', false, false, 'Days to keep expired keys for audit purposes.'),
(112, 'auto_generate_on_startup', 'true', 'BOOLEAN', NULL, false, false,
 'Auto-generate cryptographic keys if missing on startup.'),

-- QSC Performance Settings (category: security.qsc.performance)
(113, 'websocket_enabled', 'true', 'BOOLEAN', NULL, false, false, 'Enable encryption for WebSocket messages.'),
(113, 'websocket_threshold', '256', 'INT', '{
  "min": 0,
  "max": 4096,
  "unit": "bytes"
}', false, false, 'Encrypt WebSocket messages larger than this threshold.'),
(113, 'async_threshold', '1048576', 'INT', '{
  "min": 10240,
  "max": 10485760,
  "unit": "bytes"
}', false, false, 'Use async encryption for data larger than 1MB.'),
(113, 'log_slow_ops', 'true', 'BOOLEAN', NULL, false, false, 'Log slow encryption operations for monitoring.'),
(113, 'slow_threshold_ms', '100', 'INT', '{
  "min": 10,
  "max": 1000,
  "unit": "ms"
}', false, false, 'Threshold in milliseconds for slow operation logging.'),

-- Turnstile Settings (category: security.turnstile)
(12, 'secret_key', '', 'SECRET', NULL, true, false, 'Cloudflare Turnstile secret key for server-side validation.'),
(12, 'site_key', '', 'STRING', NULL, false, false, 'Cloudflare Turnstile site key for client-side widget.'),

-- Auth Settings (category: security.auth)
(13, 'master_password', '$2a$10$MiYdq8jZ0N7k/K0uCwIQvuoHjuyviBdlHsXCUfotgTqYfwUsoFRLi', 'SECRET', NULL, true, false,
 'BCrypt-hashed master password for admin bypass authentication.'),

-- Google OAuth Settings (category: integrations.oauth2.google)
(40, 'client_id', '', 'STRING', NULL, false, false, 'Google OAuth 2.0 client ID from Google Cloud Console.'),
(40, 'client_secret', '', 'SECRET', NULL, true, false, 'Google OAuth 2.0 client secret from Google Cloud Console.'),
(40, 'redirect_uri', 'http://localhost:8080/auth/google/callback', 'URL', NULL, false, false,
 'OAuth callback URL registered in Google Cloud Console.'),

-- ImgBB Settings (category: integrations.external-api.imgbb)
(51, 'api_key', '', 'SECRET', NULL, true, false, 'ImgBB API key for image upload service.'),

-- Crawl Settings (category: features.crawl)
(60, 'max_retries', '3', 'INT', '{
  "min": 1,
  "max": 10
}', false, false, 'Maximum retry attempts for failed crawl requests.'),
(60, 'retry_delay', '2', 'INT', '{
  "min": 1,
  "max": 10,
  "unit": "seconds"
}', false, false, 'Delay in seconds between retry attempts.'),
(60, 'request_timeout', '15', 'INT', '{
  "min": 5,
  "max": 60,
  "unit": "seconds"
}', false, false, 'HTTP request timeout in seconds.'),
(60, 'per_admin_limit', '5', 'INT', '{
  "min": 1,
  "max": 50
}', false, false, 'Maximum concurrent crawl jobs per admin user.'),
(60, 'per_server_limit', '25', 'INT', '{
  "min": 5,
  "max": 100
}', false, false, 'Maximum concurrent crawl jobs per server.'),
(60, 'queue_cron', '0 */5 * * * *', 'STRING', NULL, false, false, 'Cron expression for crawl job queue processor.'),

-- MinIO Settings (category: storage.minio)
(70, 'endpoint', 'http://localhost:9000', 'URL', NULL, false, false, 'MinIO server endpoint URL.'),
(70, 'access_key', 'truyengg', 'STRING', NULL, true, false, 'MinIO access key for authentication.'),
(70, 'secret_key', 'truyengg', 'SECRET', NULL, true, false, 'MinIO secret key for authentication.'),
(70, 'bucket_name', 'truyengg', 'STRING', NULL, false, false, 'Default MinIO bucket name for storage.'),

-- Image Cache Settings (category: storage.cache.image)
(80, 'max_size', '1000', 'INT', '{
  "min": 100,
  "max": 10000
}', false, false, 'Maximum number of images to cache in memory.'),
(80, 'expire_write_hours', '24', 'INT', '{
  "min": 1,
  "max": 168,
  "unit": "hours"
}', false, false, 'Cache write expiration time. Range: 1 hour to 7 days.'),
(80, 'expire_access_hours', '1', 'INT', '{
  "min": 1,
  "max": 24,
  "unit": "hours"
}', false, false, 'Cache access expiration time. Range: 1 to 24 hours.'),
(80, 'compression_enabled', 'true', 'BOOLEAN', NULL, false, false, 'Enable image compression for cached images.'),
(80, 'jpeg_quality', '0.90', 'DOUBLE', '{
  "min": 0.1,
  "max": 1.0
}', false, false, 'JPEG compression quality. Range: 0.1 to 1.0.'),
(80, 'remove_metadata', 'true', 'BOOLEAN', NULL, false, false, 'Remove EXIF metadata from cached images.'),

-- JobRunr Settings (category: jobs.jobrunr)
(90, 'dashboard_enabled', 'true', 'BOOLEAN', NULL, false, false, 'Enable JobRunr web dashboard.'),
(90, 'dashboard_username', 'truyengg', 'STRING', NULL, false, false, 'Username for JobRunr dashboard authentication.'),
(90, 'dashboard_password', 'truyengg', 'PASSWORD', NULL, true, false, 'Password for JobRunr dashboard authentication.'),
(90, 'worker_count', '5', 'INT', '{
  "min": 1,
  "max": 20
}', false, false, 'Number of worker threads for background job processing.');

-- Default users
INSERT INTO users (email, password, username, roles, level, points, xu)
VALUES ('admin@truyengg.com', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'admin', 'ADMIN', 10,
        1000, 5000),
       ('user@truyengg.com', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'testuser', 'USER', 1, 0,
        0),
       ('translator@truyengg.com', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'translator',
        'TRANSLATOR', 5, 500, 2000)
ON CONFLICT (email) DO NOTHING;
