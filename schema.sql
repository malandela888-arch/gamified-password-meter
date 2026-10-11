-- ============================================
-- Password Strength Meter — Database Schema
-- Matches the same columns as your Google Sheet.
-- Run this in MySQL Workbench (or the MySQL command line)
-- before starting the Java backend.
-- ============================================

CREATE DATABASE IF NOT EXISTS password_meter;
USE password_meter;

CREATE TABLE IF NOT EXISTS responses (
    id INT AUTO_INCREMENT PRIMARY KEY,
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    participant_id VARCHAR(100),
    group_name VARCHAR(50),
    password_hash VARCHAR(100),
    score INT,
    time_taken_ms INT,
    password_length INT,
    has_upper BOOLEAN,
    has_lower BOOLEAN,
    has_digit BOOLEAN,
    has_special BOOLEAN,
    motivation INT,
    engagement INT
);
