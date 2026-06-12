INSERT INTO admin_account (login_id, password_hash, created_at)
SELECT 'admin', '$2a$10$Tb2D59RIBzIyEDp2T83jfe/bAH0zlBNpStthZOr1E/xdtRvXSTJ6G', NOW()
WHERE NOT EXISTS (SELECT 1 FROM admin_account WHERE login_id = 'admin');
