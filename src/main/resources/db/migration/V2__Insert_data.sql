-- Insert default settings
INSERT INTO settings (setting_key, setting_value, description)
VALUES ('google_client_id', '', 'Google API Client ID'),
       ('google_client_secret', '', 'Google API Client Secret'),
       ('google_redirect_uri', 'http://localhost:8080/auth/google/callback', 'Google API Redirect URI'),
       ('turnstile_secret_key', '', 'Cloudflare Turnstile Secret Key'),
       ('turnstile_site_key', '', 'Cloudflare Turnstile Site Key'),
       ('imgbb_api_key', '', 'ImgBB API Key for image uploads'),
       ('master_password', '$2a$10$ULF6E9lu40ihG4zjptB8r.0VNaD4ZvTtFpG7EVqTyziJ4fE.LfJWK',
        'Master password để bypass đăng nhập cho tài khoản ADMIN. Để trống để tắt tính năng này.') ON CONFLICT (setting_key) DO NOTHING;

-- Insert mock users (password: 123456)
INSERT INTO users (email, password, username, roles, level, points, xu)
VALUES ('admin@truyengg.com', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'admin', 'ADMIN', 10,
        1000, 5000),
       ('user@truyengg.com', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'testuser', 'USER', 1, 0,
        0),
       ('translator@truyengg.com', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'translator',
        'TRANSLATOR', 5, 500, 2000) ON CONFLICT (email) DO NOTHING;
