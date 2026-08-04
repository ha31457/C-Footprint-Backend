CREATE TABLE support_complaints (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(100) NOT NULL,
    complaint_text TEXT NOT NULL,
    reply_text TEXT,
    is_resolved BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    replied_at TIMESTAMP
);
