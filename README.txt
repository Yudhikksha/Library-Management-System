LIBRARY MANAGEMENT WEBSITE (Java + SQLite)

This is a separate web version of the console project. It runs locally on your computer and opens in Chrome at http://localhost:8080.

REQUIREMENTS
- Java 17 or newer
- Maven installed (the same Maven folder you used for the console project is fine)
- Internet access on first Maven run so Maven can download the SQLite JDBC dependency

RUN
1. Open this folder in VS Code.
2. Open Terminal > New Terminal.
3. Run your Maven command, adjusting the path if your Downloads folder differs:
   & "C:\Users\suhip\Downloads\apache-maven-3.10.0-bin\apache-maven-3.10.0\bin\mvn.cmd" clean compile exec:java
4. Wait for: Library website is running at http://localhost:8080
5. Open Chrome and enter: http://localhost:8080
6. Keep the VS Code terminal open while using the website. Press Ctrl+C in the terminal to stop it.

DATABASE
The app creates library.db in the folder where Maven is run. To reuse your existing records, copy the old project's library.db into this project folder before starting the app. If this folder already has a library.db, back it up before replacing it. Do not run both apps against the same database file at the same time.

FEATURES
- Dashboard and counts
- Add book
- Register member
- Issue book (maximum 3 active loans per member; no duplicate active loan of the same book)
- Return book (Rs. 2/day after 7 days)
- Search books by ID, title, or author
- Book availability and member list
- SQLite persistence

This is a local demo web application, not an internet-hosted public website. It has no login/security system and should not be exposed to the public internet.
