package library;

import java.util.Scanner;

public class LibraryApp {
    private static final Scanner input = new Scanner(System.in);
    private static final LibraryService library = new LibraryService();

    public static void main(String[] args) {
        Database.initialize();
        System.out.println("Database ready. Your records are saved in library.db.");

        int choice;
        do {
            showMenu();
            choice = readInt("Enter choice: ");
            switch (choice) {
                case 1 -> addBook();
                case 2 -> addMember();
                case 3 -> issueBook();
                case 4 -> returnBook();
                case 5 -> searchBook();
                case 6 -> library.showBooks();
                case 7 -> library.showMembers();
                case 8 -> library.showSummary();
                case 9 -> System.out.println("Goodbye!");
                default -> System.out.println("Invalid choice. Enter 1 to 9.");
            }
            System.out.println();
        } while (choice != 9);
        input.close();
    }

    private static void showMenu() {
        System.out.println("===== LIBRARY MANAGEMENT SYSTEM =====");
        System.out.println("1. Add Book");
        System.out.println("2. Register Member");
        System.out.println("3. Issue Book");
        System.out.println("4. Return Book");
        System.out.println("5. Search Book");
        System.out.println("6. Show All Books");
        System.out.println("7. Show All Members");
        System.out.println("8. Show Summary");
        System.out.println("9. Exit");
    }

    private static void addBook() {
        String id = readText("Enter Book ID: ");
        String title = readText("Enter title: ");
        String author = readText("Enter author: ");
        int copies = readInt("Enter number of copies: ");
        if (copies <= 0) {
            System.out.println("Copies must be greater than zero.");
            return;
        }
        if (library.addBook(id, title, author, copies)) System.out.println("Book saved in database.");
    }

    private static void addMember() {
        String id = readText("Enter Member ID: ");
        String name = readText("Enter member name: ");
        if (library.addMember(id, name)) System.out.println("Member saved in database.");
    }

    private static void issueBook() {
        String memberId = readText("Enter Member ID: ");
        String bookId = readText("Enter Book ID: ");
        library.issueBook(memberId, bookId);
    }

    private static void returnBook() {
        String memberId = readText("Enter Member ID: ");
        String bookId = readText("Enter Book ID: ");
        int days = readInt("Enter number of days kept: ");
        library.returnBook(memberId, bookId, days);
    }

    private static void searchBook() {
        System.out.println("1. Search by Book ID");
        System.out.println("2. Search by title keyword");
        int choice = readInt("Choose search type: ");
        if (choice == 1) library.searchById(readText("Enter Book ID: "));
        else if (choice == 2) library.searchByTitle(readText("Enter title keyword: "));
        else System.out.println("Invalid search type.");
    }

    private static String readText(String prompt) {
        while (true) {
            System.out.print(prompt);
            String value = input.nextLine().trim();
            if (!value.isEmpty()) return value;
            System.out.println("This field cannot be empty. Try again.");
        }
    }

    private static int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String value = input.nextLine().trim();
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a whole number, such as 1 or 5.");
            }
        }
    }
}
