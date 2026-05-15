-- InkWell Blogging Platform - Local Database Setup Script
-- Run this script in your local MySQL instance to create all required databases.

CREATE DATABASE IF NOT EXISTS auth_db;
CREATE DATABASE IF NOT EXISTS post_db;
CREATE DATABASE IF NOT EXISTS comment_db;
CREATE DATABASE IF NOT EXISTS category_db;
CREATE DATABASE IF NOT EXISTS media_db;
CREATE DATABASE IF NOT EXISTS newsletter_db;
CREATE DATABASE IF NOT EXISTS notification_db;

-- Show databases to verify
SHOW DATABASES;
