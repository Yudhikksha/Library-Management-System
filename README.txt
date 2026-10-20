LIBRARY MANAGEMENT SYSTEM WITH DBMS (JAVA + SQLITE)

Problem statement
Managing library books manually can make it difficult to maintain book details and track borrowed books. A simple Library Management System can help librarians organize book records and manage book availability efficiently.

WHAT IS INCLUDED
- Java console application
- SQLite relational database, connected using JDBC
- Database tables: books, members, loans
- Persistent records: data remains after closing the program
- Add books and members, issue and return books, calculate fines, search, catalogue, summary
- Maximum of 3 active loans per member
- Rs. 2 fine per day after 7 days
- PreparedStatement-based SQL queries and transactions for issuing/returning books

IMPORTANT
This project uses SQLite instead of MySQL so you do not need to install or start a separate database server. SQLite is a real relational DBMS; the file library.db is created automatically in the project folder when the program starts.

REQUIREMENTS
1. Install JDK 17 or newer.
2. Install Apache Maven.
3. Install VS Code and the "Extension Pack for Java" extension.
4. Internet is needed on the first run so Maven can download the SQLite JDBC library.

RUN THE PROJECT
1. Extract the ZIP file.
2. Open VS Code.
3. Choose File > Open Folder.
4. Select the extracted Library_Management_System_DBMS folder (the folder containing pom.xml).
5. Choose Terminal > New Terminal.
6. Check Java: java -version
7. Check Maven: mvn -version
8. Run: mvn clean compile
9. Run: mvn exec:java
10. Choose a menu option and enter details.

EXAMPLE TEST
- Choose 1; Book ID B001; Title Clean Code; Author Robert Martin; Copies 2
- Choose 2; Member ID M001; Name Asha
- Choose 3; Member ID M001; Book ID B001
- Choose 6 to see available copies reduce from 2 to 1
- Choose 9 to exit, then run mvn exec:java again. The book and member records remain saved.

DATABASE FILE
library.db will appear in the project root after the first run. Do not delete it if you want to keep your records.

TROUBLESHOOTING
- If 'mvn' is not recognized, Maven is not installed or its bin folder is not in PATH. Install Apache Maven, restart VS Code, and check mvn -version.
- If Maven cannot download dependencies, check your internet connection and try mvn exec:java again.
- Keep the folder containing pom.xml open in VS Code.
