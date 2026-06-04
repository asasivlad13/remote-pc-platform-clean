ALTER TABLE education_sessions
    ADD COLUMN IF NOT EXISTS teacher_display_name VARCHAR(100);
