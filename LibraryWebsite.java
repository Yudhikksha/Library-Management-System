package library;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public class LibraryWebsite {
    private static final String DB_URL = "jdbc:sqlite:library.db";
    private static final int PORT = 8081;

    public static void main(String[] args) throws Exception {
        initializeDatabase();
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", LibraryWebsite::handle);
        server.setExecutor(null);
        server.start();
        System.out.println("Library website is running at http://localhost:" + PORT);
        System.out.println("Keep this terminal open while using the website. Press Ctrl+C to stop it.");
    }

    private static Connection connect() throws SQLException {
        Connection c = DriverManager.getConnection(DB_URL);
        try (Statement s = c.createStatement()) { s.execute("PRAGMA foreign_keys=ON"); }
        return c;
    }

    private static void initializeDatabase() throws SQLException {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS books (book_id TEXT PRIMARY KEY, title TEXT NOT NULL, author TEXT NOT NULL, total_copies INTEGER NOT NULL CHECK(total_copies>0), available_copies INTEGER NOT NULL CHECK(available_copies>=0 AND available_copies<=total_copies))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS members (member_id TEXT PRIMARY KEY, name TEXT NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS loans (loan_id INTEGER PRIMARY KEY AUTOINCREMENT, member_id TEXT NOT NULL, book_id TEXT NOT NULL, issue_date TEXT NOT NULL DEFAULT (date('now','localtime')), return_date TEXT, days_kept INTEGER, fine INTEGER DEFAULT 0, FOREIGN KEY(member_id) REFERENCES members(member_id), FOREIGN KEY(book_id) REFERENCES books(book_id))");
        }
    }

    private static void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            Map<String,String> p = new HashMap<>();
            if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                for (String pair : body.split("&")) {
                    String[] kv = pair.split("=", 2);
                    p.put(decode(kv[0]), kv.length > 1 ? decode(kv[1]) : "");
                }
                String message = process(path, p);
                redirect(ex, "/?message=" + enc(message));
                return;
            }
            String message = ex.getRequestURI().getRawQuery() == null ? "" : queryValue(ex.getRequestURI().getRawQuery(), "message");
            String search = ex.getRequestURI().getRawQuery() == null ? "" : queryValue(ex.getRequestURI().getRawQuery(), "search");
            send(ex, 200, page(message, search));
        } catch (Exception e) {
            send(ex, 500, layout("Something went wrong", "<section class='panel'><h2>Unable to complete that action</h2><p class='error'>" + esc(e.getMessage()) + "</p><a class='btn' href='/'>Back to dashboard</a></section>"));
        }
    }

    private static String process(String path, Map<String,String> p) throws SQLException {
        try (Connection c = connect()) {
            switch (path) {
                case "/add-book" -> {
                    String id=req(p,"book_id"), title=req(p,"title"), author=req(p,"author"); int copies=Integer.parseInt(req(p,"copies"));
                    if(copies<=0) return "Number of copies must be greater than zero.";
                    try(PreparedStatement s=c.prepareStatement("INSERT INTO books(book_id,title,author,total_copies,available_copies) VALUES(?,?,?,?,?)")) {s.setString(1,id);s.setString(2,title);s.setString(3,author);s.setInt(4,copies);s.setInt(5,copies);s.executeUpdate();}
                    return "Book added successfully.";
                }
                case "/add-member" -> {
                    try(PreparedStatement s=c.prepareStatement("INSERT INTO members(member_id,name) VALUES(?,?)")){s.setString(1,req(p,"member_id"));s.setString(2,req(p,"name"));s.executeUpdate();}
                    return "Member registered successfully.";
                }
                case "/issue" -> {
                    String mid=req(p,"member_id"), bid=req(p,"book_id"); c.setAutoCommit(false);
                    try {
                        if(count(c,"SELECT COUNT(*) FROM members WHERE member_id=?",mid)==0) throw new IllegalArgumentException("Member ID not found.");
                        int avail;
                        try(PreparedStatement s=c.prepareStatement("SELECT available_copies FROM books WHERE book_id=?")){s.setString(1,bid);try(ResultSet r=s.executeQuery()){if(!r.next()) throw new IllegalArgumentException("Book ID not found.");avail=r.getInt(1);}}
                        if(avail<1) throw new IllegalArgumentException("No copies are currently available.");
                        if(count(c,"SELECT COUNT(*) FROM loans WHERE member_id=? AND return_date IS NULL",mid)>=3) throw new IllegalArgumentException("A member may borrow at most 3 books at a time.");
                        try(PreparedStatement s=c.prepareStatement("SELECT COUNT(*) FROM loans WHERE member_id=? AND book_id=? AND return_date IS NULL")){s.setString(1,mid);s.setString(2,bid);try(ResultSet r=s.executeQuery()){r.next();if(r.getInt(1)>0)throw new IllegalArgumentException("This member already has this book checked out.");}}
                        try(PreparedStatement s=c.prepareStatement("UPDATE books SET available_copies=available_copies-1 WHERE book_id=? AND available_copies>0")){s.setString(1,bid);if(s.executeUpdate()!=1)throw new IllegalArgumentException("Could not issue book; try again.");}
                        try(PreparedStatement s=c.prepareStatement("INSERT INTO loans(member_id,book_id) VALUES(?,?)")){s.setString(1,mid);s.setString(2,bid);s.executeUpdate();}
                        c.commit();return "Book issued successfully.";
                    } catch(Exception e){c.rollback();if(e instanceof IllegalArgumentException) return e.getMessage();if(e instanceof SQLException se) throw se;return "Could not issue book.";} finally {c.setAutoCommit(true);}
                }
                case "/return" -> {
                    String mid=req(p,"member_id"), bid=req(p,"book_id");int days=Integer.parseInt(req(p,"days"));if(days<0)return "Days kept cannot be negative.";
                    c.setAutoCommit(false);
                    try {
                        long loanId;
                        try(PreparedStatement s=c.prepareStatement("SELECT loan_id FROM loans WHERE member_id=? AND book_id=? AND return_date IS NULL ORDER BY loan_id LIMIT 1")){s.setString(1,mid);s.setString(2,bid);try(ResultSet r=s.executeQuery()){if(!r.next())throw new IllegalArgumentException("No active borrowing record found for that member and book.");loanId=r.getLong(1);}}
                        int late=Math.max(0,days-7), fine=late*2;
                        try(PreparedStatement s=c.prepareStatement("UPDATE loans SET return_date=?,days_kept=?,fine=? WHERE loan_id=?")){s.setString(1,LocalDate.now().toString());s.setInt(2,days);s.setInt(3,fine);s.setLong(4,loanId);if(s.executeUpdate()!=1)throw new IllegalArgumentException("Could not update loan record.");}
                        try(PreparedStatement s=c.prepareStatement("UPDATE books SET available_copies=available_copies+1 WHERE book_id=? AND available_copies<total_copies")){s.setString(1,bid);if(s.executeUpdate()!=1)throw new IllegalArgumentException("Could not update available book copies.");}
                        c.commit();return "Book returned successfully. Fine: Rs. "+fine+ (fine==0?" (no late fine)":" ("+late+" late day(s))");
                    }catch(Exception e){c.rollback();if(e instanceof IllegalArgumentException)return e.getMessage();if(e instanceof SQLException se)throw se;return "Could not return book.";}finally{c.setAutoCommit(true);}
                }
                default -> { return "Unknown action."; }
            }
        } catch (NumberFormatException e) { return "Please enter a valid whole number."; }
        catch (SQLException e) { if (e.getMessage()!=null && e.getMessage().toLowerCase().contains("unique")) return "That ID already exists. Please use a different ID."; throw e; }
    }

    private static int count(Connection c,String sql,String value)throws SQLException{try(PreparedStatement s=c.prepareStatement(sql)){s.setString(1,value);try(ResultSet r=s.executeQuery()){r.next();return r.getInt(1);}}}
    private static String req(Map<String,String> p,String k){String v=p.getOrDefault(k,"").trim();if(v.isEmpty())throw new IllegalArgumentException("Please fill in all required fields.");return v;}

    private static String page(String message, String search) throws SQLException {
        StringBuilder b=new StringBuilder();
        if(!message.isBlank()) b.append("<div class='notice'>").append(esc(message)).append("</div>");
        b.append("<section class='stats'>");
        b.append(stat("Book titles",scalar("SELECT COUNT(*) FROM books"),"library"));
        b.append(stat("Physical copies",scalar("SELECT COALESCE(SUM(total_copies),0) FROM books"),"book-open"));
        b.append(stat("Available copies",scalar("SELECT COALESCE(SUM(available_copies),0) FROM books"),"check-circle"));
        b.append(stat("Members",scalar("SELECT COUNT(*) FROM members"),"users"));b.append("</section>");
        b.append("<div class='columns'><section class='panel'><div class='panel-head'><div><p class='eyebrow'>CATALOGUE</p><h2>Add a book</h2></div><span class='symbol'>＋</span></div><form method='post' action='/add-book'><label>Book ID</label><input name='book_id' placeholder='e.g. B001' required><label>Book title</label><input name='title' placeholder='Enter title' required><label>Author</label><input name='author' placeholder='Enter author name' required><label>Number of copies</label><input name='copies' type='number' min='1' value='1' required><button class='btn' type='submit'>Add book</button></form></section>");
        b.append("<section class='panel'><div class='panel-head'><div><p class='eyebrow'>MEMBERS</p><h2>Register a member</h2></div><span class='symbol'>♙</span></div><form method='post' action='/add-member'><label>Member ID</label><input name='member_id' placeholder='e.g. M001' required><label>Full name</label><input name='name' placeholder='Enter member name' required><button class='btn' type='submit'>Register member</button></form></section></div>");
        b.append("<div class='columns'><section class='panel'><div class='panel-head'><div><p class='eyebrow'>BORROWING</p><h2>Issue a book</h2></div></div><form method='post' action='/issue'><label>Member ID</label><input name='member_id' placeholder='e.g. M001' required><label>Book ID</label><input name='book_id' placeholder='e.g. B001' required><button class='btn' type='submit'>Issue book</button></form><p class='hint'>Each member can have up to 3 active loans.</p></section>");
        b.append("<section class='panel'><div class='panel-head'><div><p class='eyebrow'>RETURNS</p><h2>Return a book</h2></div></div><form method='post' action='/return'><label>Member ID</label><input name='member_id' placeholder='e.g. M001' required><label>Book ID</label><input name='book_id' placeholder='e.g. B001' required><label>Days kept</label><input name='days' type='number' min='0' value='7' required><button class='btn secondary' type='submit'>Return book</button></form><p class='hint'>Late fine: ₹2 per day after 7 days.</p></section></div>");
        b.append("<section class='panel catalogue'><div class='panel-head'><div><p class='eyebrow'>YOUR COLLECTION</p><h2>Book catalogue</h2></div><form class='search' method='get' action='/'><input name='search' placeholder='Search title, author or ID' value='").append(esc(search)).append("'><button class='btn small' type='submit'>Search</button>");if(!search.isBlank())b.append("<a class='clear' href='/'>Clear</a>");b.append("</form></div>");
        b.append("<div class='table-wrap'><table><thead><tr><th>Book ID</th><th>Title</th><th>Author</th><th>Total copies</th><th>Available</th><th>Status</th></tr></thead><tbody>");
        String sql="SELECT book_id,title,author,total_copies,available_copies FROM books"+(search.isBlank()?"":" WHERE book_id LIKE ? OR title LIKE ? OR author LIKE ?")+" ORDER BY title COLLATE NOCASE";
        try(Connection c=connect();PreparedStatement s=c.prepareStatement(sql)){if(!search.isBlank()){String q="%"+search+"%";s.setString(1,q);s.setString(2,q);s.setString(3,q);}try(ResultSet r=s.executeQuery()){boolean any=false;while(r.next()){any=true;int avail=r.getInt("available_copies");b.append("<tr><td><span class='id'>").append(esc(r.getString("book_id"))).append("</span></td><td class='book-title'>").append(esc(r.getString("title"))).append("</td><td>").append(esc(r.getString("author"))).append("</td><td>").append(r.getInt("total_copies")).append("</td><td>").append(avail).append("</td><td><span class='status ").append(avail>0?"available":"unavailable").append("'>").append(avail>0?"Available":"Checked out").append("</span></td></tr>");}if(!any)b.append("<tr><td colspan='6' class='empty'>No books found. Add a book using the form above.</td></tr>");}}
        b.append("</tbody></table></div></section>");
        b.append("<div class='columns lower'><section class='panel'><h2>Registered members</h2><div class='member-list'>");
        try(Connection c=connect();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT member_id,name FROM members ORDER BY name COLLATE NOCASE")){boolean any=false;while(r.next()){any=true;b.append("<div class='member-row'><span class='avatar'>").append(esc(r.getString("name").substring(0,1).toUpperCase(Locale.ROOT))).append("</span><div><strong>").append(esc(r.getString("name"))).append("</strong><small>").append(esc(r.getString("member_id"))).append("</small></div></div>");}if(!any)b.append("<p class='hint'>No members registered yet.</p>");}
        b.append("</div></section><section class='panel'><h2>Library activity</h2><div class='activity-row'><span>Total issue records</span><strong>").append(scalar("SELECT COUNT(*) FROM loans")).append("</strong></div><div class='activity-row'><span>Currently borrowed</span><strong>").append(scalar("SELECT COUNT(*) FROM loans WHERE return_date IS NULL")).append("</strong></div><div class='activity-row'><span>Fines recorded</span><strong>₹").append(scalar("SELECT COALESCE(SUM(fine),0) FROM loans")).append("</strong></div></section></div>");
        return layout("Library dashboard",b.toString());
    }
    private static String scalar(String sql)throws SQLException{try(Connection c=connect();Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){r.next();return r.getString(1);}}
    private static String stat(String title,String value,String icon){return "<article class='stat'><div class='stat-icon'>"+switch(icon){case "library"->"▤";case "book-open"->"▣";case "check-circle"->"✓";default->"♙";}+"</div><p>"+title+"</p><strong>"+value+"</strong></article>";}
    private static String layout(String title,String content){return "<!doctype html><html lang='en'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>"+esc(title)+" | PageTurn Library</title><style>"+css()+"</style></head><body><aside class='sidebar'><div class='brand-mark'>P</div><div class='brand'>PageTurn<span>LIBRARY MANAGEMENT</span></div><div class='nav-label'>WORKSPACE</div><a class='nav active' href='/'>▦ &nbsp; Dashboard</a><div class='side-note'><div class='side-note-icon'>✦</div><strong>Your library, organized.</strong><p>Manage books and borrowing records in one place.</p></div><div class='sidebar-foot'><span class='online'></span> Local database connected</div></aside><main class='main'><header><div><p class='eyebrow'>LIBRARY MANAGEMENT SYSTEM</p><h1>Good day, Librarian <span>✦</span></h1><p class='subtitle'>Here’s what’s happening in your library today.</p></div><div class='date-chip'>● &nbsp; System online</div></header>"+content+"<footer>PageTurn Library Management System <span>Java · SQLite · Local web app</span></footer></main></body></html>";}
    private static String css(){return "@import url('https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&family=Manrope:wght@400;500;600;700;800&display=swap');:root{--navy:#172d4d;--blue:#3569d4;--blue2:#eaf0ff;--ink:#1c2940;--muted:#78869a;--line:#e6eaf1;--bg:#f6f8fc;--green:#16845d}*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font-family:'DM Sans',Arial,sans-serif;font-size:14px}.sidebar{position:fixed;inset:0 auto 0 0;width:238px;background:#142844;color:#fff;padding:27px 18px;display:flex;flex-direction:column}.brand-mark{width:37px;height:37px;border-radius:12px;background:#4778e2;display:grid;place-items:center;font-family:Manrope;font-size:23px;font-weight:800;float:left;margin:0 10px 0 2px}.brand{font-family:Manrope;font-size:18px;font-weight:800;line-height:1.15;padding-top:1px}.brand span{display:block;font:9px 'DM Sans';letter-spacing:1.25px;color:#9aacc8;margin-top:5px}.nav-label{font-size:10px;letter-spacing:1.7px;color:#8295b2;margin:52px 0 13px 9px}.nav{display:block;text-decoration:none;color:#d7e1f1;padding:13px 12px;border-radius:10px}.nav.active{background:#2c4262;color:white}.side-note{margin-top:auto;background:#203a5b;border:1px solid #2e496b;border-radius:13px;padding:15px}.side-note-icon{color:#a6c0ff;font-size:19px;margin-bottom:9px}.side-note strong{font-size:12px}.side-note p{font-size:11px;color:#b5c3d8;line-height:1.6;margin-bottom:0}.sidebar-foot{font-size:10px;color:#a6b7ce;margin:20px 0 0 4px}.online{display:inline-block;width:7px;height:7px;border-radius:50%;background:#47d3a0;margin-right:6px}.main{margin-left:238px;padding:35px 38px;max-width:1700px}header{display:flex;align-items:center;justify-content:space-between;margin-bottom:26px}.eyebrow{font-size:10px;font-weight:700;letter-spacing:1.4px;color:#8a96a9;margin:0 0 8px}h1,h2{font-family:Manrope,Arial,sans-serif;margin:0;font-weight:750}h1{font-size:27px;letter-spacing:-.8px}h1 span{font-size:18px;color:#e8a93b}.subtitle{color:var(--muted);margin:8px 0 0}.date-chip{background:white;border:1px solid var(--line);border-radius:30px;padding:10px 14px;color:#23825f;font-size:11px;font-weight:600}.stats{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:16px;margin-bottom:19px}.stat,.panel{background:white;border:1px solid var(--line);border-radius:14px;box-shadow:0 3px 12px #172d4d05}.stat{padding:18px 20px;position:relative}.stat-icon{width:36px;height:36px;display:grid;place-items:center;border-radius:10px;background:var(--blue2);color:var(--blue);font-size:19px}.stat p{font-size:12px;color:var(--muted);margin:14px 0 5px}.stat strong{font:800 25px Manrope;color:var(--ink)}.columns{display:grid;grid-template-columns:1fr 1fr;gap:18px;margin-bottom:18px}.panel{padding:21px 22px;min-width:0}.panel-head{display:flex;justify-content:space-between;align-items:center;gap:15px;margin-bottom:17px}.panel h2{font-size:16px}.symbol{width:35px;height:35px;border-radius:10px;background:#edf2ff;color:var(--blue);display:grid;place-items:center;font-size:22px}label{display:block;font-size:11px;font-weight:700;color:#526178;margin:12px 0 6px}input{width:100%;min-width:0;padding:11px 12px;border:1px solid #dfe5ee;border-radius:8px;background:#fff;font:13px 'DM Sans';color:var(--ink);outline:none}input:focus{border-color:#6b91e9;box-shadow:0 0 0 3px #eaf0ff}.btn{border:0;border-radius:8px;background:var(--blue);color:white;padding:11px 15px;font:600 12px 'DM Sans';cursor:pointer;margin-top:14px;text-decoration:none;display:inline-block}.btn:hover{background:#2858bd}.btn.secondary{background:#1b805e}.btn.small{margin:0;white-space:nowrap;padding:11px 13px}.hint{font-size:11px;color:var(--muted);margin:11px 0 0;line-height:1.5}.catalogue{margin-bottom:18px}.search{display:flex;gap:8px;align-items:center;max-width:390px}.search input{width:220px}.clear{color:var(--blue);font-size:12px}.table-wrap{overflow-x:auto}table{width:100%;border-collapse:collapse;text-align:left;white-space:nowrap}th{background:#f8faff;color:#8390a4;font-size:10px;letter-spacing:.5px;text-transform:uppercase;font-weight:700;padding:12px 13px;border-bottom:1px solid var(--line)}td{padding:14px 13px;border-bottom:1px solid #eef1f6;font-size:12px;color:#58667a}tr:last-child td{border-bottom:0}.id{font-size:11px;font-weight:700;color:#456bc0;background:#eef3ff;border-radius:5px;padding:5px 7px}.book-title{font-weight:700;color:#24344e}.status{font-size:10px;font-weight:700;padding:5px 8px;border-radius:20px}.available{background:#e5f7ef;color:#16845d}.unavailable{background:#fff0e5;color:#b56a20}.empty{text-align:center;padding:24px;color:var(--muted)}.lower{margin-bottom:30px}.member-list{margin-top:10px}.member-row{display:flex;align-items:center;gap:11px;padding:10px 0;border-bottom:1px solid #eef1f6}.member-row:last-child{border:0}.avatar{width:34px;height:34px;display:grid;place-items:center;border-radius:50%;background:#eaf0ff;color:#426bd0;font-weight:700}.member-row strong,.member-row small{display:block}.member-row strong{font-size:12px}.member-row small{font-size:10px;color:var(--muted);margin-top:4px}.activity-row{display:flex;justify-content:space-between;padding:15px 0;border-bottom:1px solid #eef1f6;color:#6e7b8f;font-size:12px}.activity-row strong{color:var(--ink);font-size:14px}.activity-row:last-child{border:0}footer{border-top:1px solid var(--line);padding:20px 0;color:#8a96a9;font-size:10px;display:flex;justify-content:space-between}.notice{background:#e8f7ef;color:#176e50;border:1px solid #c8edda;border-radius:9px;padding:12px 15px;margin-bottom:18px;font-size:12px}.error{color:#a72c2c;line-height:1.6}@media(max-width:1050px){.main{padding:27px 22px}.sidebar{width:205px}.main{margin-left:205px}.stats{grid-template-columns:repeat(2,1fr)}}@media(max-width:720px){.sidebar{position:static;width:auto;height:auto;padding:16px;display:block}.nav-label,.nav,.side-note,.sidebar-foot{display:none}.brand-mark{display:inline-grid}.brand{padding-top:0}.main{margin:0;padding:22px 14px}header{align-items:flex-start;gap:12px;flex-direction:column}h1{font-size:23px}.stats{grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.stat{padding:13px}.columns{grid-template-columns:1fr}.panel{padding:17px}.panel-head{align-items:flex-start;flex-direction:column}.search{max-width:100%;width:100%}.search input{flex:1;width:100%}footer{gap:10px;flex-direction:column}}";}
    private static void redirect(HttpExchange ex,String path)throws IOException{ex.getResponseHeaders().set("Location",path);ex.sendResponseHeaders(303,-1);ex.close();}
    private static void send(HttpExchange ex,int code,String html)throws IOException{byte[] data=html.getBytes(StandardCharsets.UTF_8);ex.getResponseHeaders().set("Content-Type","text/html; charset=UTF-8");ex.getResponseHeaders().set("X-Content-Type-Options","nosniff");ex.sendResponseHeaders(code,data.length);try(var out=ex.getResponseBody()){out.write(data);}}
    private static String decode(String s){return URLDecoder.decode(s,StandardCharsets.UTF_8);}private static String enc(String s){return java.net.URLEncoder.encode(s,StandardCharsets.UTF_8);}private static String queryValue(String q,String key){for(String p:q.split("&")){String[] a=p.split("=",2);if(decode(a[0]).equals(key))return a.length>1?decode(a[1]):"";}return "";}
    private static String esc(String s){if(s==null)return "";return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
}
