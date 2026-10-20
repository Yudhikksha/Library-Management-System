package library;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {
    private static final String URL = "jdbc:sqlite:library.db";

    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    public static void initialize() {
        String books = """
                CREATE TABLE IF NOT EXISTS books (
                    book_id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    author TEXT NOT NULL,
                    total_copies INTEGER NOT NULL CHECK(total_copies > 0),
                    available_copies INTEGER NOT NULL CHECK(available_copies >= 0),
                    CHECK(available_copies <= total_copies)
                )
                """;
        String members = """
                CREATE TABLE IF NOT EXISTS members (
                    member_id TEXT PRIMARY KEY,
                    name TEXT NOT NULL
                )
                """;
        String loans = """
                CREATE TABLE IF NOT EXISTS loans (
                    loan_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    member_id TEXT NOT NULL,
                    book_id TEXT NOT NULL,
                    issue_date TEXT NOT NULL DEFAULT (date('now','localtime')),
                    return_date TEXT,
                    days_kept INTEGER,
                    fine INTEGER DEFAULT 0,
                    FOREIGN KEY(member_id) REFERENCES members(member_id),
                    FOREIGN KEY(book_id) REFERENCES books(book_id)
                )
                """;

        try (Connection con = connect(); Statement st = con.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute(books);
            st.execute(members);
            st.execute(loans);
        } catch (SQLException e) {
            System.out.println("Could not initialize database: " + e.getMessage());
        }
    }

    private Database() {
    }
}
