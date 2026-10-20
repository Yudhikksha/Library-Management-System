package library;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class LibraryService {
    public boolean addBook(String id, String title, String author, int copies) {
        String sql = "INSERT INTO books(book_id,title,author,total_copies,available_copies) VALUES(?,?,?,?,?)";
        try (Connection con = Database.connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, id.trim());
            ps.setString(2, title.trim());
            ps.setString(3, author.trim());
            ps.setInt(4, copies);
            ps.setInt(5, copies);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Could not add book. The ID may already exist. Details: " + e.getMessage());
            return false;
        }
    }

    public boolean addMember(String id, String name) {
        String sql = "INSERT INTO members(member_id,name) VALUES(?,?)";
        try (Connection con = Database.connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, id.trim());
            ps.setString(2, name.trim());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Could not register member. The ID may already exist. Details: " + e.getMessage());
            return false;
        }
    }

    public boolean issueBook(String memberId, String bookId) {
        String memberCheck = "SELECT COUNT(*) FROM members WHERE member_id=?";
        String bookCheck = "SELECT available_copies FROM books WHERE book_id=?";
        String activeCount = "SELECT COUNT(*) FROM loans WHERE member_id=? AND return_date IS NULL";
        String duplicate = "SELECT COUNT(*) FROM loans WHERE member_id=? AND book_id=? AND return_date IS NULL";

        try (Connection con = Database.connect()) {
            con.setAutoCommit(false);
            try {
                if (count(con, memberCheck, memberId) == 0) {
                    System.out.println("Member ID not found.");
                    con.rollback();
                    return false;
                }

                try (PreparedStatement ps = con.prepareStatement(bookCheck)) {
                    ps.setString(1, bookId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            System.out.println("Book ID not found.");
                            con.rollback();
                            return false;
                        }
                        if (rs.getInt(1) <= 0) {
                            System.out.println("No copies are currently available.");
                            con.rollback();
                            return false;
                        }
                    }
                }

                if (count(con, activeCount, memberId) >= 3) {
                    System.out.println("A member may borrow at most 3 books at a time.");
                    con.rollback();
                    return false;
                }

                try (PreparedStatement ps = con.prepareStatement(duplicate)) {
                    ps.setString(1, memberId);
                    ps.setString(2, bookId);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        if (rs.getInt(1) > 0) {
                            System.out.println("This member already has this book checked out.");
                            con.rollback();
                            return false;
                        }
                    }
                }

                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE books SET available_copies=available_copies-1 WHERE book_id=? AND available_copies>0")) {
                    ps.setString(1, bookId);
                    if (ps.executeUpdate() != 1) {
                        con.rollback();
                        System.out.println("Could not issue book; try again.");
                        return false;
                    }
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO loans(member_id,book_id) VALUES(?,?)")) {
                    ps.setString(1, memberId);
                    ps.setString(2, bookId);
                    ps.executeUpdate();
                }
                con.commit();
                System.out.println("Book issued successfully.");
                return true;
            } catch (SQLException e) {
                con.rollback();
                System.out.println("Issue failed: " + e.getMessage());
                return false;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
            return false;
        }
    }

    public boolean returnBook(String memberId, String bookId, int daysKept) {
        if (daysKept < 0) {
            System.out.println("Days kept cannot be negative.");
            return false;
        }
        String sql = "SELECT loan_id FROM loans WHERE member_id=? AND book_id=? AND return_date IS NULL ORDER BY loan_id LIMIT 1";
        try (Connection con = Database.connect()) {
            con.setAutoCommit(false);
            try {
                long loanId;
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setString(1, memberId);
                    ps.setString(2, bookId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            System.out.println("No active borrowing record found for this member and book.");
                            con.rollback();
                            return false;
                        }
                        loanId = rs.getLong("loan_id");
                    }
                }

                int lateDays = Math.max(0, daysKept - 7);
                int fine = lateDays * 2;
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE loans SET return_date=date('now','localtime'), days_kept=?, fine=? WHERE loan_id=?")) {
                    ps.setInt(1, daysKept);
                    ps.setInt(2, fine);
                    ps.setLong(3, loanId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE books SET available_copies=available_copies+1 WHERE book_id=? AND available_copies<total_copies")) {
                    ps.setString(1, bookId);
                    ps.executeUpdate();
                }
                con.commit();
                System.out.println("Book returned successfully.");
                System.out.println("Fine: Rs. " + fine + (fine == 0 ? " (no late fine)" : " (" + lateDays + " late day(s))"));
                return true;
            } catch (SQLException e) {
                con.rollback();
                System.out.println("Return failed: " + e.getMessage());
                return false;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
            return false;
        }
    }

    public void showBooks() {
        String sql = "SELECT book_id,title,author,total_copies,available_copies FROM books ORDER BY title";
        try (Connection con = Database.connect();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            System.out.println("\n--- BOOK CATALOGUE ---");
            boolean found = false;
            while (rs.next()) {
                found = true;
                System.out.println(rs.getString("book_id") + " | " + rs.getString("title")
                        + " | " + rs.getString("author") + " | total: " + rs.getInt("total_copies")
                        + " | available: " + rs.getInt("available_copies"));
            }
            if (!found) System.out.println("No books added yet.");
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
        }
    }

    public void showMembers() {
        String sql = "SELECT member_id,name FROM members ORDER BY name";
        try (Connection con = Database.connect();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            System.out.println("\n--- MEMBERS ---");
            boolean found = false;
            while (rs.next()) {
                found = true;
                System.out.println(rs.getString("member_id") + " | " + rs.getString("name"));
            }
            if (!found) System.out.println("No members registered yet.");
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
        }
    }

    public void searchById(String id) {
        String sql = "SELECT book_id,title,author,total_copies,available_copies FROM books WHERE book_id=?";
        try (Connection con = Database.connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) printBook(rs);
                else System.out.println("No book found with that ID.");
            }
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
        }
    }

    public void searchByTitle(String keyword) {
        String sql = "SELECT book_id,title,author,total_copies,available_copies FROM books WHERE title LIKE ? ORDER BY title";
        try (Connection con = Database.connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, "%" + keyword + "%");
            try (ResultSet rs = ps.executeQuery()) {
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    printBook(rs);
                }
                if (!found) System.out.println("No matching books found.");
            }
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
        }
    }

    public void showSummary() {
        String sql = """
                SELECT
                  (SELECT COUNT(*) FROM books) AS book_titles,
                  (SELECT COUNT(*) FROM members) AS members,
                  (SELECT COALESCE(SUM(total_copies),0) FROM books) AS total_copies,
                  (SELECT COALESCE(SUM(available_copies),0) FROM books) AS available_copies,
                  (SELECT COUNT(*) FROM loans) AS total_issues,
                  (SELECT COALESCE(SUM(fine),0) FROM loans) AS fines_recorded
                """;
        try (Connection con = Database.connect(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                System.out.println("\n--- LIBRARY SUMMARY ---");
                System.out.println("Book titles: " + rs.getInt("book_titles"));
                System.out.println("Registered members: " + rs.getInt("members"));
                System.out.println("Total physical copies: " + rs.getInt("total_copies"));
                System.out.println("Available copies: " + rs.getInt("available_copies"));
                System.out.println("Total issue records: " + rs.getInt("total_issues"));
                System.out.println("Fines recorded: Rs. " + rs.getInt("fines_recorded"));
            }
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
        }
    }

    private int count(Connection con, String sql, String value) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private void printBook(ResultSet rs) throws SQLException {
        System.out.println(rs.getString("book_id") + " | " + rs.getString("title")
                + " | " + rs.getString("author") + " | total: " + rs.getInt("total_copies")
                + " | available: " + rs.getInt("available_copies"));
    }
}
