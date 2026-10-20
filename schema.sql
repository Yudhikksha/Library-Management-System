-- These tables are created automatically when the Java program starts.
-- This file is provided for documentation/reference.

CREATE TABLE books (
    book_id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    author TEXT NOT NULL,
    total_copies INTEGER NOT NULL CHECK(total_copies > 0),
    available_copies INTEGER NOT NULL CHECK(available_copies >= 0 AND available_copies <= total_copies)
);

CREATE TABLE members (
    member_id TEXT PRIMARY KEY,
    name TEXT NOT NULL
);

CREATE TABLE loans (
    loan_id INTEGER PRIMARY KEY AUTOINCREMENT,
    member_id TEXT NOT NULL,
    book_id TEXT NOT NULL,
    issue_date TEXT NOT NULL,
    return_date TEXT,
    days_kept INTEGER,
    fine INTEGER DEFAULT 0,
    FOREIGN KEY(member_id) REFERENCES members(member_id),
    FOREIGN KEY(book_id) REFERENCES books(book_id)
);
